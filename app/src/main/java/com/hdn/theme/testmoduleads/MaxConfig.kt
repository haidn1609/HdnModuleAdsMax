package com.hdn.theme.testmoduleads

import com.google.gson.annotations.SerializedName

/**
 * Remote Config `config_ads_max` (docs/config_ads_max.json): SDK key + ad unit MAX theo đúng tên slot.
 * Mỗi slot là list ID (waterfall). Slot thiếu / ID sai định dạng -> dùng ID mặc định trong [MaxTestIds].
 */
data class MaxConfig(
    @SerializedName("sdk_key") val sdkKey: String? = null,
    @SerializedName("bn_inapp") val bnInapp: List<String>? = null,
    @SerializedName("int_inapp") val intInapp: List<String>? = null,
    @SerializedName("rv_inapp") val rvInapp: List<String>? = null,
    @SerializedName("nt_inapp") val ntInapp: List<String>? = null,
    @SerializedName("int_splash") val intSplash: List<String>? = null,
    @SerializedName("nt_lang1") val ntLang1: List<String>? = null,
    @SerializedName("nt_lang2") val ntLang2: List<String>? = null,
    @SerializedName("nt_clp") val ntClp: List<String>? = null,
    @SerializedName("nt_onb_full") val ntOnbFull: List<String>? = null,
    @SerializedName("aoa") val aoa: List<String>? = null,
)
