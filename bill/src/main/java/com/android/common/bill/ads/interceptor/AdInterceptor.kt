package com.android.common.bill.ads.interceptor

import com.android.common.bill.ads.log.AdLogger
import net.corekit.core.ext.DataStoreBoolDelegate

/**
 * 全局广告开关控制器
 * 用于控制全局广告的开启和关闭（由点击保护等模块调用）
 */
object GlobalAdSwitchInterceptor {
    private const val TAG = "GlobalAdSwitch"

    private var _isGlobalAdEnabled by DataStoreBoolDelegate(
        "GlobalAdSwitchInterceptor_isGlobalAdEnabledDefault",
        true
    )

    /**
     * 开启全局广告
     */
    fun enableGlobalAd() {
        _isGlobalAdEnabled = true
        AdLogger.d("[$TAG] 全局广告已开启")
    }

    /**
     * 关闭全局广告
     */
    fun disableGlobalAd() {
        _isGlobalAdEnabled = false
        AdLogger.d("[$TAG] 全局广告已关闭")
    }

    /**
     * 获取当前全局广告状态
     */
    fun isGlobalAdEnabled(): Boolean = _isGlobalAdEnabled

    /**
     * 切换全局广告状态
     */
    fun toggleGlobalAd() {
        _isGlobalAdEnabled = !_isGlobalAdEnabled
        AdLogger.d("[$TAG] 全局广告状态已切换为: ${if (_isGlobalAdEnabled) "开启" else "关闭"}")
    }
}
