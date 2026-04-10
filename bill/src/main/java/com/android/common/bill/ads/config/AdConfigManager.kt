package com.android.common.bill.ads.config

import android.annotation.SuppressLint
import android.content.Context
import com.android.common.bill.ads.BillCryptoScope
import com.android.common.bill.ads.util.AdmobStackReflectionUtils
import com.google.gson.Gson
import com.android.common.bill.ads.log.AdLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.corekit.core.controller.ChannelUserController
import net.corekit.core.ext.DataStoreStringDelegate
import net.corekit.core.ext.autoDecryptIfNeeded
import net.corekit.core.utils.ConfigRemoteManager
import java.util.concurrent.ConcurrentHashMap

/**
 * 广告配置管理器
 */
@SuppressLint("StaticFieldLeak")
object AdConfigManager {
    private const val CONFIG_FILE_NATURAL = "ad_config_natural.json"
    private const val CONFIG_FILE_PAID = "ad_config_paid.json"

    // ==================== Remote Config Keys ====================
    const val KEY_AD_CONFIG_NATURAL_JSON = "adConfigNaturalJson"
    const val KEY_AD_CONFIG_PAID_JSON = "adConfigPaidJson"


    private var adConfigNaturalJsonFromRemote by DataStoreStringDelegate("adConfigNaturalJsonRemote", "")
    private var adConfigPaidJsonFromRemote by DataStoreStringDelegate("adConfigPaidJsonRemote", "")

    private var naturalConfig: AdConfigData? = null
    private var paidConfig: AdConfigData? = null
    private var context: Context? = null

    // 总控配置缓存（按广告类型缓存）- 使用线程安全的 ConcurrentHashMap
    private val totalConfigCache = ConcurrentHashMap<AdType, AdConfig>()

    /**
     * 初始化所有配置
     */
    fun initialize(context: Context) {
        try {
            // 保存Context引用
            this.context = context.applicationContext
            AdmobStackReflectionUtils.initialize()

            // 1. 先使用本地配置进行初始化
            initializeWithLocalConfig(context)

            // 2. 监听用户渠道变化
            setupChannelListener()

            // 3. 异步获取远程配置
            fetchRemoteConfig()

            AdLogger.d("广告配置初始化成功，当前渠道: ${ChannelUserController.getCurrentChannel()}")
        } catch (e: Exception) {
            AdLogger.e("广告配置初始化失败", e)
        }
    }

    /**
     * 异步获取远程配置
     */
    private fun fetchRemoteConfig() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AdLogger.d("开始获取远程广告配置")

                // 获取 Natural 配置
                val remoteNaturalJson = ConfigRemoteManager.getString(KEY_AD_CONFIG_NATURAL_JSON, "")
                if (!remoteNaturalJson.isNullOrEmpty()) {
                    naturalConfig = parseConfig(remoteNaturalJson)
                    adConfigNaturalJsonFromRemote = remoteNaturalJson
                    // 清除总控缓存，确保使用最新配置
                    totalConfigCache.clear()
                    AdLogger.d("远程 Natural 广告配置更新成功")
                }

