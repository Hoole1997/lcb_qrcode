package com.android.common.bill.ads.bidding

import android.content.Context
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.admob.AdmobNativeAdController
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.pangle.PangleNativeAdController
import com.android.common.bill.ads.topon.TopOnNativeAdController
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.util.AdmobStackReflectionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.Locale

/**
 * 原生广告竞价控制器
 * 同时加载 AdMob、Pangle、TopOn，比较收益后选择展示
 */
object NativeBiddingManager {

    suspend fun bidding(
        context: Context,
        admobAdUnitId: String = BillConfig.admob.nativeId,
        pangleAdUnitId: String = BillConfig.pangle.nativeId,
        toponPlacementId: String = BillConfig.topon.nativeId,
    ): BiddingWinner {
        // 检查固定聚合源（包含初始化和频控检查）
        when (val result = AdSourceController.checkFixedSource(AdType.NATIVE)) {
            is AdSourceController.FixedSourceCheckResult.UseFixedSource -> {
                return result.winner
            }
            is AdSourceController.FixedSourceCheckResult.UseBidding -> {
                // 继续使用竞价逻辑
            }
        }
        
        return performBidding(context, admobAdUnitId, pangleAdUnitId, toponPlacementId)
    }

    private suspend fun performBidding(
        context: Context,
        admobAdUnitId: String,
        pangleAdUnitId: String,
        toponPlacementId: String,
    ): BiddingWinner {
        val applicationContext = context.applicationContext
        val admobController = AdmobNativeAdController.getInstance()
        val pangleController = PangleNativeAdController.getInstance()
        val toponController = TopOnNativeAdController.getInstance()
        
        // 根据平台配置决定是否参与比价
        val admobConfigEnabled = BiddingPlatformController.isAdmobEnabled(AdType.NATIVE)
        val pangleConfigEnabled = BiddingPlatformController.isPangleEnabled(AdType.NATIVE)
        val toponConfigEnabled = BiddingPlatformController.isToponEnabled(AdType.NATIVE)
        
        // 检查频率限制，过滤掉被限制的平台
        val admobEnabled = admobConfigEnabled && BiddingExclusionController.canPlatformBid(AdType.NATIVE, AdPlatform.ADMOB)
        val pangleEnabled = pangleConfigEnabled && BiddingExclusionController.canPlatformBid(AdType.NATIVE, AdPlatform.PANGLE)
        val toponEnabled = toponConfigEnabled && BiddingExclusionController.canPlatformBid(AdType.NATIVE, AdPlatform.TOPON)

        // 生成竞价唯一ID
        val bidId = BiddingTracker.generateBidId()
        
        // 构建参与平台列表
        val platformList = mutableListOf<AdPlatform>()
        if (admobEnabled) platformList.add(AdPlatform.ADMOB)
        if (pangleEnabled) platformList.add(AdPlatform.PANGLE)
        if (toponEnabled) platformList.add(AdPlatform.TOPON)
        
        // 上报竞价开始
        val startTime = System.currentTimeMillis()
        AdLogger.d("============= 原生广告竞价开始 =============")
        BiddingTracker.reportBidStart(bidId, AdType.NATIVE, platformList)
        if (platformList.isEmpty()) {
            BiddingTracker.reportBidFailNoPlatform(bidId, AdType.NATIVE, "no_platform_available_after_exclusion")
            AdLogger.w("原生广告无可参与竞价平台（初始化/配置/频控后全部不可用）")
            return BiddingWinner.ADMOB
        }

        // 缓存优先：检查各平台缓存状态
        val admobHasCache = admobEnabled && admobController.peekCurrentAd(admobAdUnitId) != null
        val pangleHasCache = pangleEnabled && pangleController.getCurrentAd(pangleAdUnitId) != null
        val toponHasCache = toponEnabled && toponController.peekCachedAd(toponPlacementId) != null
        val anyCached = admobHasCache || pangleHasCache || toponHasCache

        val needLoadPlatforms = mutableListOf<AdPlatform>()
        if (admobEnabled && !admobHasCache) needLoadPlatforms.add(AdPlatform.ADMOB)
        if (pangleEnabled && !pangleHasCache) needLoadPlatforms.add(AdPlatform.PANGLE)
        if (toponEnabled && !toponHasCache) needLoadPlatforms.add(AdPlatform.TOPON)

        AdLogger.d("原生缓存状态 -> AdMob:$admobHasCache, Pangle:$pangleHasCache, TopOn:$toponHasCache, anyCached:$anyCached, 需加载:${needLoadPlatforms.map { it.key }}")

        // 上报无缓存平台
        if (admobEnabled && !admobHasCache) AdEventReporter.reportNoCache(AdType.NATIVE, AdPlatform.ADMOB, admobAdUnitId)
        if (pangleEnabled && !pangleHasCache) AdEventReporter.reportNoCache(AdType.NATIVE, AdPlatform.PANGLE, pangleAdUnitId)
        if (toponEnabled && !toponHasCache) AdEventReporter.reportNoCache(AdType.NATIVE, AdPlatform.TOPON, toponPlacementId)

        if (needLoadPlatforms.isNotEmpty()) {
            if (anyCached) {
                // 有缓存：非缓存平台后台fire-and-forget预加载，不阻塞竞价
                AdLogger.d("原生有缓存平台，非缓存平台后台预加载，秒出竞价结果")
                val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                for (platform in needLoadPlatforms) {
                    bgScope.launch {
                        runCatching {
                            when (platform) {
                                AdPlatform.ADMOB -> admobController.preloadAd(applicationContext, admobAdUnitId)
                                AdPlatform.PANGLE -> pangleController.preloadAd(applicationContext, pangleAdUnitId)
                                AdPlatform.TOPON -> toponController.preloadAd(applicationContext, toponPlacementId)
                            }
                        }
                    }
                }
            } else {
                // 全无缓存：并行加载+7s超时
                supervisorScope {
                    val admobDeferred = if (AdPlatform.ADMOB in needLoadPlatforms) async {
                        runCatching { admobController.preloadAd(applicationContext, admobAdUnitId) }.getOrNull()
                    } else null
                    val pangleDeferred = if (AdPlatform.PANGLE in needLoadPlatforms) async {
                        runCatching { pangleController.preloadAd(applicationContext, pangleAdUnitId) }.getOrNull()
                    } else null
                    val toponDeferred = if (AdPlatform.TOPON in needLoadPlatforms) async {
                        runCatching { toponController.preloadAd(applicationContext, toponPlacementId) }.getOrNull()
                    } else null

                    awaitBidPreloadsWithTimeout(
                        timeoutMs = 7000L,
                        timeoutLog = "原生广告竞价加载超时(7s)，取消未完成的加载任务并使用已完成的结果",
                        admobDeferred,
                        pangleDeferred,
                        toponDeferred
                    )
                }
            }
        } else {
            AdLogger.d("原生所有启用平台都有缓存，跳过加载")
        }

        // 计算竞价耗时
        val biddingDuration = System.currentTimeMillis() - startTime

        // 上报加载后仍无缓存的平台为竞价失败
        if (admobEnabled && admobController.peekCurrentAd(admobAdUnitId) == null) {
            BiddingTracker.reportBidFail(bidId, AdPlatform.ADMOB, "no_cache_after_load")
        }
        if (pangleEnabled && pangleController.getCurrentAd(pangleAdUnitId) == null) {
            BiddingTracker.reportBidFail(bidId, AdPlatform.PANGLE, "no_cache_after_load")
        }
        if (toponEnabled && toponController.peekCachedAd(toponPlacementId) == null) {
            BiddingTracker.reportBidFail(bidId, AdPlatform.TOPON, "no_cache_after_load")
        }

        // 从缓存获取各平台 eCPM
        val admobValueUsd = if (admobEnabled) {
            admobController.peekCurrentAd(admobAdUnitId)?.let { ad ->
                AdmobStackReflectionUtils.getNativeEcpmMicros(ad).toDouble().div(1_000_000.0)
            } ?: 0.0
        } else 0.0

        val pangleValueUsd = if (pangleEnabled) {
            pangleController.getCurrentAd(pangleAdUnitId)?.winEcpm?.revenue?.toDoubleOrNull() ?: 0.0
        } else 0.0

        val toponValueUsd = if (toponEnabled) {
            toponController.peekCachedAd(toponPlacementId)?.let { ad ->
                runCatching { ad.checkValidAdCaches().firstOrNull()?.publisherRevenue }.getOrNull() ?: 0.0
            } ?: 0.0
        } else 0.0

        val biddingLog = String.format(
            Locale.US,
            "原生竞价结果 -> AdMob: %.8f 美元%s, Pangle: %.8f 美元%s, TopOn: %.8f 美元%s",
            admobValueUsd, if (admobEnabled) "" else "(禁用)",
            pangleValueUsd, if (pangleEnabled) "" else "(禁用)",
            toponValueUsd, if (toponEnabled) "" else "(禁用)"
        )
        AdLogger.d(biddingLog)
        BiddingTracker.reportBiddingLog(biddingLog)

        // 只在启用的平台中选择胜出者
        val candidates = mutableListOf<Pair<BiddingWinner, Double>>()
        if (admobEnabled) candidates.add(BiddingWinner.ADMOB to admobValueUsd)
        if (pangleEnabled) candidates.add(BiddingWinner.PANGLE to pangleValueUsd)
        if (toponEnabled) candidates.add(BiddingWinner.TOPON to toponValueUsd)

        val winner = candidates.maxByOrNull { it.second }?.first ?: BiddingWinner.ADMOB
        val winnerCpm = candidates.maxByOrNull { it.second }?.second ?: 0.0
        
        // 上报竞价胜出
        val winnerPlatform = when (winner) {
            BiddingWinner.ADMOB -> AdPlatform.ADMOB
            BiddingWinner.PANGLE -> AdPlatform.PANGLE
            BiddingWinner.TOPON -> AdPlatform.TOPON
        }
        BiddingTracker.reportBidWin(bidId, winnerPlatform, biddingDuration, winnerCpm)
        AdLogger.d("============= 原生广告竞价结束 =============")

        return winner
    }
}
