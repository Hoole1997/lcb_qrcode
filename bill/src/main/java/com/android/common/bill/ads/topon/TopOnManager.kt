package com.android.common.bill.ads.topon

import android.content.Context
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.log.AdLogger
import com.thinkup.core.api.TUNetworkConfig
import com.thinkup.core.api.TUSDK
import com.thinkup.core.api.TUSDKInitListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * TopOn SDK 管理器
 * 负责SDK初始化和全局配置
 */
object TopOnManager {

    private const val TAG = "TopOnManager"
    @Volatile
    private var isInitialized = true

    /**
     * 初始化 TopOn SDK（阻塞当前线程直到初始化完成）
     * @param context 上下文
     * @param appId TopOn App ID
     * @param appKey TopOn App Key
     */
    fun initialize(context: Context, appId: String, appKey: String): AdResult<Unit> {
        if (isInitialized) {
            return AdResult.Success(Unit)
        }

        val applicationContext = context.applicationContext

        return try {
            TUSDK.setNetworkLogDebug(AdLogger.isLogEnabled())

            val tuNetworkConfig = TUNetworkConfig.Builder()
                .withInitConfigList(emptyList())
                .build()

            val latch = CountDownLatch(1)
            val resultRef = AtomicReference<AdResult<Unit>>(
                AdResult.Failure(
                    AdException(
                        code = AdException.ERROR_INTERNAL,
                        message = "TopOn 初始化未完成"
                    )
                )
            )

            TUSDK.init(
                applicationContext,
                appId,
                appKey,
                tuNetworkConfig,
                object : TUSDKInitListener {
                    override fun onSuccess() {
                        AdLogger.d("TopOn SDK初始化完成，版本: ${runCatching { TUSDK.getSDKVersionName() }.getOrElse { "unknown" }}")
                        isInitialized = true
                        val result = AdResult.Success(Unit)
                        resultRef.set(result)
                        latch.countDown()
                    }

                    override fun onFail(errorMsg: String) {
                        val message = errorMsg.ifBlank { "未知错误" }
                        AdLogger.e("TopOn SDK初始化失败: %s", message)
                        val result = AdResult.Failure(
                            AdException(
                                code = AdException.ERROR_INTERNAL,
                                message = "SDK初始化失败: $message"
                            )
                        )
                        resultRef.set(result)
                        latch.countDown()
                    }
                }
            )

            latch.await()

            resultRef.get()
        } catch (e: Exception) {
            AdLogger.e("TopOn SDK初始化过程中发生异常", e)
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
        return isInitialized
    }

    /**
     * 获取TopOn广告控制器
     */
    object Controllers {
        val interstitial: TopOnInterstitialAdController
            get() = TopOnInterstitialAdController.getInstance()
            
        val rewarded: TopOnRewardedAdController
            get() = TopOnRewardedAdController.getInstance()
            
        val native: TopOnNativeAdController
            get() = TopOnNativeAdController.getInstance()
            
        val splash: TopOnSplashAdController
            get() = TopOnSplashAdController.getInstance()
            
        val fullScreenNative: TopOnFullScreenNativeAdController
            get() = TopOnFullScreenNativeAdController.getInstance()
            
        val banner: TopOnBannerAdController
            get() = TopOnBannerAdController.getInstance()
    }

    /**
     * 清理所有控制器资源
     */
    fun destroyAll() {
        Controllers.interstitial.destroy()
        Controllers.rewarded.destroy()
        Controllers.native.destroy()
        Controllers.splash.destroy()
        Controllers.fullScreenNative.destroy()
        Controllers.banner.destroy()
        AdLogger.d("TopOn广告控制器已清理")
    }
}
