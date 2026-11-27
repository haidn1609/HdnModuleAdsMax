package com.hdn.adsmodule.ads.inter

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.applovin.mediation.ads.MaxInterstitialAd
import com.hdn.adsmodule.R
import com.hdn.adsmodule.ads.AdUnitParser
import com.hdn.adsmodule.ads.AdsController
import com.hdn.adsmodule.ads.AdsIdConfig
import com.hdn.adsmodule.ads.AdsManager
import com.hdn.adsmodule.ads.MaxListener
import com.hdn.adsmodule.ads.fullDialog.FullScreenDialog
import com.hdn.adsmodule.base.ui.LoadingDialog
import com.hdn.adsmodule.model.AdValue
import com.hdn.adsmodule.model.AdsLog

object InterSplashAds {
    private val interIdDefault: List<String>
        get() = AdUnitParser.parse(AdsIdConfig.Remote.INTERSTITIAL_SPLASH, AdsIdConfig.Release.INTERSTITIAL_SPLASH)

    var mInterstitialAd: MaxInterstitialAd? = null
        private set

    private val listener = MaxListener()
    private var isLoading = false
    private var loadTimeAd: Long = 0
    private var currentAdUnitIds: List<String> = emptyList()

    @JvmStatic
    @JvmOverloads
    fun initInterAds(
        ac: Context,
        adUnitIds: List<String> = interIdDefault,
        callback: Callback?
    ) {
        currentAdUnitIds = adUnitIds.ifEmpty { interIdDefault }

        if (!AdsController.canShowAds()) {
            clear()
            callback?.invoke()
            return
        }

        if (!isCanLoadAds) {
            callback?.invoke()
            return
        }

        releaseAd()
        isLoading = true
        loadInterstitialByIndex(ac.applicationContext, currentAdUnitIds, 0, callback)
    }

