package com.hdn.adsmodule.ads.banner

import android.annotation.SuppressLint
import android.app.Activity
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isNotEmpty
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxAdViewConfiguration
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.sdk.AppLovinSdkUtils
import com.hdn.adsmodule.R
import com.hdn.adsmodule.ads.AdUnitParser
import com.hdn.adsmodule.ads.AdsController
import com.hdn.adsmodule.ads.AdsIdConfig
import com.hdn.adsmodule.ads.AdsManager
import com.hdn.adsmodule.model.AdValue
import com.hdn.adsmodule.model.AdsLog
import com.hdn.adsmodule.model.BannerMargin
import com.hdn.adsmodule.model._enum.BannerType

/**
 * Banner MAX (adaptive anchored). MAX không có banner collapsible như AdMob:
 * ponytail: COLLAPSIBLE chỉ dùng ID BANNER_COLLAPSIBLE, hiển thị như banner thường, không cache.
 */
object BannerAds {
    private fun getBannerIdDefault(bannerType: BannerType): List<String> =
        when (bannerType) {
            BannerType.COLLAPSIBLE -> AdUnitParser.parse(
                AdsIdConfig.Remote.BANNER_COLLAPSIBLE,
                AdsIdConfig.Release.BANNER_COLLAPSIBLE
            )

            BannerType.NORMAL -> AdUnitParser.parse(
                AdsIdConfig.Remote.BANNER,
                AdsIdConfig.Release.BANNER
            )
        }

    /** Banner NORMAL đã load, dùng lại giữa các màn. */
    private class CachedBanner(val view: MaxAdView, var loaded: Boolean)

    // Cache theo bề rộng banner (dp): màn full width và màn có margin mỗi bên giữ MaxAdView riêng,
    // tránh dùng lại banner rộng hơn container (bị cắt 2 bên). Cùng bề rộng thì vẫn dùng chung.
    private val normalBanners = mutableMapOf<Int, CachedBanner>()

    /** Bề rộng banner (dp) = bề rộng màn hình trừ margin start + end. */
    @JvmOverloads
    fun getAdWidthDp(activity: Activity, margin: BannerMargin = BannerMargin.NONE): Int {
        val outMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getMetrics(outMetrics)
        return (outMetrics.widthPixels / outMetrics.density).toInt() - margin.start - margin.end
    }

    /** Chiều cao adaptive (px) MAX trả cho bề rộng [widthDp]. */
    private fun getAdHeightPx(activity: Activity, widthDp: Int): Int {
        val heightDp = MaxAdFormat.BANNER.getAdaptiveSize(widthDp, activity).height
        return AppLovinSdkUtils.dpToPx(activity, heightDp)
    }

