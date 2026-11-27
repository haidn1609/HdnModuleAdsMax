package com.hdn.adsmodule.ads.nativeAds

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import com.hdn.adsmodule.R
import com.hdn.adsmodule.ads.AdsController
import com.hdn.adsmodule.ads.AdsManager
import com.hdn.adsmodule.model.AdValue
import com.hdn.adsmodule.model.AdsLog
import java.lang.ref.WeakReference
import java.util.ArrayDeque

/**
 * Native MAX (manual template): layout của app được bind theo id giống bản AdMob:
 * primary (title), body, secondary (advertiser), icon (ImageView), cta (Button),
 * media_view (ViewGroup), rating_bar (ViewGroup), ad_options_view (ViewGroup), btnClose.
 * Id nào layout không có thì bỏ qua.
 */
class NativeAdManager(
    private val key: String, private val adUnitIds: List<String>
) {

    /** Ad đã load cùng loader của nó (render/destroy phải qua đúng loader). */
    private class LoadedAd(val loader: MaxNativeAdLoader, val ad: MaxAd)

    private val cachedAds = ArrayDeque<LoadedAd>()
    private val activeAds = linkedSetOf<LoadedAd>()
    private val loaders = hashMapOf<String, MaxNativeAdLoader>()
    private var desiredCacheSize = 1
    var isLoading: Boolean = false
        private set

    private enum class LoadMode {
        CACHE, NO_CACHE
    }

    private var pendingShow: PendingShow? = null
    private var currentIndex = 0

    private data class PendingShow(
        val activityRef: WeakReference<Activity>,
        val containerRef: WeakReference<FrameLayout>,
        val layoutResId: Int
    )

    fun preload(
        activity: Activity, cacheSize: Int = 1
    ) {

        if (!AdsController.canShowAds()) {
            destroy()
            return
        }

        desiredCacheSize = cacheSize.coerceAtLeast(0)

        if (desiredCacheSize == 0) {
            return
        }

        if (adUnitIds.isEmpty() || isLoading || cachedAds.size >= desiredCacheSize) {
            return
        }

        isLoading = true
        currentIndex = 0

        loadNextAd(activity, LoadMode.CACHE)
    }

    private fun loaderFor(activity: Activity, adUnitId: String): MaxNativeAdLoader =
        loaders.getOrPut(adUnitId) {
            MaxNativeAdLoader(adUnitId, activity.applicationContext).apply {
                setRevenueListener { AdsManager.onAdsPair(AdValue(it)) }
            }
        }

    private fun loadNextAd(
        activity: Activity, mode: LoadMode
    ) {

        if (currentIndex >= adUnitIds.size) {
            isLoading = false
            tryShowPending()
            return
        }

        val adUnitId = adUnitIds[currentIndex]
        AdsController.invalidLoadReason(adUnitId)?.let { reason ->
            AdsManager.onAdsLog(AdsLog(key, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.LOAD_FAILED, null))
            AdsManager.onAdsLog(AdsLog(key, adUnitId, AdsLog.Action.LOAD, reason, null))
            currentIndex++
            loadNextAd(activity, mode)
            return
        }
        val loader = loaderFor(activity, adUnitId)

        loader.setNativeAdListener(object : MaxNativeAdListener() {
            override fun onNativeAdLoaded(nativeAdView: MaxNativeAdView?, ad: MaxAd) {
                val loaded = LoadedAd(loader, ad)
                activeAds.add(loaded)

                AdsManager.onAdsLog(
                    AdsLog(key, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.LOAD_SUCCESS, null)
                )

                when (mode) {
                    LoadMode.CACHE -> {
                        cachedAds.addLast(loaded)

                        isLoading = false
                        tryShowPending()

                        if (desiredCacheSize > 0 && cachedAds.size < desiredCacheSize) {
                            preload(activity, desiredCacheSize)
                        }
                    }

                    LoadMode.NO_CACHE -> {
                        isLoading = false

                        val pending = pendingShow
                        val container = pending?.containerRef?.get()

                        if (pending != null && container != null) {
                            populateNativeAdView(activity, pending.layoutResId, loaded, container)
                            pendingShow = null
                        } else {
                            releaseAd(loaded)
                        }
                    }
                }
            }

            override fun onNativeAdLoadFailed(id: String, error: MaxError) {
                AdsManager.onAdsLog(
                    AdsLog(key, adUnitId, AdsLog.Action.LOAD, AdsLog.Mess.LOAD_FAILED, error)
                )

                currentIndex++

                loadNextAd(activity, mode)
            }

            override fun onNativeAdClicked(ad: MaxAd) {
                AdsManager.onAdsLog(
                    AdsLog(key, adUnitId, AdsLog.Action.ADS_CLICK, AdsLog.Action.SHOW, null)
                )
            }

            override fun onNativeAdExpired(ad: MaxAd) {
                // Ad hết hạn trong cache -> bỏ, lần show sau dùng ad khác / load mới
                cachedAds.firstOrNull { it.ad === ad }?.let { releaseAd(it) }
            }
        })

        loader.loadAd()
    }

    private fun tryShowPending() {

        val pending = pendingShow ?: return

        val activity = pending.activityRef.get()
        val container = pending.containerRef.get()

        AdsManager.onAdsLog(AdsLog(key, "", "try_show_pending", "call_show_pending", null))

        if (activity == null || container == null) {
            AdsManager.onAdsLog(AdsLog(key, "", "try_show_pending", "err_view_unavailable", null))
            pendingShow = null
            return
        }

        val ad = pollCachedAd()

        if (ad == null) {
            AdsManager.onAdsLog(AdsLog(key, "", "try_show_pending", "err_ad_null", null))
            return
        }

        AdsManager.onAdsLog(AdsLog(key, "", "try_show_pending", AdsLog.Mess.CALL_SHOW, null))

        populateNativeAdView(activity, pending.layoutResId, ad, container)

        pendingShow = null

        // FIX cacheSize = 0
        if (desiredCacheSize > 0 && cachedAds.size < desiredCacheSize && !isLoading) {
            preload(activity, desiredCacheSize)
        }
    }

    fun show(
        activity: Activity,
        container: FrameLayout,
        layoutResId: Int,
        loadIfMissing: Boolean = false,
        cacheSize: Int = 1
    ): Boolean {

        if (!AdsController.canShowAds()) {
            container.removeAllViews()
            destroy()
            return false
        }

        desiredCacheSize = cacheSize.coerceAtLeast(0)

        pollCachedAd()?.let { ad ->

            populateNativeAdView(activity, layoutResId, ad, container)

            // FIX cacheSize = 0
            if (desiredCacheSize > 0 && cachedAds.size < desiredCacheSize && !isLoading) {
                preload(activity, desiredCacheSize)
            }

            return true
        }

        pendingShow = PendingShow(
            activityRef = WeakReference(activity),
            containerRef = WeakReference(container),
            layoutResId = layoutResId
        )

        if (loadIfMissing && !isLoading) {
            preload(activity, desiredCacheSize)
        }

        return false
    }

    fun loadAndShow(
        activity: Activity, container: FrameLayout, layoutResId: Int, cacheSize: Int = 1
    ): Boolean {

        if (!AdsController.canShowAds()) {
            container.removeAllViews()
            destroy()
            return false
        }

        desiredCacheSize = cacheSize.coerceAtLeast(0)

        pollCachedAd()?.let { ad ->

            populateNativeAdView(activity, layoutResId, ad, container)

            if (desiredCacheSize > 0 && cachedAds.size < desiredCacheSize && !isLoading) {
                preload(activity, desiredCacheSize)
            }

            return true
        }

        pendingShow = PendingShow(
            WeakReference(activity), WeakReference(container), layoutResId
        )

        if (!isLoading) {

            if (desiredCacheSize == 0) {
                loadSingleAdForShow(activity)
            } else {
                preload(activity, desiredCacheSize)
            }
        }

        return false
    }

    private fun loadSingleAdForShow(activity: Activity) {

        if (adUnitIds.isEmpty() || isLoading) return

        isLoading = true
        currentIndex = 0

        loadNextAd(activity, LoadMode.NO_CACHE)
    }

    private fun populateNativeAdView(
        activity: Activity,
        layoutResId: Int,
        loaded: LoadedAd,
        container: FrameLayout,
    ) {

        if (activity.isFinishing || activity.isDestroyed) {
            AdsManager.onAdsLog(
                AdsLog(key, "", AdsLog.Action.SHOW, AdsLog.Mess.ERR_ACTIVITY_UNAVAILABLE, null)
            )
            releaseAd(loaded)
            return
        }

        AdsManager.onAdsLog(AdsLog(key, "", AdsLog.Action.SHOW, AdsLog.Mess.START_SHOW, null))

        val adView = createAdView(activity, layoutResId, container)
        if (!loaded.loader.render(adView, loaded.ad)) {
            AdsManager.onAdsLog(AdsLog(key, "", AdsLog.Action.SHOW, AdsLog.Mess.SHOW_FAILED, null))
            releaseAd(loaded)
            return
        }

        container.removeAllViews()
        container.addView(adView)

        // Field nào ad không có thì ẩn view tương ứng (giống bản AdMob)
        val nativeAd = loaded.ad.nativeAd
        adView.titleTextView?.hideIfEmpty(nativeAd?.title)
        adView.bodyTextView?.hideIfEmpty(nativeAd?.body)
        adView.advertiserTextView?.hideIfEmpty(nativeAd?.advertiser)
        adView.callToActionButton?.hideIfEmpty(nativeAd?.callToAction)
        adView.starRatingContentViewGroup?.let { rating ->
            rating.visibility = if (nativeAd?.starRating != null) View.VISIBLE else View.GONE
            // MAX add view sao MATCH_PARENT x MATCH_PARENT: container wrap_content sẽ giãn hết màn,
            // đẩy media/title ra ngoài (native trắng). wrap_content -> kích thước cố định của dải sao.
            val density = activity.resources.displayMetrics.density
            rating.layoutParams = rating.layoutParams?.apply {
                if (width == ViewGroup.LayoutParams.WRAP_CONTENT) width = (RATING_WIDTH_DP * density).toInt()
                if (height == ViewGroup.LayoutParams.WRAP_CONTENT) height = (RATING_HEIGHT_DP * density).toInt()
            }
        }

        adView.findViewById<View>(R.id.btnClose)?.setOnClickListener {
            AdsManager.onAdsLog(AdsLog(key, "", "ad_click", "close_click", null))
            container.removeAllViews()
            destroy()
        }

        AdsManager.onAdsLog(AdsLog(key, "", AdsLog.Action.SHOW, AdsLog.Mess.SHOW_DONE, null))
    }

    /** Inflate layout của app rồi chỉ bind những id có trong layout. */
    private fun createAdView(activity: Activity, layoutResId: Int, container: FrameLayout): MaxNativeAdView {
        val root = activity.layoutInflater.inflate(layoutResId, container, false)
        val builder = MaxNativeAdViewBinder.Builder(root)
        fun has(id: Int) = root.findViewById<View>(id) != null
        if (has(R.id.primary)) builder.setTitleTextViewId(R.id.primary)
        if (has(R.id.body)) builder.setBodyTextViewId(R.id.body)
        if (has(R.id.secondary)) builder.setAdvertiserTextViewId(R.id.secondary)
        if (has(R.id.icon)) builder.setIconImageViewId(R.id.icon)
        if (has(R.id.cta)) builder.setCallToActionButtonId(R.id.cta)
        if (has(R.id.media_view)) builder.setMediaContentViewGroupId(R.id.media_view)
        if (has(R.id.rating_bar)) builder.setStarRatingContentViewGroupId(R.id.rating_bar)
        if (has(R.id.ad_options_view)) builder.setOptionsContentViewGroupId(R.id.ad_options_view)
        return MaxNativeAdView(builder.build(), activity).apply {
            // MaxNativeAdView tự addView(root) không kèm params -> root bị wrap_content, layout full màn
            // (match_parent + media 0dp/constraint) co về 0. Giữ đúng kích thước root khai báo trong XML.
            val rootParams = root.layoutParams
            if (rootParams != null) {
                layoutParams = FrameLayout.LayoutParams(rootParams.width, rootParams.height)
                root.layoutParams = FrameLayout.LayoutParams(rootParams.width, rootParams.height)
            }
        }
    }

    private fun View.hideIfEmpty(text: String?) {
        visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    fun destroy() {
        activeAds.forEach { it.loader.destroy(it.ad) }
        activeAds.clear()
        cachedAds.clear()
        loaders.values.forEach { it.destroy() }
        loaders.clear()

        isLoading = false
        pendingShow = null
        currentIndex = 0
    }

    private fun pollCachedAd(): LoadedAd? {

        while (cachedAds.isNotEmpty()) {

            val ad = cachedAds.removeFirst()

            if (activeAds.contains(ad) && ad.ad.nativeAd?.isExpired != true) {
                return ad
            }
            releaseAd(ad)
        }

        return null
    }

    private fun releaseAd(ad: LoadedAd) {
        cachedAds.remove(ad)
        if (activeAds.remove(ad)) ad.loader.destroy(ad.ad)
    }

    private companion object {
        // Kích thước dải 5 sao khi layout để rating_bar wrap_content
        const val RATING_WIDTH_DP = 80
        const val RATING_HEIGHT_DP = 16
    }
}
