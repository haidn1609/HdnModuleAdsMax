package com.hdn.adsmodule.ads.reward

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.applovin.mediation.ads.MaxRewardedAd
import com.hdn.adsmodule.R
import com.hdn.adsmodule.ads.AdUnitParser
import com.hdn.adsmodule.ads.AdsController
import com.hdn.adsmodule.ads.AdsIdConfig
import com.hdn.adsmodule.ads.AdsManager
import com.hdn.adsmodule.ads.MaxListener
import com.hdn.adsmodule.ads.inter.InterAds
import com.hdn.adsmodule.base.ui.LoadingDialog
import com.hdn.adsmodule.model.AdValue
import com.hdn.adsmodule.model.AdsLog

object RewardAds {
    private val rewardIdDefault: List<String>
        get() = AdUnitParser.parse(AdsIdConfig.Remote.REWARDED, AdsIdConfig.Release.REWARDED)

    private var currentAdUnitIds: List<String> = emptyList()

    // ponytail: MaxRewardedAd.getInstance là singleton theo ad unit -> không destroy, dùng lại instance cho lần load sau
    private var rewardedAd: MaxRewardedAd? = null
    private val listener = MaxListener()
    private var isLoading = false
    var isShowing = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var hasEarnedReward = false
    private fun showAdUnavailableToast(activity: Activity) {
        Toast.makeText(activity, activity.getString(R.string.ad_unavailable), Toast.LENGTH_SHORT)
            .show()
    }

    fun preload(context: Activity) {
        currentAdUnitIds = rewardIdDefault
        if (rewardedAd?.isReady == true || isLoading) return

        isLoading = true
        loadRewardedAd(
            activity = context,
            ids = currentAdUnitIds,
            index = 0,
            onLoaded = {},
            onFailed = {
                rewardedAd = null
                isLoading = false
            }
        )
    }

    fun showRewardWithFallbackInter(
        activity: Activity,
        useWithoutVip: Boolean = false,
        autoCache: Boolean = true,
        fakeLoadingTime: Long = 0L,
        callback: RewardCallback
    ) {
        show(activity, callback, useInterFallback = true, useWithoutVip = useWithoutVip, autoCache = autoCache, fakeLoadingTime = fakeLoadingTime)
    }

    fun show(activity: Activity, callback: RewardCallback, useWithoutVip: Boolean, autoCache: Boolean = true, fakeLoadingTime: Long = 0L) {
        show(activity, callback, useInterFallback = false, useWithoutVip = useWithoutVip, autoCache = autoCache, fakeLoadingTime = fakeLoadingTime)
    }

    private fun show(
        activity: Activity,
        callback: RewardCallback,
        useInterFallback: Boolean,
        useWithoutVip: Boolean = false,
        autoCache: Boolean = true,
        fakeLoadingTime: Long = 0L
    ) {
        if (!AdsController.adsEnable || (AdsController.isVip && !useWithoutVip)) {
            callback.onPremium()
            return
        }
        if (isShowing) {
            showAdUnavailableToast(activity)
            if (useInterFallback) {
                showInterFallback(activity, useWithoutVip, callback)
            } else {
                AdsManager.onAdsLog(AdsLog(AdsLog.Type.REWARD, "", AdsLog.Action.SHOW, AdsLog.Mess.ERR_SHOWING, null))
                callback.onAdFailed()
            }
            return
        }

        currentAdUnitIds = rewardIdDefault

        // Fake loading: có ad sẵn -> fake time rồi show. Chưa có ad -> load-and-show.
        if (fakeLoadingTime > 0) {
            val ad = rewardedAd?.takeIf { it.isReady }
            if (ad == null) {
                loadAndShow(activity, callback, useWithoutVip, useInterFallback, autoCache, fakeLoadingTime)
                return
            }
            LoadingDialog.show(activity)
            handler.postDelayed({
                LoadingDialog.dismiss()
                showInternal(activity, ad, callback, useInterFallback, useWithoutVip, autoCache)
            }, fakeLoadingTime)
            return
        }

        val ad = rewardedAd?.takeIf { it.isReady }
        if (ad != null) {
            showInternal(activity, ad, callback, useInterFallback, useWithoutVip, autoCache)
        } else {
            loadAndShow(activity, callback, useWithoutVip, useInterFallback, autoCache)
        }
    }

