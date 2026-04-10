package com.android.common.bill.ads.pangle

import android.content.Context
import com.bytedance.sdk.openadsdk.api.PAGMInitSuccessModel
import com.bytedance.sdk.openadsdk.api.init.PAGMConfig
import com.bytedance.sdk.openadsdk.api.init.PAGMSdk
import com.bytedance.sdk.openadsdk.api.init.PAGSdk
import com.bytedance.sdk.openadsdk.api.model.PAGErrorModel
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.log.AdLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * Pangle SDK 管理器
 * 负责SDK初始化和全局配置
 * 参考文档: https://www.pangleglobal.com/integration/android-initialize-pangle-sdk
 */
object PangleManager {
    
    private const val TAG = "PangleManager"
    private var isInitialized = true
    
    /**
     * 初始化 Pangle SDK（阻塞当前线程直到初始化完成）
     * @param context 上下文
     * @param appId Pangle App ID
     * @param appIconId 应用图标资源ID（用于App Open Ads）
     */
    fun initialize(context: Context, appId: String, appIconId: Int? = null): AdResult<Unit> {
        if (isInitialized || PAGSdk.isInitSuccess()) {
            isInitialized = true
            return AdResult.Success(Unit)
        }

        return try {
            val configBuilder = PAGMConfig.Builder()
                .appId(appId)
                .debugLog(AdLogger.isLogEnabled()) // 测试阶段打开，可以通过日志排查问题，上线时关闭该开关
                .supportMultiProcess(false) // 是否支持多进程

            appIconId?.let { configBuilder.appIcon(it) }

            val config = configBuilder.build()
            val latch = CountDownLatch(1)
            val resultRef = AtomicReference<AdResult<Unit>>(
                AdResult.Failure(
                    AdException(
                        code = AdException.ERROR_INTERNAL,
                        message = "Pangle 初始化未完成"
                    )
                )
            )

            PAGMSdk.init(context, config, object : PAGMSdk.PAGMInitCallback {
                override fun success(pagmInitSuccessModel: PAGMInitSuccessModel) {
                    AdLogger.d("Pangle SDK初始化完成")
                    isInitialized = true
                    val result = AdResult.Success(Unit)
                    resultRef.set(result)
                    latch.countDown()
                }

                override fun fail(pagErrorModel: PAGErrorModel) {
                    val code = pagErrorModel.errorCode
                    val msg = pagErrorModel.errorMessage
                    AdLogger.e("Pangle SDK初始化失败，错误码: %d, 错误信息: %s", code, msg ?: "")
                    val result = AdResult.Failure(
                        AdException(
                            code = AdException.ERROR_INTERNAL,
                            message = "SDK初始化失败: $msg (code: $code)"
                        )
                    )
                    resultRef.set(result)
                    latch.countDown()
                }
            })

            latch.await()

            resultRef.get()
        } catch (e: Exception) {
            AdLogger.e("Pangle SDK初始化过程中发生异常", e)
            val result = AdResult.Failure(
                AdException(
                    code = AdException.ERROR_INTERNAL,
                    message = "SDK初始化异常: ${e.message}",
                    cause = e
                )
            )
            result
        }
    }

    /**
     * 检查SDK是否已初始化
     */
    fun isInitialized(): Boolean {
        return isInitialized || PAGSdk.isInitSuccess()
    }
    
    /**
     * 获取所有广告控制器的快捷访问器
     */
    object Controllers {
        val appOpen: PangleAppOpenAdController
            get() = PangleAppOpenAdController.getInstance()
            
        val interstitial: PangleInterstitialAdController
            get() = PangleInterstitialAdController.getInstance()
            
        val banner: PangleBannerAdController
            get() = PangleBannerAdController.getInstance()
            
        val native: PangleNativeAdController
            get() = PangleNativeAdController.getInstance()
            
        val fullScreenNative: PangleFullScreenNativeAdController
            get() = PangleFullScreenNativeAdController.getInstance()
            
        val rewarded: PangleRewardedAdController
            get() = PangleRewardedAdController.getInstance()
    }
    
    /**
     * 清理所有控制器资源
     */
    fun destroyAll() {
        Controllers.appOpen.destroy()
        Controllers.interstitial.destroy()
        Controllers.banner.destroy()
        Controllers.native.destroy()
        Controllers.fullScreenNative.destroy()
        Controllers.rewarded.destroy()
        AdLogger.d("所有Pangle广告控制器已清理")
    }
}
