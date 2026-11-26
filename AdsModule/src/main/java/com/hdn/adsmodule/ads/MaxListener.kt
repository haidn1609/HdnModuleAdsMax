package com.hdn.adsmodule.ads

import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxReward
import com.applovin.mediation.MaxRewardedAdListener

/**
 * MAX dùng 1 listener cho cả load lẫn show; lambda gán lại lúc show để giữ luồng giống bản AdMob
 * (callback load riêng, FullScreenContentCallback riêng). Dùng chung cho inter/reward/open.
 */
internal class MaxListener : MaxRewardedAdListener {
    var onLoaded: (MaxAd) -> Unit = {}
    var onLoadFailed: (MaxError) -> Unit = {}
    var onDisplayed: () -> Unit = {}
    var onHidden: () -> Unit = {}
    var onClicked: () -> Unit = {}
    var onDisplayFailed: (MaxError) -> Unit = {}
    var onRewarded: () -> Unit = {}

    override fun onAdLoaded(ad: MaxAd) = onLoaded(ad)
    override fun onAdLoadFailed(adUnitId: String, error: MaxError) = onLoadFailed(error)
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
