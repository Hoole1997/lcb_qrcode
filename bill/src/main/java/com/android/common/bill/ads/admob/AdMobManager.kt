package com.android.common.bill.ads.admob

import android.content.Context
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.log.AdLogger
import com.google.android.gms.ads.MobileAds
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * AdMob SDK 管理器
 * 负责SDK初始化和全局配置
 */
object AdMobManager {

    private const val TAG = "AdMobManager"
    private var isInitialized = true

    /**
     * 初始化 AdMob SDK（阻塞当前线程直到初始化完成）
     */
    fun initialize(context: Context): AdResult<Unit> {
        if (isInitialized) {
            return AdResult.Success(Unit)
        }

        return try {
            val latch = CountDownLatch(1)
            val resultRef = AtomicReference<AdResult<Unit>>(
                AdResult.Failure(
                    AdException(
                        code = AdException.ERROR_INTERNAL,
                        message = "AdMob 初始化未完成"
                    )
                )
            )

            MobileAds.initialize(context) { initializationStatus ->
                try {
                    val statusMap = initializationStatus.adapterStatusMap
                    AdLogger.d("AdMob SDK初始化完成")

                    // 输出各个适配器的状态
                    for ((className, status) in statusMap) {
                        AdLogger.d("AdMob 适配器: $className, 状态: ${status.initializationState}, 描述: ${status.description}")
                    }

                    isInitialized = true
                    val result = AdResult.Success(Unit)
                    resultRef.set(result)

                } catch (e: Exception) {
                    AdLogger.e("AdMob SDK初始化过程中发生异常", e)
                    val result = AdResult.Failure(
                        AdException(
                            code = AdException.Companion.ERROR_INTERNAL,
                            message = "SDK初始化异常: ${e.message}",
                            cause = e
                        )
                    )
                    resultRef.set(result)
                }
                latch.countDown()
            }

            latch.await()

            resultRef.get()
        } catch (e: Exception) {
            AdLogger.e("AdMob SDK初始化过程中发生异常", e)
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
     * 获取所有广告控制器的快捷访问器
     */
    object Controllers {
        val interstitial: AdmobInterstitialAdController
            get() = AdmobInterstitialAdController.getInstance()

        val appOpen: AdmobAppOpenAdController
            get() = AdmobAppOpenAdController.getInstance()

        val native: AdmobNativeAdController
            get() = AdmobNativeAdController.getInstance()

        val fullScreenNative: AdmobFullScreenNativeAdController
            get() = AdmobFullScreenNativeAdController.getInstance()

        val banner: AdmobBannerAdController
            get() = AdmobBannerAdController.getInstance()
    }

    /**
     * 清理所有控制器资源
     */
    fun destroyAll() {
//        Controllers.interstitial.destroy()
        Controllers.appOpen.destroy()
        Controllers.native.destroy()
        Controllers.fullScreenNative.destroy()
        Controllers.banner.destroy()
        AdLogger.d("所有广告控制器已清理")
    }
}
