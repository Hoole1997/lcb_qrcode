package com.android.common.bill.ads.protection

import com.google.gson.annotations.SerializedName

/**
 * 广告点击保护配置数据类（包含渠道配置）
 */
data class AdClickProtectionConfigData(
    @SerializedName("natural")
    val natural: AdClickProtectionConfig = AdClickProtectionConfig(),
    @SerializedName("paid")
    val paid: AdClickProtectionConfig = AdClickProtectionConfig()
)

/**
 * 单个渠道的点击保护配置
 */
data class AdClickProtectionConfig(
    @SerializedName("protection_enabled")
    val protectionEnabled: Boolean = true,
    @SerializedName("threshold")
    val threshold: Int = 3
)
