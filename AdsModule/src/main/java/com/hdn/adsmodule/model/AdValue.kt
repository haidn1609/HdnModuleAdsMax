package com.hdn.adsmodule.model

import com.applovin.mediation.MaxAd

/**
 * Doanh thu 1 impression từ MAX (onAdRevenuePaid). MAX luôn báo USD.
 * [maxAd] giữ nguyên để app đọc thêm (network, placement, precision...).
 */
class AdValue(val maxAd: MaxAd) {
    /** Doanh thu USD của impression này. */
    val revenue: Double get() = maxAd.revenue

    /** Giống AdMob AdValue.valueMicros để app cũ dễ chuyển. */
    val valueMicros: Long get() = (maxAd.revenue * 1_000_000).toLong()

    val currencyCode: String get() = CURRENCY

    val networkName: String? get() = maxAd.networkName

    val adUnitId: String get() = maxAd.adUnitId

    companion object {
        const val CURRENCY = "USD"
    }
}
