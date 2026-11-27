package com.hdn.adsmodule.ads.open

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.applovin.mediation.ads.MaxAppOpenAd
import com.hdn.adsmodule.ads.AdUnitParser
import com.hdn.adsmodule.ads.AdsController
import com.hdn.adsmodule.ads.AdsIdConfig
import com.hdn.adsmodule.ads.AdsManager
import com.hdn.adsmodule.ads.MaxListener
import com.hdn.adsmodule.ads.inter.Callback
import com.hdn.adsmodule.model.AdValue
import com.hdn.adsmodule.model.AdsLog
import java.util.Date

object OpenAds {
    private val openIdDefault: List<String>
        get() = AdUnitParser.parse(AdsIdConfig.Remote.APP_OPEN, AdsIdConfig.Release.APP_OPEN)

    private var appOpenAd: MaxAppOpenAd? = null
    private val listener = MaxListener()
    private var isLoading = false
    private var isOpenShowingAd = false
    val disableClasses: ArrayList<Class<*>> = arrayListOf()
    private var loadTimeOpenAd: Long = 0
    var flagQC: Int = 1
    private var currentAdUnitIds: List<String> = emptyList()

    private val handler = Handler(Looper.getMainLooper())
    private val runnable = Runnable { flagQC = 1 }

    @JvmStatic
    @JvmOverloads
    fun initOpenAds(
        context: Context,
        adUnitIds: List<String> = openIdDefault,
        callback: Callback?
    ) {
        currentAdUnitIds = adUnitIds.ifEmpty { openIdDefault }

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
        loadOpenAdByIndex(context.applicationContext, currentAdUnitIds, 0, callback)
    }

    private fun loadOpenAdByIndex(
        context: Context,
        ids: List<String>,
        index: Int,
        callback: Callback?
    ) {
        if (index >= ids.size) {
            releaseAd()
            isLoading = false
            callback?.invoke()
            return
        }
        val adUnitId = ids[index]
        AdsController.invalidLoadReason(adUnitId)?.let { reason ->
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.OPEN, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.LOAD_FAILED, null))
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.OPEN, adUnitId, AdsLog.Action.LOAD, reason, null))
            loadOpenAdByIndex(context, ids, index + 1, callback)
            return
        }
        AdsManager.onAdsLog(AdsLog(AdsLog.Type.OPEN, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.START_LOAD, null))
        val ad = MaxAppOpenAd(adUnitId, context)
        appOpenAd = ad
        listener.clearShowCallbacks()
        listener.onLoaded = {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.OPEN, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.LOAD_SUCCESS, null))
            isLoading = false
            loadTimeOpenAd = Date().time
            callback?.invoke()
        }
        listener.onLoadFailed = { error ->
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.OPEN, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.START_FAILED, error))
            ad.destroy()
            if (appOpenAd === ad) appOpenAd = null
            loadOpenAdByIndex(context, ids, index + 1, callback)
        }
        ad.setListener(listener)
        ad.setRevenueListener { AdsManager.onAdsPair(AdValue(it)) }
        listener.startLoad()
        ad.loadAd()
    }

    private fun isOpenAdsCanUse(): Boolean {
        val dateDifference = Date().time - loadTimeOpenAd
        return dateDifference < 3600000 * 4
    }

    fun isCanShowOpenAds(): Boolean {
        return appOpenAd?.isReady == true && !isOpenShowingAd && isOpenAdsCanUse()
    }

    private val isCanLoadAds: Boolean
        get() {
            if (!AdsController.canShowAds()) return false
            if (isOpenShowingAd || isLoading) return false
            if (currentAdUnitIds.isEmpty()) return false
            return appOpenAd?.isReady != true || !isOpenAdsCanUse()
        }

    fun showOpenAds(context: Activity, callback: Callback?) {
        if (!AdsController.canShowAds()) {
            callback?.invoke()
            return
        }
        if (appOpenAd?.isReady != true) {
            // chưa có ad -> tranh thủ load, trả callback để caller đi tiếp (thường là điều hướng sau splash)
            initOpenAds(context) { callback?.invoke() }
            return
        }
        AdsManager.onAdsLog(AdsLog(AdsLog.Type.OPEN, "", AdsLog.Action.SHOW, AdsLog.Mess.CALL_SHOW, null))
        if (flagQC == 1) {
            val ad = appOpenAd
            if (isCanShowOpenAds() && ad != null) {
                listener.onDisplayFailed = { error ->
                    AdsManager.onAdsLog(AdsLog(AdsLog.Type.OPEN, "", AdsLog.Action.SHOW, AdsLog.Mess.SHOW_FAILED, error))
                    listener.clearShowCallbacks()
                    releaseAd()
                    callback?.invoke()
                }
                listener.onDisplayed = {
                    AdsManager.onAdsLog(AdsLog(AdsLog.Type.OPEN, "", AdsLog.Action.SHOW, AdsLog.Mess.SHOW_SUCCESS, null))
                    isOpenShowingAd = true
                }
                listener.onClicked = {
                    AdsManager.onAdsLog(AdsLog(AdsLog.Type.OPEN, "", AdsLog.Action.ADS_CLICK, AdsLog.Action.SHOW, null))
                }
                listener.onHidden = {
                    AdsManager.onAdsLog(AdsLog(AdsLog.Type.OPEN, "", AdsLog.Action.SHOW, AdsLog.Mess.SHOW_DISMISS, null))
                    listener.clearShowCallbacks()
                    isOpenShowingAd = false
                    releaseAd()
                    initOpenAds(context) {}
                    startDelay()
                    callback?.invoke()
                    Overlay.finish()
                }
                AdsManager.onAdsLog(AdsLog(AdsLog.Type.OPEN, "", AdsLog.Action.SHOW, AdsLog.Mess.START_SHOW, null))
                ad.showAd()
            } else {
                AdsManager.onAdsLog(AdsLog(AdsLog.Type.OPEN, "", AdsLog.Action.SHOW, AdsLog.Mess.ERR_CANT_SHOW, null))
                callback?.invoke()
            }
        } else {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.OPEN, "", AdsLog.Action.SHOW, AdsLog.Mess.ERR_DELAY, null))
            callback?.invoke()
        }
    }

    fun startDelay() {
        flagQC = 0
        handler.removeCallbacks(runnable)
        handler.postDelayed(runnable, if (AdsController.isDebug) 0 else 60000)
    }

    @JvmStatic
    fun disableAdsOpenForActivity(activityClass: Class<*>) {
        if (!disableClasses.contains(activityClass)) {
            disableClasses.add(activityClass)
        }
    }

    fun enableAdsOpenForActivity(activityClass: Class<*>) {
        disableClasses.remove(activityClass)
    }

    private fun releaseAd() {
        appOpenAd?.destroy()
        appOpenAd = null
    }

    private fun clear() {
        releaseAd()
        isLoading = false
        isOpenShowingAd = false
        loadTimeOpenAd = 0
    }
}
