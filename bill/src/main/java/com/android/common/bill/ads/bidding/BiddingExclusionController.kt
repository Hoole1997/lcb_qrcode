package com.android.common.bill.ads.bidding

import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.log.AdLogger
import net.corekit.core.report.ReportDataManager

/**
 * 竞价排除控制器
 * 管理平台的竞价频率限制逻辑：展示/点击次数达上限、展示间隔不足
 */
object BiddingExclusionController {
    
    private data class FrequencyLimitDecision(
        val canBid: Boolean,
        val exclusionType: String = "",
        val reason: String = "",
        val detail: String = ""
    )

    /**
     * 检查平台是否可以参与竞价（仅检查频率限制，会上报排除埋点）
     * 注意：竞价流程中应只调用一次，避免重复上报
     *
     * @param adType 广告类型
     * @param platform 平台
     * @return true 可以参与竞价，false 被排除
     */
    fun canPlatformBid(adType: AdType, platform: AdPlatform): Boolean {
        return checkFrequencyLimit(adType, platform, shouldReport = true)
    }

    /**
     * 静默检查平台是否可以参与竞价（不上报埋点）
     * 用于缓存检查等非正式竞价场景，避免重复上报
     *
     * @param adType 广告类型
     * @param platform 平台
     * @return true 可以参与竞价，false 被排除
     */
    fun canPlatformBidSilent(adType: AdType, platform: AdPlatform): Boolean {
        return checkFrequencyLimit(adType, platform, shouldReport = false)
    }
    
    /**
     * 获取平台被频控阻断的详细原因（不上报埋点）
     * @return null 表示未被频控阻断
     */
    fun getBlockReasonWithDetail(adType: AdType, platform: AdPlatform): String? {
        val decision = evaluateFrequencyLimit(adType, platform)
        if (decision.canBid) return null
        return if (decision.detail.isNotEmpty()) {
            "${decision.reason}(${decision.detail})"
        } else {
            decision.reason
        }
    }

    /**
     * 检查频率限制
     * @param shouldReport 是否上报排除埋点
     */
    private fun checkFrequencyLimit(adType: AdType, platform: AdPlatform, shouldReport: Boolean): Boolean {
        val decision = evaluateFrequencyLimit(adType, platform)
        if (!decision.canBid) {
            AdLogger.d("${platform.key} 被频率限制排除（${decision.detail}）")
            if (shouldReport) {
                reportExclusion(adType, platform, decision.exclusionType, decision.reason)
            }
        }
        return decision.canBid
    }
    
    private fun evaluateFrequencyLimit(adType: AdType, platform: AdPlatform): FrequencyLimitDecision {
        val config = AdConfigManager.getPlatformConfig(adType, platform) ?: return FrequencyLimitDecision(canBid = true)

        val dailyShow = config.getDailyShowCount()
        val maxDailyShow = config.getMaxDailyShow()
        if (dailyShow >= maxDailyShow) {
            return FrequencyLimitDecision(
                canBid = false,
                exclusionType = "frequency_limit",
                reason = "show_limit_exceeded",
                detail = "show=$dailyShow/$maxDailyShow"
            )
        }

        val dailyClick = config.getDailyClickCount()
        val maxDailyClick = config.getMaxDailyClick()
        if (dailyClick >= maxDailyClick) {
            return FrequencyLimitDecision(
                canBid = false,
                exclusionType = "frequency_limit",
                reason = "click_limit_exceeded",
                detail = "click=$dailyClick/$maxDailyClick"
            )
        }

        val interval = config.getLastShowInterval()
        if (interval < 0) {
            AdLogger.w("${platform.key} 检测到系统时间回拨（间隔=${interval}s），重置展示时间记录")
            config.resetLastShowTime()
            return FrequencyLimitDecision(canBid = true)
        }

        val minInterval = config.getMinInterval()
        if (interval < minInterval) {
            return FrequencyLimitDecision(
                canBid = false,
                exclusionType = "frequency_limit",
                reason = "interval_not_met",
                detail = "interval=${interval}s/${minInterval}s"
            )
        }

        return FrequencyLimitDecision(canBid = true)
    }

    /**
     * 上报平台被排除事件
     */
    private fun reportExclusion(
        adType: AdType,
        platform: AdPlatform,
        exclusionType: String,
        reason: String
    ) {
        ReportDataManager.reportData(
            "ad_bid_excluded",
            mapOf(
                "ad_format" to adType.configKey,
                "ad_platform" to platform.key,
                "exclusion_type" to exclusionType,
                "reason" to reason
            )
        )
    }
}
