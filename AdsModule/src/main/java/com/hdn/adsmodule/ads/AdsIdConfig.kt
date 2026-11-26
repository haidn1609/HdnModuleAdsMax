package com.hdn.adsmodule.ads

/**
 * ID ad unit MAX. Không có ID test công khai như AdMob: debug dùng chính các ID này,
 * thiết bị test khai báo qua [AdsManager.initialize] (testDeviceIds) sẽ nhận quảng cáo test.
 */
class AdsIdConfig {
    object Release {
        var APP_OPEN = ""
        var BANNER = ""
        var BANNER_COLLAPSIBLE = ""
        var INTERSTITIAL = ""
        var INTERSTITIAL_SPLASH = ""
        var REWARDED = ""
    }

    object Remote {
        var APP_OPEN = ""
        var BANNER = ""
        var BANNER_COLLAPSIBLE = ""
        var INTERSTITIAL = ""
        var INTERSTITIAL_SPLASH = ""
        var REWARDED = ""
    }
}
