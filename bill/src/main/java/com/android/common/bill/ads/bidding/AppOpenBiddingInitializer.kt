package com.android.common.bill.ads.bidding

import android.content.Context
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.admob.AdMobManager
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.pangle.PangleManager
import com.android.common.bill.ads.topon.TopOnManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.getOrElse
import kotlin.runCatching

/**
 * App Open 竞价初始化控制器
 * 负责初始化参与竞价的3个网络（AdMob、Pangle、TopOn）
 */
object AppOpenBiddingInitializer {

    private const val TAG = "AppOpenBiddingInit"

    data class PlatformInitResult(
        val platform: String,
        val result: AdResult<Unit>
    )

    /**
     * 初始化入口
     * 内部并行初始化各平台
     * 单个平台失败不会影响整体返回（整体始终返回 Success）
     *
     * @param context 上下文
     * @param icon 应用图标资源ID
     * @param configSetup 可选的 BillConfig 配置
     * @return 初始化结果
     */
    suspend fun initialize(
        context: Context,
        icon: Int,
        configSetup: (BillConfig.() -> Unit)? = null
    ): AdResult<Unit> {
        configSetup?.invoke(BillConfig)
        AdLogger.d("$TAG 开始并行初始化")

        val platformResults = runCatching {
            initializeAllPlatforms(context, icon)
        }.getOrElse { throwable ->
            AdLogger.e("$TAG 初始化框架异常", throwable)
            emptyList()
        }

        logPlatformFailures(platformResults)

        AdLogger.d("$TAG 初始化流程结束")
        val result = AdResult.Success(Unit)
        return result
    }

    suspend fun initializeAllPlatforms(
        context: Context,
        icon: Int
    ): List<PlatformInitResult> = coroutineScope {
        val applicationContext = context.applicationContext
        val admobDeferred = async(Dispatchers.IO) {
            PlatformInitResult(
                platform = "AdMob",
                result = runManagerInit("AdMob") {
                    AdMobManager.initialize(applicationContext)
                }
            )
        }

        val pangleDeferred = async(Dispatchers.IO) {
            PlatformInitResult(
                platform = "Pangle",
                result = runManagerInit("Pangle") {
                    PangleManager.initialize(
                        context = applicationContext,
                        appId = BillConfig.pangle.applicationId,
                        appIconId = icon
                    )
                }
            )
        }

        val toponDeferred = async(Dispatchers.IO) {
            PlatformInitResult(
                platform = "TopOn",
                result = runManagerInit("TopOn") {
                    TopOnManager.initialize(
                        context = applicationContext,
                        appId = BillConfig.topon.applicationId,
                        appKey = BillConfig.topon.appKey
                    )
                }
            )
        }

        listOf(
            admobDeferred.await(),
            pangleDeferred.await(),
            toponDeferred.await()
        )
    }

    fun runManagerInit(platform: String, block: () -> AdResult<Unit>): AdResult<Unit> {
        return runCatching {
            block()
        }.getOrElse { throwable ->
            AdLogger.e("$TAG $platform 初始化异常", throwable)
            AdResult.Failure(
                AdException(
                    code = AdException.ERROR_INTERNAL,
                    message = "$platform 初始化异常: ${throwable.message}",
                    cause = throwable
                )
            )
        }
    }

    private fun logPlatformFailures(results: List<PlatformInitResult>) {
        val failedPlatforms = mutableListOf<String>()
        results.forEach { platformResult ->
            if (platformResult.result is AdResult.Failure) {
                val error = platformResult.result.error
                failedPlatforms.add(platformResult.platform)
                AdLogger.w("$TAG ${platformResult.platform} 初始化失败: ${error.message}")
            }
        }

        if (failedPlatforms.isEmpty()) {
            AdLogger.d("$TAG 所有平台初始化完成（均成功）")
        } else {
            AdLogger.w("$TAG 初始化完成（忽略失败平台）: ${failedPlatforms.joinToString()}")
        }
    }
}
