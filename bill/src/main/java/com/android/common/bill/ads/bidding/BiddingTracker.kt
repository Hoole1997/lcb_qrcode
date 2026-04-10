package com.android.common.bill.ads.bidding

import net.corekit.core.report.ReportDataManager
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.log.AdLogger
import java.util.Locale
import java.util.UUID

/**
 * 竞价埋点控制器
 * 统一管理竞价相关的埋点上报
 */
object BiddingTracker {
    
    /**
     * 生成唯一的竞价ID
     */
    fun generateBidId(): String = UUID.randomUUID().toString()
    
    /**
     * 上报竞价开始
     * @param bidId 竞价ID
     * @param adType 广告类型
     * @param platforms 参与竞价的平台列表
     */
    fun reportBidStart(bidId: String, adType: AdType, platforms: List<AdPlatform>) {
        val platformNames = platforms.map { it.key }
        ReportDataManager.reportData("ad_bid_start", mapOf(
            "bid_id" to bidId,
            "ad_format" to adType.configKey,
            "platforms" to platformNames.joinToString(",")
        ))
        AdLogger.d("竞价开始: bid_id=$bidId, ad_format=${adType.configKey}, platforms=$platformNames")
    }
    
    /**
     * 上报竞价胜出
     * @param bidId 竞价ID
     * @param winner 胜出平台
     * @param durationMs 竞价耗时（毫秒）
     * @param cpm CPM 收益
     */
    fun reportBidWin(bidId: String, winner: AdPlatform, durationMs: Long, cpm: Double) {
        ReportDataManager.reportData("ad_bid_win", mapOf(
            "bid_id" to bidId,
            "ad_platform" to winner.key,
            "duration_ms" to durationMs,
            "cpm" to String.format(Locale.US, "%.8f", cpm)
        ))
        AdLogger.d("竞价胜出: bid_id=$bidId, ad_platform=${winner.key}, duration=${durationMs}ms, cpm=$cpm")
    }
    
    /**
     * 上报竞价失败（单个平台）
     * @param bidId 竞价ID
     * @param platform 失败的平台
     * @param reason 失败原因
     */
    fun reportBidFail(bidId: String, platform: AdPlatform, reason: String) {
        ReportDataManager.reportData("ad_bid_fail", mapOf(
            "bid_id" to bidId,
            "ad_platform" to platform.key,
            "reason" to reason
        ))
        AdLogger.d("竞价失败: bid_id=$bidId, ad_platform=${platform.key}, reason=$reason")
    }
    
    /**
     * 上报竞价失败（无可参与平台）
     * @param bidId 竞价ID
     * @param adType 广告类型
     * @param reason 失败原因
     */
    fun reportBidFailNoPlatform(bidId: String, adType: AdType, reason: String) {
        ReportDataManager.reportData(
            "ad_bid_fail",
            mapOf(
                "bid_id" to bidId,
                "ad_format" to adType.configKey,
                "reason" to reason
            )
        )
        AdLogger.d("竞价失败: bid_id=$bidId, ad_format=${adType.configKey}, reason=$reason")
    }
    
    /**
     * 上报竞价日志（详细竞价结果）
     * @param log 日志内容
     */
    fun reportBiddingLog(log: String) {
        ReportDataManager.reportDataByName("ThinkingData", "bidding", mapOf("log" to log))
    }
    
    /**
     * 检查平台是否可以参与竞价（频率限制检查）
     * @param adType 广告类型
     * @param platform 平台
     * @return true 可以参与竞价，false 被频率限制
     * @deprecated 请使用 [BiddingExclusionController.canPlatformBid] 替代，该方法已迁移并整合了加载失败排除逻辑
     */
    @Deprecated(
        message = "请使用 BiddingExclusionController.canPlatformBid() 替代",
        replaceWith = ReplaceWith("BiddingExclusionController.canPlatformBid(adType, platform)")
    )
    fun canPlatformBid(adType: AdType, platform: AdPlatform): Boolean {
        val config = AdConfigManager.getPlatformConfig(adType, platform) ?: return true
        
        // 检查每日展示次数限制
        if (config.getDailyShowCount() >= config.getMaxDailyShow()) {
            AdLogger.d("${platform.key} 被频率限制忽略（展示次数达上限: ${config.getDailyShowCount()}/${config.getMaxDailyShow()}）")
            return false
        }
        
        // 检查每日点击次数限制
        if (config.getDailyClickCount() >= config.getMaxDailyClick()) {
            AdLogger.d("${platform.key} 被频率限制忽略（点击次数达上限: ${config.getDailyClickCount()}/${config.getMaxDailyClick()}）")
            return false
        }
        
        // 检查展示时间间隔限制
        if (config.getLastShowInterval() < config.getMinInterval()) {
            AdLogger.d("${platform.key} 被频率限制忽略（展示间隔不足: ${config.getLastShowInterval()}s < ${config.getMinInterval()}s）")
            return false
        }
        
        return true
    }
    
    /**
     * 获取可以参与竞价的平台列表
     * @param adType 广告类型
     * @param enabledPlatforms 已启用的平台列表（从 BiddingPlatformController 获取）
     * @return 可以参与竞价的平台列表
     * @deprecated 请使用 [BiddingExclusionController.getAvailablePlatforms] 替代
     */
    @Deprecated(
        message = "请使用 BiddingExclusionController.getAvailablePlatforms() 替代",
        replaceWith = ReplaceWith("BiddingExclusionController.getAvailablePlatforms(adType, enabledPlatforms)")
    )
    fun getAvailablePlatforms(adType: AdType, enabledPlatforms: Map<AdPlatform, Boolean>): List<AdPlatform> {
        return enabledPlatforms.filter { (platform, enabled) ->
            enabled && canPlatformBid(adType, platform)
        }.keys.toList()
    }
}
