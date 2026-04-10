package com.android.common.bill.ads.bidding

import android.content.Context
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.log.AdLogger
import net.corekit.core.ext.DataStoreStringDelegate

/**
 * 广告聚合源控制器
 * 用于管理当前聚合源选择（同时应用于插页和激励广告）
 * 支持持久化存储，默认为空表示使用竞价逻辑
 */
object AdSourceController {

    /**
     * 聚合源类型
     */
    enum class AdSource {
        BIDDING,  // 竞价（默认）
        ADMOB,    // AdMob
        PANGLE,   // Pangle
        TOPON     // TopOn
    }

    /**
     * 固定源检查结果
     */
    sealed class FixedSourceCheckResult {
        /** 使用竞价逻辑 */
        object UseBidding : FixedSourceCheckResult()
        /** 使用固定源 */
        data class UseFixedSource(val winner: BiddingWinner) : FixedSourceCheckResult()
    }

    /**
     * 检查固定聚合源是否可用（包含初始化和频控检查）
     * 
     * @param adType 广告类型
     * @return FixedSourceCheckResult
     *   - UseBidding: 需要使用竞价逻辑（未设置固定源，或固定源不可用）
     *   - UseFixedSource: 可以使用固定源
     */
    fun checkFixedSource(adType: AdType): FixedSourceCheckResult {
        val source = getCurrentSource()
        
        // 未设置固定源，使用竞价
        if (source == AdSource.BIDDING) {
            return FixedSourceCheckResult.UseBidding
        }

        // 转换为平台和 Winner
        val (platform, winner) = when (source) {
            AdSource.ADMOB -> AdPlatform.ADMOB to BiddingWinner.ADMOB
            AdSource.PANGLE -> AdPlatform.PANGLE to BiddingWinner.PANGLE
            AdSource.TOPON -> AdPlatform.TOPON to BiddingWinner.TOPON
            AdSource.BIDDING -> return FixedSourceCheckResult.UseBidding
        }

        // 检查平台是否启用（包含初始化检查）
        if (!BiddingPlatformController.isPlatformEnabled(adType, platform)) {
            AdLogger.d("固定源 ${source.name} 未启用或未初始化，回退到竞价")
            return FixedSourceCheckResult.UseBidding
        }

        // 检查频控限制
        if (!BiddingExclusionController.canPlatformBid(adType, platform)) {
            AdLogger.d("固定源 ${source.name} 被频控限制，回退到竞价")
            return FixedSourceCheckResult.UseBidding
        }

        AdLogger.d("使用固定源: ${source.name}")
        return FixedSourceCheckResult.UseFixedSource(winner)
    }

    // 当前聚合源（持久化）
    private var currentSource by DataStoreStringDelegate("ad_source_current", null)

    /**
     * 获取当前聚合源
     * @return AdSource，如果为空则返回 BIDDING（竞价）
     */
    fun getCurrentSource(): AdSource {
        return currentSource?.let {
            try {
                AdSource.valueOf(it)
            } catch (e: Exception) {
                AdSource.BIDDING
            }
        } ?: AdSource.BIDDING
    }

    /**
     * 设置当前聚合源
     * @param source AdSource
     */
    fun setCurrentSource(source: AdSource) {
        currentSource = source.name
    }

    /**
     * 获取聚合源显示名称
     */
    fun getSourceDisplayName(source: AdSource): String {
        return when (source) {
            AdSource.BIDDING -> "竞价"
            AdSource.ADMOB -> "ADMOB"
            AdSource.PANGLE -> "PANGLE"
            AdSource.TOPON -> "TOPON"
        }
    }

    /**
     * 获取所有可用的聚合源
     */
    fun getAllSources(): List<AdSource> {
        return listOf(
            AdSource.BIDDING,
            AdSource.ADMOB,
            AdSource.PANGLE,
            AdSource.TOPON
        )
    }

    /**
     * 显示聚合源选择弹窗
     * @param context 上下文
     * @param onSourceChanged 聚合源改变后的回调，用于刷新UI
     */
    fun showAdSourceSelection(context: Context, onSourceChanged: () -> Unit = {}) {
        val currentSource = getCurrentSource()
        AdSourceSelectionBottomSheet.show(context, currentSource) { selectedSource ->
            setCurrentSource(selectedSource)
            onSourceChanged()
        }
    }
}
