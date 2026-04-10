package com.android.common.bill.ads.config

import com.google.gson.annotations.SerializedName

/**
 * 广告配置数据类（单渠道配置）
 * 分别对应 ad_config_natural.json 和 ad_config_paid.json
 */
data class AdConfigData(
    @SerializedName("app_open")
    val appOpen: AdTypeConfig = AdTypeConfig(),
    @SerializedName("interstitial")
    val interstitial: AdTypeConfig = AdTypeConfig(),
    @SerializedName("native")
    val native: AdTypeConfig = AdTypeConfig(),
    @SerializedName("rewarded")
    val rewarded: AdTypeConfig = AdTypeConfig(),
    @SerializedName("banner")
    val banner: AdTypeConfig = AdTypeConfig(),
    @SerializedName("ad_strategies")
    val adStrategies: AdStrategies = AdStrategies()
) {
    /**
     * 广告类型配置（包含平台开关、总控频限和聚合频限配置）
     */
    data class AdTypeConfig(
        @SerializedName("bidding_platforms")
        val biddingPlatforms: PlatformsEnabled = PlatformsEnabled(),
        @SerializedName("total_frequency_limits")
        val totalFrequencyLimits: LimitValues = LimitValues(),
        @SerializedName("bidding_frequency_limits")
        val biddingFrequencyLimits: BiddingFrequencyLimits = BiddingFrequencyLimits()
    )

    /**
     * 平台启用开关
     */
    data class PlatformsEnabled(
        @SerializedName("admob")
        val admob: Boolean = true,
        @SerializedName("pangle")
        val pangle: Boolean = true,
        @SerializedName("topon")
        val topon: Boolean = true
    )

    /**
     * 聚合频限配置（各平台限制）
     */
    data class BiddingFrequencyLimits(
        @SerializedName("admob")
        val admob: LimitValues = LimitValues(),
        @SerializedName("pangle")
        val pangle: LimitValues = LimitValues(),
        @SerializedName("topon")
        val topon: LimitValues = LimitValues()
    )

    /**
     * 限制值
     */
    data class LimitValues(
        @SerializedName("max_daily_show")
        val maxDailyShow: Int = 20,
        @SerializedName("max_daily_click")
        val maxDailyClick: Int = 10,
        @SerializedName("min_interval")
        val minInterval: Int = 0
    )

    /**
     * 广告策略配置
     */
    data class AdStrategies(
        @SerializedName("fullscreen_native_after_interstitial")
        val fullscreenNativeAfterInterstitial: Int = 3,
        @SerializedName("app_open_after_interstitial")
        val appOpenAfterInterstitial: Int = 2
    )
} 