    private fun loadAndShow(
        activity: Activity,
        callback: RewardCallback,
        useWithoutVip: Boolean = false,
        useInterFallback: Boolean,
        autoCache: Boolean = true,
        fakeLoadingTime: Long = 0L
    ) {
        if (!AdsController.adsEnable || (AdsController.isVip && !useWithoutVip)) {
            callback.onPremium()
            return
        }
        if (isLoading) {
            Toast.makeText(activity, activity.getString(R.string.ad_is_loading), Toast.LENGTH_SHORT)
                .show()
            return
        }

        isLoading = true
        LoadingDialog.show(activity)
        val startTime = SystemClock.elapsedRealtime()

        loadRewardedAd(
            activity = activity,
            ids = currentAdUnitIds,
            index = 0,
            onLoaded = { ad ->
                // Đảm bảo loading hiển thị đủ fakeLoadingTime dù ad load nhanh hơn
                val remaining = fakeLoadingTime - (SystemClock.elapsedRealtime() - startTime)
                handler.postDelayed({
                    LoadingDialog.dismiss()
                    showInternal(activity, ad, callback, useInterFallback, useWithoutVip, autoCache)
                }, remaining.coerceAtLeast(0))
            },
            onFailed = {
                LoadingDialog.dismiss()
                rewardedAd = null
                isLoading = false
                showAdUnavailableToast(activity)
                if (useInterFallback) {
                    showInterFallback(activity, useWithoutVip, callback)
                } else {
                    callback.onAdFailed()
                }
            }
        )
    }

    private fun loadRewardedAd(
        activity: Activity,
        ids: List<String>,
        index: Int,
        onLoaded: (MaxRewardedAd) -> Unit,
        onFailed: () -> Unit
    ) {
        if (index >= ids.size) {
            rewardedAd = null
            isLoading = false
            onFailed()
            return
        }
        val adUnitId = ids[index]
        AdsController.invalidLoadReason(adUnitId)?.let { reason ->
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.REWARD, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.LOAD_FAILED, null))
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.REWARD, adUnitId, AdsLog.Action.LOAD, reason, null))
            loadRewardedAd(activity, ids, index + 1, onLoaded, onFailed)
            return
        }
        AdsManager.onAdsLog(AdsLog(AdsLog.Type.REWARD, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.START_LOAD, null))
        val ad = MaxRewardedAd.getInstance(adUnitId, activity.applicationContext)
        listener.clearShowCallbacks()
        listener.onLoaded = {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.REWARD, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.LOAD_SUCCESS, null))
            rewardedAd = ad
            isLoading = false
            onLoaded(ad)
        }
        listener.onLoadFailed = { error ->
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.REWARD, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.LOAD_FAILED, error))
            loadRewardedAd(activity, ids, index + 1, onLoaded, onFailed)
        }
        ad.setListener(listener)
        ad.setRevenueListener { AdsManager.onAdsPair(AdValue(it)) }
        listener.startLoad()
        ad.loadAd()
    }

    private fun showInternal(
        activity: Activity,
        ad: MaxRewardedAd,
        callback: RewardCallback,
        useInterFallback: Boolean,
        useWithoutVip: Boolean = false,
        autoCache: Boolean = true
    ) {
        hasEarnedReward = false
        if (!AdsController.adsEnable || (AdsController.isVip && !useWithoutVip)) {
            showAdUnavailableToast(activity)
            callback.onAdFailed()
            return
        }

        listener.onDisplayed = {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.REWARD, "", AdsLog.Action.SHOW, AdsLog.Mess.START_SHOW, null))
            isShowing = true
            callback.onAdShowed()
        }
        listener.onClicked = {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.REWARD, "", AdsLog.Action.ADS_CLICK, AdsLog.Action.SHOW, null))
        }
        listener.onRewarded = {
            hasEarnedReward = true
        }
        listener.onDisplayFailed = { error ->
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.REWARD, "", AdsLog.Action.SHOW, AdsLog.Mess.SHOW_FAILED, error))
            listener.clearShowCallbacks()
            rewardedAd = null
            isShowing = false
            showAdUnavailableToast(activity)
            if (useInterFallback) {
                showInterFallback(activity, useWithoutVip, callback)
            } else {
                callback.onAdFailed()
            }
        }
        listener.onHidden = {
            AdsManager.onAdsLog(AdsLog(AdsLog.Type.REWARD, "", AdsLog.Action.SHOW, AdsLog.Mess.SHOW_DISMISS, null))
            listener.clearShowCallbacks()
            InterAds.startDelay()
            rewardedAd = null
            isShowing = false

            if (hasEarnedReward) {
                callback.onRewardEarned()
            }

            callback.onAdClosed()
            if (autoCache) preload(activity)
        }
        AdsManager.onAdsLog(AdsLog(AdsLog.Type.REWARD, "", AdsLog.Action.SHOW, AdsLog.Mess.CALL_SHOW, null))
        ad.showAd(activity)
    }

    private fun showInterFallback(
        activity: Activity,
        useWithoutVip: Boolean,
        callback: RewardCallback
    ) {
        callback.onAdShowed()
        AdsManager.onAdsLog(AdsLog(AdsLog.Type.REWARD, "", AdsLog.Action.SHOW_ITA_FALLBACK, AdsLog.Mess.CALL_SHOW, null))
        InterAds.forceShowAdsBreak(activity, useWithoutVip) {
            if (it) {
                callback.onRewardEarned()
            } else {
                callback.onAdFailed()
            }
            callback.onAdClosed()
        }
    }

    interface RewardCallback {
        fun onAdShowed()
        fun onAdClosed()
        fun onRewardEarned()
        fun onAdFailed()
        fun onPremium()
    }
}
