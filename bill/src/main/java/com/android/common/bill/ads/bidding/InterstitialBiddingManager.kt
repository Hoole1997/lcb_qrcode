package com.android.common.bill.ads.bidding

import android.app.Activity
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.admob.AdmobInterstitialAdController
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.pangle.PangleInterstitialAdController
import com.android.common.bill.ads.topon.TopOnInterstitialAdController
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.util.AdmobStackReflectionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 插页广告竞价控制器
 * 同时加载 AdMob、Pangle、TopOn，比较收益后选择展示
 */
object InterstitialBiddingManager {

    suspend fun bidding(
        activity: Activity,
        admobAdUnitId: String = BillConfig.admob.interstitialId,
        pangleAdUnitId: String = BillConfig.pangle.interstitialId,
        toponPlacementId: String = BillConfig.topon.interstitialId,
    ): BiddingResult {
        // 检查固定聚合源（包含初始化和频控检查）
        when (val result = AdSourceController.checkFixedSource(AdType.INTERSTITIAL)) {
            is AdSourceController.FixedSourceCheckResult.UseFixedSource -> {
                return BiddingResult(result.winner, 0.0)
            }
            is AdSourceController.FixedSourceCheckResult.UseBidding -> {
                // 继续使用竞价逻辑
            }
        }
        
        return performBidding(activity, admobAdUnitId, pangleAdUnitId, toponPlacementId)
    }