    @SuppressLint("InflateParams")
    @JvmStatic
    @JvmOverloads
    fun createBannerView(
        activity: Activity,
        type: BannerType,
        adUnitIds: List<String> = getBannerIdDefault(type),
        margin: BannerMargin = BannerMargin.NONE,
        onFinished: ((View) -> Unit)? = null,
    ): View {
        if (!AdsController.canShowAds()) {
            normalBanners.values.forEach { it.view.destroy() }
            normalBanners.clear()

            val emptyView = View(activity)
            emptyView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
            )
            onFinished?.invoke(emptyView)
            return emptyView
        }
        AdsManager.onAdsLog(AdsLog(logType(type), "", AdsLog.Mess.CALL_SHOW, "", null))
        val viewRoot =
            LayoutInflater.from(activity).inflate(R.layout.banner_ads, null, false)//loading layout
        if (!margin.isEmpty) {
            val density = activity.resources.displayMetrics.density
            viewRoot.layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (margin.start * density).toInt()
                topMargin = (margin.top * density).toInt()
                marginEnd = (margin.end * density).toInt()
                bottomMargin = (margin.bottom * density).toInt()
            }
        }
        val adViewContainer: LinearLayout = viewRoot.findViewById(R.id.adView_container)
        val loadingText: View = viewRoot.findViewById(R.id.tv_loading)
        val loadingOverlay: View = viewRoot.findViewById(R.id.view_d)

        val widthDp = getAdWidthDp(activity, margin)
        val heightPx = getAdHeightPx(activity, widthDp)
        val idsToUse = adUnitIds.ifEmpty { getBannerIdDefault(type) }
        loadingText.minimumHeight = heightPx

        val cachedBanner = if (type == BannerType.NORMAL) normalBanners[widthDp] else null
        if (cachedBanner != null) {
            val cachedView = cachedBanner.view
            (cachedView.parent as? ViewGroup)?.removeView(cachedView)
            adViewContainer.removeAllViews()
            adViewContainer.addView(cachedView)
            if (cachedBanner.loaded || adViewContainer.isNotEmpty()) {
                hideLoading(loadingText, loadingOverlay)
                AdsManager.onAdsLog(AdsLog(AdsLog.Type.BANNER, "", AdsLog.Action.SHOW, AdsLog.Mess.SHOW_SUCCESS, null))
            } else {
                showLoading(loadingText, loadingOverlay)
                AdsManager.onAdsLog(AdsLog(AdsLog.Type.BANNER, "", AdsLog.Action.SHOW, AdsLog.Mess.SHOW_FAILED, null))
            }
        } else {
            AdsManager.onAdsLog(AdsLog(logType(type), "", AdsLog.Action.CALL_LOAD, "", null))
            showLoading(loadingText, loadingOverlay)
            loadBannerByIndex(activity, idsToUse, 0, type, widthDp, heightPx, loadingText, loadingOverlay, adViewContainer)
        }

        onFinished?.invoke(viewRoot)
        return viewRoot
    }

    private fun loadBannerByIndex(
        activity: Activity,
        ids: List<String>,
        index: Int,
        bannerType: BannerType,
        widthDp: Int,
        heightPx: Int,
        loadingText: View,
        loadingOverlay: View,
        adViewContainer: LinearLayout
    ) {
        if (index >= ids.size) {
            if (bannerType == BannerType.NORMAL) {
                normalBanners.remove(widthDp)
            }
            hideLoading(loadingText, loadingOverlay)
            return
        }

        val adUnitId = ids[index]
        val type = logType(bannerType)
        val config = MaxAdViewConfiguration.builder()
            .setAdaptiveType(MaxAdViewConfiguration.AdaptiveType.ANCHORED)
            .setAdaptiveWidth(widthDp)
            .build()
        // Constructor có config không nhận Context: SDK lấy context từ AppLovinSdk đã initialize
        val bannerView = MaxAdView(adUnitId, MaxAdFormat.BANNER, config)
        bannerView.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)
        AdsManager.onAdsLog(AdsLog(type, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.START_LOAD, null))
        bannerView.setRevenueListener { AdsManager.onAdsPair(AdValue(it)) }
        bannerView.setListener(object : MaxAdViewAdListener {
            /** True once this banner has shown an ad; later failures are refreshes. */
            private var hasLoaded = false

            override fun onAdLoaded(ad: MaxAd) {
                hasLoaded = true
                if (bannerType == BannerType.NORMAL) {
                    normalBanners[widthDp] = CachedBanner(bannerView, loaded = true)
                }
                AdsManager.onAdsLog(AdsLog(type, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.LOAD_SUCCESS, null))
                AdsManager.onAdsLog(AdsLog(type, "", AdsLog.Action.SHOW, AdsLog.Mess.SHOW_SUCCESS, null))
                hideLoading(loadingText, loadingOverlay)
            }

            override fun onAdClicked(ad: MaxAd) {
                AdsManager.onAdsLog(AdsLog(type, adUnitId, AdsLog.Action.ADS_CLICK, AdsLog.Action.SHOW, null))
            }

            override fun onAdLoadFailed(id: String, error: MaxError) {
                if (hasLoaded) {
                    // Refresh failed: MAX keeps showing the last ad and retries on the
                    // next cycle, so keep this banner (and its cache entry) as it is.
                    AdsManager.onAdsLog(AdsLog(type, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.REFRESH_FAILED, error))
                    return
                }
                if (bannerType == BannerType.NORMAL) {
                    normalBanners.remove(widthDp)
                }
                AdsManager.onAdsLog(AdsLog(type, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.LOAD_FAILED, error))
                adViewContainer.removeView(bannerView)
                bannerView.destroy()
                loadBannerByIndex(activity, ids, index + 1, bannerType, widthDp, heightPx, loadingText, loadingOverlay, adViewContainer)
            }

            override fun onAdDisplayed(ad: MaxAd) = Unit
            override fun onAdHidden(ad: MaxAd) = Unit
            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) = Unit
            override fun onAdExpanded(ad: MaxAd) = Unit
            override fun onAdCollapsed(ad: MaxAd) = Unit
        })

        if (bannerType == BannerType.NORMAL && index == 0) {
            normalBanners[widthDp] = CachedBanner(bannerView, loaded = false)
        }
        adViewContainer.addView(bannerView)

        bannerView.loadAd()
    }

    private fun logType(type: BannerType) =
        if (type == BannerType.NORMAL) AdsLog.Type.BANNER else AdsLog.Type.BANNER_COLLAPSIBLE

    private fun showLoading(loadingText: View, loadingOverlay: View) {
        loadingText.visibility = View.VISIBLE
        loadingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoading(loadingText: View, loadingOverlay: View) {
        loadingText.visibility = View.GONE
        loadingOverlay.visibility = View.GONE
    }
}
