package com.hdn.adsmodule.ads

object AdsController {
    var isDebug = true
    var adsEnable = true
    var isVip = false

    // Thời gian tối đa chờ 1 ad unit load (ms); hết giờ coi như fail, thử ID tiếp theo trong waterfall
    var loadTimeoutMs = 15_000L

    // Key truyền vào AdsManager.initialize; sai thì MAX chỉ log lỗi, không gọi callback load
    var sdkKey = ""

    private val adUnitIdRegex = Regex("^[0-9a-fA-F]{16}$")

    /**
     * Lý do không thể load [adUnitId] (null = load được). MAX chỉ log lỗi chứ không báo onAdLoadFailed
     * khi SDK key / ad unit sai định dạng, nên kiểm tra trước để fail ngay thay vì chờ timeout.
     */
    fun invalidLoadReason(adUnitId: String): String? = when {
        sdkKey.length < 86 -> "invalid_sdk_key" // cùng ngưỡng SDK MAX dùng
        !adUnitIdRegex.matches(adUnitId) -> "invalid_ad_unit_id"
        else -> null
    }

    fun canShowAds(): Boolean {
        return adsEnable && !isVip
    }

    // useWithoutVip = true: vẫn cho show dù đang VIP (giống RewardAds)
    fun canShowAds(useWithoutVip: Boolean): Boolean {
        return adsEnable && (!isVip || useWithoutVip)
    }
}
