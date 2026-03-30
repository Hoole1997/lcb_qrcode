package net.corekit.metrics.data

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.gson.Gson
import net.corekit.metrics.log.MetricsLogger
import net.corekit.metrics.provider.MetricsModuleProvider
import net.corekit.core.report.ReporterData
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Firebase Analytics 数据上报控制器
 * 实现ReporterData接口，用于向Firebase Analytics上报数据
 */
class FirebaseReporter : ReporterData {

    private val gson = Gson()

    /**
     * 获取Firebase Analytics实例，如果为空则异步等待
     * @return Firebase Analytics实例，可能为null
     */
    private fun retrieveFirebaseAnalytics(): FirebaseAnalytics? {
        return try {
            val context = MetricsModuleProvider.Companion.getApplicationContext()
            if (context != null) {
                FirebaseAnalytics.getInstance(context)
            } else {
                MetricsLogger.w("无法获取应用上下文，Firebase Analytics获取失败")
                null
            }
        } catch (e: Exception) {
            MetricsLogger.e("获取Firebase Analytics实例失败", e)
            null
        }
    }

    /**
     * 异步等待获取Firebase Analytics实例
     * @return Firebase Analytics实例
     */
    private suspend fun waitForAnalytics(): FirebaseAnalytics? {
        var attempts = 0
        val maxAttempts = 10 // 最多尝试10次
        val delayMs = 100L // 每次等待100ms

        while (attempts < maxAttempts) {
            val analytics = retrieveFirebaseAnalytics()
            if (analytics != null) {
                return analytics
            }

            MetricsLogger.d("Firebase Analytics未就绪，等待中... (${attempts + 1}/$maxAttempts)")
            delay(delayMs)
            attempts++
        }

        MetricsLogger.e("等待Firebase Analytics超时，无法获取实例")
        return null
    }

    /**
     * 将Map转换为Bundle
     * @param map 要转换的Map
     * @return Bundle
     */
    private fun mapToBundle(map: Map<String, Any>): Bundle {
        val bundle = Bundle()
        try {
            map.forEach { (key, value) ->
                when (value) {
                    is String -> bundle.putString(key, value)
                    is Int -> bundle.putInt(key, value)
                    is Long -> bundle.putLong(key, value)
                    is Double -> bundle.putDouble(key, value)
                    is Float -> bundle.putFloat(key, value)
                    is Boolean -> bundle.putBoolean(key, value)
                    else -> bundle.putString(key, value.toString())
                }
            }
        } catch (e: Exception) {
            MetricsLogger.e("Map转Bundle失败: ${e.message}")
        }
        return bundle
    }

    /**
     * 将Map转换为JSON字符串（用于日志）
     * @param map 要转换的Map
     * @return JSON字符串
     */
    private fun mapToJson(map: Map<String, Any>): String {
        return try {
            gson.toJson(map)
        } catch (e: Exception) {
            MetricsLogger.e("Map转JSON失败: ${e.message}")
            "{}"
        }
    }

    /**
     * 获取上报器名称
     */
    override fun getName(): String = "Firebase"
    
    /**
     * 实现ReporterData接口的reportData方法
     * @param eventName 事件名称
     * @param data 要上报的数据
     */
    override fun reportData(eventName: String, data: Map<String, Any>) {
        try {
            // 先尝试直接获取Firebase Analytics实例
            var analytics = retrieveFirebaseAnalytics()

            // 如果获取为空，则异步等待阻塞获取
            if (analytics == null) {
                MetricsLogger.d("Firebase Analytics未就绪，开始异步等待...")
                analytics = runBlocking {
                    waitForAnalytics()
                }
            }

            if (analytics == null) {
                MetricsLogger.w("无法获取Firebase Analytics实例，跳过数据上报: $eventName")
                return
            }

            // 将Map转换为Bundle
            val bundle = mapToBundle(data)

            // 上报到Firebase Analytics
            analytics.logEvent(eventName, bundle)

            MetricsLogger.d("Firebase Analytics数据上报: $eventName, JSON: ${mapToJson(data)}")

        } catch (e: Exception) {
            MetricsLogger.e("Firebase Analytics数据上报失败", e)
        }
    }

    /**
     * 实现ReporterData接口的setCommonParams方法
     * @param params 公共参数Map
     */
    override fun setCommonParams(params: Map<String, Any>) {
        try {
            // 先尝试直接获取Firebase Analytics实例
            var analytics = retrieveFirebaseAnalytics()

            // 如果获取为空，则异步等待阻塞获取
            if (analytics == null) {
                MetricsLogger.d("Firebase Analytics未就绪，开始异步等待...")
                analytics = runBlocking {
                    waitForAnalytics()
                }
            }

            if (analytics == null) {
                MetricsLogger.w("无法获取Firebase Analytics实例，跳过设置公共参数")
                return
            }

            // Firebase Analytics使用setDefaultEventParameters设置默认事件参数
            val bundle = mapToBundle(params)
            try {
                analytics.setDefaultEventParameters(bundle)
            } catch (e: Exception) {
                MetricsLogger.e("设置Firebase Analytics默认事件参数失败", e)
            }

            MetricsLogger.d("Firebase Analytics公共参数设置完成: ${mapToJson(params)}")

        } catch (e: Exception) {
            MetricsLogger.e("Firebase Analytics设置公共参数失败", e)
        }
    }

    /**
     * 实现ReporterData接口的setUserParams方法
     * @param params 用户参数Map
     */
    override fun setUserParams(params: Map<String, Any>) {
        try {
            // 先尝试直接获取Firebase Analytics实例
            var analytics = retrieveFirebaseAnalytics()

            // 如果获取为空，则异步等待阻塞获取
            if (analytics == null) {
                MetricsLogger.d("Firebase Analytics未就绪，开始异步等待...")
                analytics = runBlocking {
                    waitForAnalytics()
                }
            }

            if (analytics == null) {
                MetricsLogger.w("无法获取Firebase Analytics实例，跳过设置用户参数")
                return
            }

            // Firebase Analytics使用setUserProperty设置用户属性
            params.forEach { (key, value) ->
                try {
                    analytics.setUserProperty(key, value.toString())
                } catch (e: Exception) {
                    MetricsLogger.e("设置Firebase Analytics用户属性失败: $key = $value", e)
                }
            }

            MetricsLogger.d("Firebase Analytics用户参数设置完成: ${mapToJson(params)}")

        } catch (e: Exception) {
            MetricsLogger.e("Firebase Analytics设置用户参数失败", e)
        }
    }
}