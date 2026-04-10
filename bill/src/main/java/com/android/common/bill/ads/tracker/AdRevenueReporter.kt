package com.android.common.bill.ads.tracker

import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.log.AdLogger
import net.corekit.core.ads.RevenueAdData
import net.corekit.core.ads.RevenueAdManager
import net.corekit.core.ads.RevenueInfo

/**
 * 广告收益上报器
 * 统一管理所有广告收益的上报
 */
object AdRevenueReporter {

    private const val TAG = "AdRevenueReporter"

    /**
     * 上报广告收益
     * @param adType 广告类型
     * @param platform 广告平台
     * @param adUnitId 广告位ID
     * @param value 收益值
     * @param currency 货币代码，默认 USD
     * @param networkName 广告网络名称，默认使用平台名称
     * @param placement 广告位置
     */
    fun reportRevenue(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        value: Double,
        currency: String = "USD",
        networkName: String? = null,
        placement: String? = null
    ) {
        val adRevenueData = RevenueAdData(
            revenue = RevenueInfo(
                value = value,
                currencyCode = currency
            ),
            adRevenueNetwork = networkName ?: platform.key,
            adRevenueUnit = adUnitId,
            adRevenuePlacement = placement ?: "",
            adFormat = adType.configKey
        )

        RevenueAdManager.reportAdRevenue(adRevenueData)
        AdLogger.d(
            "$TAG 收益上报: format=%s, platform=%s, adUnitId=%s, revenue=%.6f %s, network=%s",
            adType.configKey,
            platform.key,
            adUnitId,
            value,
            currency,
            networkName ?: platform.key
        )
    }
}
