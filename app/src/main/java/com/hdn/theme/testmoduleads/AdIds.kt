package com.hdn.theme.testmoduleads

/**
 * Điền SDK key + ad unit MAX của bạn để chạy demo. ID trống: slot đó chỉ load fail (xem log "Ads_log").
 * Mỗi slot là 1 ID hoặc JSON array ID (waterfall), ví dụ "[\"id1\",\"id2\"]".
 */
object AdIds {
    const val SDK_KEY = ""

    // GAID máy test: nhận quảng cáo test của MAX
    val TEST_DEVICE_IDS: List<String> = listOf()

    const val APP_OPEN = ""
    const val INTERSTITIAL = ""
    const val INTER_SPLASH = ""
    const val REWARDED = ""
    const val BANNER = ""
    const val BANNER_COLLAPSE = ""
    const val NATIVE = ""
    const val NATIVE_COLLAPSE = ""
    const val NATIVE_DIALOG_FULL = ""

    fun apply() {
        val m = com.hdn.adsmodule.ads.AdsManager
        m.setOpaIdRl(APP_OPEN)
        m.setItaIdRl(INTERSTITIAL)
        m.setItsaIdRl(INTER_SPLASH)
        m.setRwaIdRl(REWARDED)
        m.setBnaIdRl(BANNER)
        m.setBncaIdRl(BANNER_COLLAPSE)
        m.addNative("native_home", NATIVE, "")
        m.addNative("native_collapse", NATIVE_COLLAPSE, "")
        m.addNative("native_dialog_full", NATIVE_DIALOG_FULL, "")
    }
}
