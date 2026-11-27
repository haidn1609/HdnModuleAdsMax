package com.hdn.adsmodule.ads

import android.os.Handler
import android.os.Looper
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxReward
import com.applovin.mediation.MaxRewardedAdListener

/**
 * MAX dùng 1 listener cho cả load lẫn show; lambda gán lại lúc show để giữ luồng giống bản AdMob
 * (callback load riêng, FullScreenContentCallback riêng). Dùng chung cho inter/reward/open.
 *
 * MAX không phải lúc nào cũng báo lỗi load (vd SDK key sai/trống: kẹt LOADING mãi), nên mỗi lần load
 * có timeout [AdsController.loadTimeoutMs]: hết giờ coi như load fail (error = null). Callback load
 * ngoài lượt [startLoad] (về trễ sau timeout, MAX tự reload ad hết hạn) bị bỏ qua.
 */
internal class MaxListener : MaxRewardedAdListener {
    var onLoaded: (MaxAd) -> Unit = {}
    var onLoadFailed: (MaxError?) -> Unit = {}
    var onDisplayed: () -> Unit = {}
    var onHidden: () -> Unit = {}
    var onClicked: () -> Unit = {}
    var onDisplayFailed: (MaxError) -> Unit = {}
    var onRewarded: () -> Unit = {}

    private val handler = Handler(Looper.getMainLooper())
    private var loadPending = false
    private val timeoutRunnable = Runnable {
        if (loadPending) {
            loadPending = false
            onLoadFailed(null)
        }
    }

    /** Gọi ngay trước loadAd(). */
    fun startLoad() {
        loadPending = true
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, AdsController.loadTimeoutMs)
    }

    private fun finishLoad(): Boolean {
        if (!loadPending) return false
        loadPending = false
        handler.removeCallbacks(timeoutRunnable)
        return true
    }

    override fun onAdLoaded(ad: MaxAd) {
        if (finishLoad()) onLoaded(ad)
    }

    override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
        if (finishLoad()) onLoadFailed(error)
    }

    override fun onAdDisplayed(ad: MaxAd) = onDisplayed()
    override fun onAdHidden(ad: MaxAd) = onHidden()
    override fun onAdClicked(ad: MaxAd) = onClicked()
    override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) = onDisplayFailed(error)
    override fun onUserRewarded(ad: MaxAd, reward: MaxReward) = onRewarded()

    /** Show xong/huỷ: bỏ callback show để lần load sau không bắn nhầm. */
    fun clearShowCallbacks() {
        onDisplayed = {}
        onHidden = {}
        onClicked = {}
        onDisplayFailed = {}
        onRewarded = {}
    }
}