    private suspend fun performBidding(
        activity: Activity,
        admobAdUnitId: String,
        pangleAdUnitId: String,
        toponPlacementId: String,
    ): BiddingResult {
        val context = activity.applicationContext
        val admobController = AdmobInterstitialAdController.getInstance()
        val pangleController = PangleInterstitialAdController.getInstance()
        val toponController = TopOnInterstitialAdController.getInstance()
        
        // 根据平台配置决定是否参与比价
        val admobConfigEnabled = BiddingPlatformController.isAdmobEnabled(AdType.INTERSTITIAL)
        val pangleConfigEnabled = BiddingPlatformController.isPangleEnabled(AdType.INTERSTITIAL)
        val toponConfigEnabled = BiddingPlatformController.isToponEnabled(AdType.INTERSTITIAL)
        
        // 检查频率限制，过滤掉被限制的平台
        val admobEnabled = admobConfigEnabled && BiddingExclusionController.canPlatformBid(AdType.INTERSTITIAL, AdPlatform.ADMOB)
        val pangleEnabled = pangleConfigEnabled && BiddingExclusionController.canPlatformBid(AdType.INTERSTITIAL, AdPlatform.PANGLE)
        val toponEnabled = toponConfigEnabled && BiddingExclusionController.canPlatformBid(AdType.INTERSTITIAL, AdPlatform.TOPON)

        // 生成竞价唯一ID
        val bidId = BiddingTracker.generateBidId()
        
        // 构建参与平台列表
        val platformList = mutableListOf<AdPlatform>()
        if (admobEnabled) platformList.add(AdPlatform.ADMOB)
        if (pangleEnabled) platformList.add(AdPlatform.PANGLE)
        if (toponEnabled) platformList.add(AdPlatform.TOPON)
        
        // 上报竞价开始
        val startTime = System.currentTimeMillis()
        AdLogger.d("============= 插页广告竞价开始 =============")
        BiddingTracker.reportBidStart(bidId, AdType.INTERSTITIAL, platformList)
        if (platformList.isEmpty()) {
            BiddingTracker.reportBidFailNoPlatform(bidId, AdType.INTERSTITIAL, "no_platform_available_after_exclusion")
            AdLogger.w("插页广告无可参与竞价平台（初始化/配置/频控后全部不可用）")
            return BiddingResult(BiddingWinner.ADMOB, 0.0)
        }

        // 缓存优先：检查各平台缓存状态
        val admobHasCache = admobEnabled && admobController.getCachedAdPeek(admobAdUnitId) != null
        val pangleHasCache = pangleEnabled && pangleController.hasCachedAd()
        val toponHasCache = toponEnabled && toponController.getCurrentAd(toponPlacementId) != null
        val anyCached = admobHasCache || pangleHasCache || toponHasCache

        val needLoadPlatforms = mutableListOf<AdPlatform>()
        if (admobEnabled && !admobHasCache) needLoadPlatforms.add(AdPlatform.ADMOB)
        if (pangleEnabled && !pangleHasCache) needLoadPlatforms.add(AdPlatform.PANGLE)
        if (toponEnabled && !toponHasCache) needLoadPlatforms.add(AdPlatform.TOPON)

        AdLogger.d("插页缓存状态 -> AdMob:$admobHasCache, Pangle:$pangleHasCache, TopOn:$toponHasCache, anyCached:$anyCached, 需加载:${needLoadPlatforms.map { it.key }}")

        // 上报无缓存平台
        if (admobEnabled && !admobHasCache) AdEventReporter.reportNoCache(AdType.INTERSTITIAL, AdPlatform.ADMOB, admobAdUnitId)
        if (pangleEnabled && !pangleHasCache) AdEventReporter.reportNoCache(AdType.INTERSTITIAL, AdPlatform.PANGLE, pangleAdUnitId)
        if (toponEnabled && !toponHasCache) AdEventReporter.reportNoCache(AdType.INTERSTITIAL, AdPlatform.TOPON, toponPlacementId)

        if (needLoadPlatforms.isNotEmpty()) {
            if (anyCached) {
                // 有缓存：非缓存平台后台fire-and-forget预加载，不阻塞竞价
                AdLogger.d("插页有缓存平台，非缓存平台后台预加载，秒出竞价结果")
                val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                for (platform in needLoadPlatforms) {
                    bgScope.launch {
                        runCatching {
                            when (platform) {
                                AdPlatform.ADMOB -> admobController.preloadAd(context, admobAdUnitId)
                                AdPlatform.PANGLE -> pangleController.preloadAd(context, pangleAdUnitId)
                                AdPlatform.TOPON -> toponController.preloadAd(context, toponPlacementId)
                            }
                        }
                    }
                }
            } else {
                // 全无缓存：并行加载+7s超时
                kotlinx.coroutines.supervisorScope {
                    val admobDeferred = if (AdPlatform.ADMOB in needLoadPlatforms) async {
                        runCatching { admobController.preloadAd(context, admobAdUnitId) }.getOrNull()
                    } else null
                    val pangleDeferred = if (AdPlatform.PANGLE in needLoadPlatforms) async {
                        runCatching { pangleController.preloadAd(context, pangleAdUnitId) }.getOrNull()
                    } else null
                    val toponDeferred = if (AdPlatform.TOPON in needLoadPlatforms) async {
                        runCatching { toponController.preloadAd(context, toponPlacementId) }.getOrNull()
                    } else null

                    awaitBidPreloadsWithTimeout(
                        timeoutMs = 7000L,
                        timeoutLog = "插页广告竞价加载超时(7s)，取消未完成的加载任务并使用已完成的结果",
                        admobDeferred,
                        pangleDeferred,
                        toponDeferred
                    )
                }
            }
        } else {
            AdLogger.d("插页所有启用平台都有缓存，跳过加载")
        }

        // 计算竞价耗时
        val biddingDuration = System.currentTimeMillis() - startTime

        // 上报加载后仍无缓存的平台为竞价失败
        if (admobEnabled && admobController.getCachedAdPeek(admobAdUnitId) == null) {
            BiddingTracker.reportBidFail(bidId, AdPlatform.ADMOB, "no_cache_after_load")
        }
        if (pangleEnabled && !pangleController.hasCachedAd()) {
            BiddingTracker.reportBidFail(bidId, AdPlatform.PANGLE, "no_cache_after_load")
        }
        if (toponEnabled && toponController.getCurrentAd(toponPlacementId) == null) {
            BiddingTracker.reportBidFail(bidId, AdPlatform.TOPON, "no_cache_after_load")
        }

        // 从缓存获取各平台 eCPM（直接查缓存，不依赖加载结果状态）
        val admobValueUsd = if (admobEnabled) {
            admobController.getCachedAdPeek(admobAdUnitId)?.ad?.let { ad ->
                AdmobStackReflectionUtils.getIvEcpmMicros(ad).toDouble().div(1_000_000.0)
            } ?: 0.0
        } else 0.0

        val pangleValueUsd = if (pangleEnabled) {
            pangleController.getCurrentAd()?.winEcpm?.revenue?.toDoubleOrNull() ?: 0.0
        } else 0.0

        val toponValueUsd = if (toponEnabled) {
            toponController.getCurrentAd(toponPlacementId)?.let { ad ->
                runCatching { ad.checkValidAdCaches().firstOrNull()?.publisherRevenue }.getOrNull() ?: 0.0
            } ?: 0.0
        } else 0.0

        val biddingLog = String.format(
            Locale.US,
            "插页竞价结果 -> AdMob: %.8f 美元%s, Pangle: %.8f 美元%s, TopOn: %.8f 美元%s",
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
        AdLogger.d("============= 插页广告竞价结束 =============")

        return BiddingResult(winner, winnerCpm)
    }
}
