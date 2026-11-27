package com.hdn.theme.testmoduleads

/**
 * SDK key + ad unit MAX mặc định của demo (app "Draw Music Android" trên MAX), giống docs/config_ads_max.json.
 * Remote Config `config_ads_max` ghi đè slot nào có ID MAX hợp lệ (16 ký tự hex).
 */
object MaxTestIds {
    const val SDK_KEY = "k_mtuwdinJw2fDiQ4iCF6Nh8buCSERDJSLmHhd-LTH7f5TR9UAlu-w0kHC1_CKN16ZWEHPs4pbVMtrh6EepoE3"

    const val APP_OPEN = "b482cc80d027a187" // aoa
    const val INTERSTITIAL = "7c35edbee004f9da" // int_inapp
    const val INTER_SPLASH = "77ecb3fdfe3beb2b" // int_splash
    const val REWARDED = "075bd1fa3e32f092" // rv_inapp
    const val BANNER = "c3698ab5427eb3e0" // bn_inapp
    const val NATIVE = "5c39726a05f3c6c2" // nt_inapp
    const val NATIVE_COLLAPSE = "c25284c23a618ca3" // nt_clp
    const val NATIVE_FULL = "17ba72c4a809ad19" // nt_onb_full

    // Đã add native key native_lang1 / native_lang2, chưa có nút nào trong demo show
    const val NATIVE_LANG1 = "ea290b3eaa2295a6" // nt_lang1
    const val NATIVE_LANG2 = "79deb82bc5a9eb84" // nt_lang2

    /** GAID máy test để nhận quảng cáo test của MAX. */
    @JvmField
    val TEST_DEVICE_IDS: List<String> = listOf(
        "abee24e3-471c-4c59-8a93-4e25fac16699", // máy test 2506BPN68G
    )

    private val MAX_ID = Regex("^[0-9a-fA-F]{16}$")

    /** Chỉ giữ ID đúng định dạng MAX. */
    @JvmStatic
    fun maxIds(ids: List<String>?): List<String> = ids.orEmpty().map(String::trim).filter(MAX_ID::matches)
}