                // 获取 Paid 配置
                val remotePaidJson = ConfigRemoteManager.getString(KEY_AD_CONFIG_PAID_JSON, "")
                if (!remotePaidJson.isNullOrEmpty()) {
                    paidConfig = parseConfig(remotePaidJson)
                    adConfigPaidJsonFromRemote = remotePaidJson
                    // 清除总控缓存，确保使用最新配置
                    totalConfigCache.clear()
                    AdLogger.d("远程 Paid 广告配置更新成功")
                }
            } catch (e: Exception) {
                AdLogger.e("获取远程广告配置异常", e)
            }
        }
    }

    /**
     * 设置渠道监听器
     */
    private fun setupChannelListener() {
        ChannelUserController.addChannelChangeListener(object : ChannelUserController.ChannelChangeListener {
            override fun onChannelChanged(oldChannel: ChannelUserController.UserChannelType, newChannel: ChannelUserController.UserChannelType) {
                AdLogger.d("广告渠道变化: ${oldChannel.value} -> ${newChannel.value}")
                // 清除总控缓存，确保使用新渠道的配置限制
                totalConfigCache.clear()
                AdLogger.d("渠道切换，已清除总控配置缓存")
            }
        })
    }

    /**
     * 使用本地配置初始化
     */
    private fun initializeWithLocalConfig(context: Context) {
        // Natural 配置
        val naturalJson = adConfigNaturalJsonFromRemote.orEmpty().takeIf { it.isNotEmpty() }
            ?: context.assets.open(CONFIG_FILE_NATURAL).bufferedReader().use { it.readText() }
        naturalConfig = parseConfig(naturalJson.autoDecryptIfNeeded(BillCryptoScope.STATIC_ASSET_PACKAGE_NAME))

        // Paid 配置
        val paidJson = adConfigPaidJsonFromRemote.orEmpty().takeIf { it.isNotEmpty() }
            ?: context.assets.open(CONFIG_FILE_PAID).bufferedReader().use { it.readText() }
        paidConfig = parseConfig(paidJson.autoDecryptIfNeeded(BillCryptoScope.STATIC_ASSET_PACKAGE_NAME))

        AdLogger.d("本地广告配置初始化完成")
    }

    /**
     * 解析配置 JSON
     */
    private fun parseConfig(jsonString: String): AdConfigData {
        return try {
            Gson().fromJson(jsonString, AdConfigData::class.java)
        } catch (e: Exception) {
            AdLogger.e("解析配置文件失败", e)
            AdConfigData()
        }
    }

    /**
     * 获取当前渠道的配置
     */
    private fun getCurrentConfig(): AdConfigData {
        return try {
            when (ChannelUserController.getCurrentChannel()) {
                ChannelUserController.UserChannelType.NATURAL -> naturalConfig ?: AdConfigData()
                ChannelUserController.UserChannelType.PAID -> paidConfig ?: AdConfigData()
            }
        } catch (e: Exception) {
            AdLogger.e("获取用户渠道失败，使用默认配置", e)
            naturalConfig ?: AdConfigData()
        }
    }

    /**
     * 获取插页广告配置
     * @param platform 聚合平台（必传，用于独立计数）
     */
    fun getInterstitialConfig(platform: AdPlatform): AdConfig {
        return createInterstitialConfig(platform)
    }

    /**
     * 获取原生广告配置
     * @param platform 聚合平台（必传，用于独立计数）
     */
    fun getNativeConfig(platform: AdPlatform): AdConfig {
        return createNativeConfig(platform)
    }

    /**
     * 获取Banner广告配置
     * @param platform 聚合平台（必传，用于独立计数）
     */
    fun getBannerConfig(platform: AdPlatform): AdConfig {
        return createBannerConfig(platform)
    }

    /**
     * 获取开屏广告配置
     * @param platform 聚合平台（必传，用于独立计数）
     */
    fun getAppOpenConfig(platform: AdPlatform): AdConfig {
        return createAppOpenConfig(platform)
    }

    /**
     * 获取全屏原生广告配置
     * @param platform 聚合平台（必传，用于独立计数）
     */
    fun getFullscreenNativeConfig(platform: AdPlatform): AdConfig {
        return createFullscreenNativeConfig(platform)
    }

    /**
     * 获取激励广告配置
     * @param platform 聚合平台（必传，用于独立计数）
     */
    fun getRewardedConfig(platform: AdPlatform): AdConfig {
        return createRewardedConfig(platform)
    }

    /**
     * 获取插屏结束后的全屏Native广告数量
     */
    fun getFullscreenNativeAfterInterstitialCount(): Int {
        return getCurrentConfig().adStrategies.fullscreenNativeAfterInterstitial
    }

    /**
     * 获取展示开屏当插页展示的配置值
     * @return 配置值，默认为2
     */
    fun getAppOpenAfterInterstitial(): Int {
        return getCurrentConfig().adStrategies.appOpenAfterInterstitial
    }

    /**
     * 获取广告策略配置
     */
    fun getAdStrategies(): AdConfigData.AdStrategies {
        return getCurrentConfig().adStrategies
    }

    // ==================== Total 总控配置获取 ====================

    /**
     * 根据广告类型获取总控配置
     * 使用缓存避免重复创建实例，远程配置更新时会清除缓存
     * 总控仅限制日展示/点击次数，不限制时间间隔
     * 总控不分平台
     */
    fun getTotalConfig(adType: AdType): AdConfig? {
        // 优先从缓存获取
        totalConfigCache[adType]?.let { return it }

        val ctx = context ?: return null
        val config = getCurrentConfig()
        val adTypeConfig = when (adType) {
            AdType.INTERSTITIAL -> config.interstitial
            AdType.NATIVE -> config.native
            AdType.BANNER -> config.banner
            AdType.APP_OPEN -> config.appOpen
            AdType.FULL_SCREEN_NATIVE -> config.native
            AdType.REWARDED -> config.rewarded
        }
        val totalLimits = adTypeConfig.totalFrequencyLimits
        val totalConfig = AdConfig.Builder(ctx, adType, AdPlatform.ADMOB)
            .setSpName("ad_config_Total_${adType.configKey}")
            .setMaxDailyShow(totalLimits.maxDailyShow)
            .setMaxDailyClick(totalLimits.maxDailyClick)
            .build()

        // 存入缓存
        totalConfigCache[adType] = totalConfig
        return totalConfig
    }

    // ==================== 统一记录方法（自动同步到总控）====================

    /**
     * 记录展示（同时记录到平台配置和总控配置）
     */
    fun recordShow(adType: AdType, platform: AdPlatform) {
        // 记录到平台配置
        getPlatformConfig(adType, platform)?.recordShow()
        // 记录到总控配置
        getTotalConfig(adType)?.recordShow()
        AdLogger.d("记录展示: ${adType.name} - ${platform.key} (含总控)")
    }

    /**
     * 记录点击（同时记录到平台配置和总控配置）
     */
    fun recordClick(adType: AdType, platform: AdPlatform) {
        // 记录到平台配置
        getPlatformConfig(adType, platform)?.recordClick()
        // 记录到总控配置
        getTotalConfig(adType)?.recordClick()
        AdLogger.d("记录点击: ${adType.name} - ${platform.key} (含总控)")
    }

    /**
     * 获取指定广告类型和平台的配置
     */
    fun getPlatformConfig(adType: AdType, platform: AdPlatform): AdConfig? {
        return when (adType) {
            AdType.INTERSTITIAL -> getInterstitialConfig(platform)
            AdType.NATIVE -> getNativeConfig(platform)
            AdType.BANNER -> getBannerConfig(platform)
            AdType.APP_OPEN -> getAppOpenConfig(platform)
            AdType.FULL_SCREEN_NATIVE -> getFullscreenNativeConfig(platform)
            AdType.REWARDED -> getRewardedConfig(platform)
        }
    }

    // ==================== 平台启用状态查询 ====================

    /**
     * 获取指定广告类型的平台限制配置（聚合频限）
     */
    private fun getPlatformLimits(adTypeConfig: AdConfigData.AdTypeConfig, platform: AdPlatform): AdConfigData.LimitValues {
        return when (platform) {
            AdPlatform.ADMOB -> adTypeConfig.biddingFrequencyLimits.admob
            AdPlatform.PANGLE -> adTypeConfig.biddingFrequencyLimits.pangle
            AdPlatform.TOPON -> adTypeConfig.biddingFrequencyLimits.topon
        }
    }

    /**
     * 检查平台是否启用
     */
    fun isPlatformEnabled(adType: AdType, platform: AdPlatform): Boolean {
        val config = getCurrentConfig()
        val adTypeConfig = when (adType) {
            AdType.APP_OPEN -> config.appOpen
            AdType.INTERSTITIAL -> config.interstitial
            AdType.NATIVE, AdType.FULL_SCREEN_NATIVE -> config.native
            AdType.REWARDED -> config.rewarded
            AdType.BANNER -> config.banner
        }
        return when (platform) {
            AdPlatform.ADMOB -> adTypeConfig.biddingPlatforms.admob
            AdPlatform.PANGLE -> adTypeConfig.biddingPlatforms.pangle
            AdPlatform.TOPON -> adTypeConfig.biddingPlatforms.topon
        }
    }

    // ==================== 创建广告配置 ====================

    /**
     * 创建插页广告配置（根据当前渠道和平台）
     */
    private fun createInterstitialConfig(platform: AdPlatform): AdConfig {
        val ctx = checkNotNull(context) { "Context 未初始化" }
        val config = getCurrentConfig()
        val limits = getPlatformLimits(config.interstitial, platform)

        return AdConfig.Builder(ctx, AdType.INTERSTITIAL, platform)
            .setMaxDailyShow(limits.maxDailyShow)
            .setMaxDailyClick(limits.maxDailyClick)
            .setMinInterval(limits.minInterval.toLong())
            .build()
    }

    /**
     * 创建原生广告配置（根据当前渠道和平台）
     */
    private fun createNativeConfig(platform: AdPlatform): AdConfig {
        val ctx = checkNotNull(context) { "Context 未初始化" }
        val config = getCurrentConfig()
        val limits = getPlatformLimits(config.native, platform)

        return AdConfig.Builder(ctx, AdType.NATIVE, platform)
            .setMaxDailyShow(limits.maxDailyShow)
            .setMaxDailyClick(limits.maxDailyClick)
            .setMinInterval(limits.minInterval.toLong())
            .build()
    }

    /**
     * 创建Banner广告配置（根据当前渠道和平台）
     */
    private fun createBannerConfig(platform: AdPlatform): AdConfig {
        val ctx = checkNotNull(context) { "Context 未初始化" }
        val config = getCurrentConfig()
        val limits = getPlatformLimits(config.banner, platform)

        return AdConfig.Builder(ctx, AdType.BANNER, platform)
            .setMaxDailyShow(limits.maxDailyShow)
            .setMaxDailyClick(limits.maxDailyClick)
            .setMinInterval(limits.minInterval.toLong())
            .build()
    }

    /**
     * 创建开屏广告配置（根据当前渠道和平台）
     */
    private fun createAppOpenConfig(platform: AdPlatform): AdConfig {
        val ctx = checkNotNull(context) { "Context 未初始化" }
        val config = getCurrentConfig()
        val limits = getPlatformLimits(config.appOpen, platform)

        return AdConfig.Builder(ctx, AdType.APP_OPEN, platform)
            .setMaxDailyShow(limits.maxDailyShow)
            .setMaxDailyClick(limits.maxDailyClick)
            .setMinInterval(limits.minInterval.toLong())
            .build()
    }

    /**
     * 创建全屏原生广告配置（根据当前渠道和平台）
     */
    private fun createFullscreenNativeConfig(platform: AdPlatform): AdConfig {
        val ctx = checkNotNull(context) { "Context 未初始化" }
        val config = getCurrentConfig()
        val limits = getPlatformLimits(config.native, platform)

        return AdConfig.Builder(ctx, AdType.FULL_SCREEN_NATIVE, platform)
            .setMaxDailyShow(limits.maxDailyShow)
            .setMaxDailyClick(limits.maxDailyClick)
            .setMinInterval(limits.minInterval.toLong())
            .build()
    }

    /**
     * 创建激励广告配置（根据当前渠道和平台）
     */
    private fun createRewardedConfig(platform: AdPlatform): AdConfig {
        val ctx = checkNotNull(context) { "Context 未初始化" }
        val config = getCurrentConfig()
        val limits = getPlatformLimits(config.rewarded, platform)

        return AdConfig.Builder(ctx, AdType.REWARDED, platform)
            .setMaxDailyShow(limits.maxDailyShow)
            .setMaxDailyClick(limits.maxDailyClick)
            .setMinInterval(limits.minInterval.toLong())
            .build()
    }
}
