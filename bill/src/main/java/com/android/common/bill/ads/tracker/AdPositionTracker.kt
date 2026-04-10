package com.android.common.bill.ads.tracker

import com.android.common.bill.ads.config.AdType
import net.corekit.core.ext.DataStoreIntDelegate

/**
 * 广告位置埋点控制器
 * 统一处理各类广告的ad_position埋点
 * 内部使用 AdEventReporter 统一上报
 */
object AdPositionTracker {
    
    // 插页广告累积触发统计（持久化）
    private var interstitialShowTriggerCount by DataStoreIntDelegate("ad_position_interstitial_show_triggers", 0)
    
    // 原生广告累积触发统计（持久化）
    private var nativeShowTriggerCount by DataStoreIntDelegate("ad_position_native_show_triggers", 0)
    
    // 开屏广告累积触发统计（持久化）
    private var splashShowTriggerCount by DataStoreIntDelegate("ad_position_splash_show_triggers", 0)
    
    // 全屏原生广告累积触发统计（持久化）
    private var fullNativeShowTriggerCount by DataStoreIntDelegate("ad_position_full_native_show_triggers", 0)
    
    // Banner广告累积触发统计（持久化）
    private var bannerShowTriggerCount by DataStoreIntDelegate("ad_position_banner_show_triggers", 0)
    
    // 激励广告累积触发统计（持久化）
    private var rewardedShowTriggerCount by DataStoreIntDelegate("ad_position_rewarded_show_triggers", 0)
    
    /**
     * 上报插页广告位置埋点
     * @return 生成的 session_id
     */
    fun trackInterstitialAdPosition(): String {
        interstitialShowTriggerCount++
        return AdEventReporter.reportPosition(AdType.INTERSTITIAL, interstitialShowTriggerCount)
    }
    
    /**
     * 上报原生广告位置埋点
     * @return 生成的 session_id
     */
    fun trackNativeAdPosition(): String {
        nativeShowTriggerCount++
        return AdEventReporter.reportPosition(AdType.NATIVE, nativeShowTriggerCount)
    }
    
    /**
     * 上报开屏广告位置埋点
     * @return 生成的 session_id
     */
    fun trackSplashAdPosition(): String {
        splashShowTriggerCount++
        return AdEventReporter.reportPosition(AdType.APP_OPEN, splashShowTriggerCount)
    }
    
    /**
     * 上报全屏原生广告位置埋点
     * @return 生成的 session_id
     */
    fun trackFullNativeAdPosition(): String {
        fullNativeShowTriggerCount++
        return AdEventReporter.reportPosition(AdType.FULL_SCREEN_NATIVE, fullNativeShowTriggerCount)
    }
    
    /**
     * 上报Banner广告位置埋点
     * @return 生成的 session_id
     */
    fun trackBannerAdPosition(): String {
        bannerShowTriggerCount++
        return AdEventReporter.reportPosition(AdType.BANNER, bannerShowTriggerCount)
    }
    
    /**
     * 上报激励广告位置埋点
     * @return 生成的 session_id
     */
    fun trackRewardedAdPosition(): String {
        rewardedShowTriggerCount++
        return AdEventReporter.reportPosition(AdType.REWARDED, rewardedShowTriggerCount)
    }
}