    private fun loadInterstitialByIndex(
        context: Context,
        ids: List<String>,
        index: Int,
        callback: Callback?
    ) {
        if (index >= ids.size) {
            clear()
            callback?.invoke()
            return
        }
        val adUnitId = ids[index]
        AdsController.invalidLoadReason(adUnitId)?.let { reason ->
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.LOAD_FAILED, null))
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, adUnitId, AdsLog.Action.LOAD, reason, null))
            loadInterstitialByIndex(context, ids, index + 1, callback)
            return
        }
        AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.START_LOAD, null))
        val ad = MaxInterstitialAd(adUnitId, context)
        mInterstitialAd = ad
        listener.clearShowCallbacks()
        listener.onLoaded = {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.LOAD_SUCCESS, null))
            isLoading = false
            loadTimeAd = System.currentTimeMillis()
            callback?.invoke()
        }
        listener.onLoadFailed = { error ->
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.LOAD_FAILED, error))
            ad.destroy()
            if (mInterstitialAd === ad) mInterstitialAd = null
            loadInterstitialByIndex(context, ids, index + 1, callback)
        }
        ad.setListener(listener)
        ad.setRevenueListener { AdsManager.onAdsPair(AdValue(it)) }
        listener.startLoad()
        ad.loadAd()
    }

    val isCanShowAds: Boolean
        get() = mInterstitialAd?.isReady == true && !isAdsOverdue

    private val isCanLoadAds: Boolean
        get() = !isLoading && currentAdUnitIds.isNotEmpty() && (mInterstitialAd?.isReady != true || isAdsOverdue)

    private val isAdsOverdue: Boolean
        get() = System.currentTimeMillis() - loadTimeAd > 4 * 60 * 60 * 1000L

    @JvmStatic
    fun Activity.canShowInter(): Boolean {
        if (isFinishing || isDestroyed) return false
        return (this as? LifecycleOwner)
            ?.lifecycle
            ?.currentState
            ?.isAtLeast(Lifecycle.State.RESUMED)
            ?: true
    }

    @JvmStatic
    @JvmOverloads
    fun showAdsBreak(
        activity: AppCompatActivity,
        showDialog: Boolean,
        dialogRes: Int,
        nativeKey: String,
        startCallback: Callback?,
        doneCallBack: Callback?,
        fakeLoadingTime: Long = 0L
    ) {
        if (!AdsController.canShowAds()) {
            doneCallBack?.invoke()
            return
        }

        AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, "", AdsLog.Action.SHOW, AdsLog.Mess.CALL_SHOW, null))

        val enableDialog = showDialog && nativeKey.isNotEmpty()
        val layoutRes = if (enableDialog) {
            if (dialogRes != 0) dialogRes else R.layout.template_native_full
        } else {
            0
        }
        val key = if (enableDialog) nativeKey else ""

        // Fake loading: có ad sẵn -> chờ đủ fake time rồi show. Chưa có ad -> load, show khi xong nhưng tối thiểu fake time.
        if (fakeLoadingTime > 0) {
            val handler = Handler(Looper.getMainLooper())
            // Chờ nốt tới khi đủ fake time (tính từ startTime) rồi show/kết thúc
            fun showAfterFakeTime(startTime: Long) {
                val remaining = fakeLoadingTime - (SystemClock.elapsedRealtime() - startTime)
                handler.postDelayed({
                    LoadingDialog.dismiss()
                    if (isCanShowAds) {
                        runCatching {
                            showAdsFull(activity, enableDialog, layoutRes, key, startCallback, doneCallBack)
                        }.onFailure {
                            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, "", AdsLog.Action.SHOW, AdsLog.Mess.ERR_CALL_SHOW, null))
                            doneCallBack?.invoke()
                        }
                    } else {
                        AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, "", AdsLog.Action.SHOW, AdsLog.Mess.CANT_SHOW_VIP, null))
                        doneCallBack?.invoke()
                    }
                }, remaining.coerceAtLeast(0))
            }

            LoadingDialog.show(activity)
            val startTime = SystemClock.elapsedRealtime()
            if (isCanShowAds) {
                showAfterFakeTime(startTime)
            } else {
                // callback của initInterAds bắn cả khi load xong lẫn fail -> chờ nốt fake time rồi kiểm tra
                initInterAds(activity, callback = { showAfterFakeTime(startTime) })
            }
            return
        }

        if (!isCanShowAds) {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, "", AdsLog.Action.SHOW, AdsLog.Mess.CANT_SHOW_VIP, null))
            doneCallBack?.invoke()
            return
        }

        try {
            showAdsFull(activity, enableDialog, layoutRes, key, startCallback, doneCallBack)
        } catch (_: Exception) {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, "", AdsLog.Action.SHOW, AdsLog.Mess.ERR_CALL_SHOW, null))
            doneCallBack?.invoke()
        }
    }

    private fun showAdsFull(
        context: AppCompatActivity,
        showDialog: Boolean,
        dialogRes: Int,
        nativeKey: String,
        startCallback: Callback?,
        doneCallBack: Callback?
    ) {
        val currentAd = mInterstitialAd?.takeIf { it.isReady } ?: run {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, "", AdsLog.Action.SHOW_ADS_FULL, AdsLog.Mess.SHOW_FAILED_AD_NULL, null))
            doneCallBack?.invoke()
            return
        }
        val dialogNativeFull = FullScreenDialog.newInstance(dialogRes, nativeKey)
        listener.onDisplayFailed = { error ->
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, "", AdsLog.Action.SHOW_ADS_FULL, AdsLog.Mess.SHOW_FAILED, error))
            listener.clearShowCallbacks()
            releaseAd()
            doneCallBack?.invoke()
        }
        listener.onDisplayed = {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, "", AdsLog.Action.SHOW_ADS_FULL, AdsLog.Mess.SHOW_SUCCESS, null))
            startCallback?.invoke()
        }
        listener.onClicked = {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, "", AdsLog.Action.ADS_CLICK, AdsLog.Action.SHOW_ADS_FULL, null))
        }
        listener.onHidden = {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, "", AdsLog.Action.SHOW_ADS_FULL, AdsLog.Mess.SHOW_DISMISS, null))
            listener.clearShowCallbacks()
            releaseAd()
            if (dialogNativeFull.dialog != null && showDialog) {
                dialogNativeFull.requireDialog().setOnDismissListener {
                    AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, "", AdsLog.Action.SHOW_DIALOG_ADS, "dialog_dismiss", null))
                    dialogNativeFull.dismiss()
                    doneCallBack?.invoke()
                }
            } else {
                doneCallBack?.invoke()
            }
        }
        if (showDialog) {
            // Như bản gốc: sau 3s mở dialog, dialog mở rồi mới load native (FullScreenDialog.loadAndShow)
            Handler(Looper.getMainLooper()).postDelayed({
                if (context.isFinishing || context.isDestroyed) return@postDelayed
                AdsManager.onAdsLog(AdsLog(AdsLog.Type.INTER_SPLASH, "", AdsLog.Action.SHOW_DIALOG_ADS, AdsLog.Mess.CALL_SHOW, null))
                dialogNativeFull.showAllowingStateLoss(
                    context.supportFragmentManager,
                    "FullScreenDialog"
                )
            }, 3000)
        }
        currentAd.showAd(context)
    }

    private fun releaseAd() {
        mInterstitialAd?.destroy()
        mInterstitialAd = null
    }

    private fun clear() {
        releaseAd()
        isLoading = false
        loadTimeAd = 0
    }
}
