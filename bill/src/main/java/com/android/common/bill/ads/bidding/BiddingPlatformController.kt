package com.android.common.bill.ads.bidding

import com.android.common.bill.ads.admob.AdMobManager
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.pangle.PangleManager
import com.android.common.bill.ads.topon.TopOnManager

/**
 * 比价平台控制器
 * 用于控制各广告类型参与比价的平台
 * 配置从 AdConfigManager 读取（支持本地 assets 和远程配置）
 * 同时检查平台是否已初始化
 */
object BiddingPlatformController {

    /**
     * 检查指定广告类型的平台是否启用（包含配置开关和初始化状态）
     */
    fun isPlatformEnabled(adType: AdType, platform: AdPlatform): Boolean {
        // 1. 检查配置是否启用
        val configEnabled = AdConfigManager.isPlatformEnabled(adType, platform)
        if (!configEnabled) return false
        
        // 2. 检查平台是否已初始化
        val initialized = isPlatformInitialized(platform)
        if (!initialized) {
            AdLogger.d("${platform.key} 配置已启用但尚未初始化，跳过参与竞价")
        }
        return initialized
    }

    /**
     * 检查平台是否已初始化
     */
    private fun isPlatformInitialized(platform: AdPlatform): Boolean {
        return when (platform) {
            AdPlatform.ADMOB -> AdMobManager.isInitialized()
            AdPlatform.PANGLE -> PangleManager.isInitialized()
            AdPlatform.TOPON -> TopOnManager.isInitialized()
        }
    }

    /**
     * 获取指定广告类型启用的平台列表（已初始化的）
     */
    fun getEnabledPlatforms(adType: AdType): List<AdPlatform> {
        return AdPlatform.entries.filter { isPlatformEnabled(adType, it) }
    }

    // ==================== 便捷方法 ====================

    fun isAdmobEnabled(adType: AdType): Boolean = isPlatformEnabled(adType, AdPlatform.ADMOB)
    fun isPangleEnabled(adType: AdType): Boolean = isPlatformEnabled(adType, AdPlatform.PANGLE)
    fun isToponEnabled(adType: AdType): Boolean = isPlatformEnabled(adType, AdPlatform.TOPON)
}
