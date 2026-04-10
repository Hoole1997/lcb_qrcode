package net.corekit.metrics.data

import android.content.Context
import cn.thinkingdata.analytics.TDAnalytics
import com.google.gson.Gson
import net.corekit.metrics.BuildConfig
import net.corekit.metrics.log.MetricsLogger
import net.corekit.core.report.ReporterData
import org.json.JSONObject

/**
 * 数数SDK控制器
 */
class ThinkingReporter : ReporterData {

    private val gson = Gson()

    companion object {
        private var isInitialized = true

        /**
         * 初始化数数SDK
         * @param context 应用上下文
         */
        fun init(context: Context) {

            if (isInitialized) {
                MetricsLogger.w("数数SDK已经初始化过了")
                return
            }

            try {
                val appId = BuildConfig.THINKING_DATA_APP_ID
                val serverUrl = BuildConfig.THINKING_DATA_SERVER_URL

                if (appId.isBlank() || serverUrl.isBlank()) {
                    MetricsLogger.e("数数SDK App ID 未配置")
                    return
                }

                // 初始化数数SDK
                TDAnalytics.init(context, appId, serverUrl)
                TDAnalytics.enableLog(MetricsLogger.checkLogEnabled())
                TDAnalytics.enableAutoTrack(
                    (TDAnalytics.TDAutoTrackEventType.APP_START or TDAnalytics.TDAutoTrackEventType.APP_END
                            or TDAnalytics.TDAutoTrackEventType.APP_INSTALL or TDAnalytics.TDAutoTrackEventType.APP_VIEW_SCREEN))

                isInitialized = true
                MetricsLogger.i("数数SDK初始化成功，App ID: $appId, Server URL: $serverUrl")

            } catch (e: Exception) {
                MetricsLogger.e("数数SDK初始化失败", e)
            }
        }
    }


    /**
     * 将Map转换为JSONObject
     * @param map 要转换的Map
     * @return JSONObject
     */
    private fun mapToJsonObject(map: Map<String, Any>): JSONObject {
        return try {
            val jsonString = gson.toJson(map)
            JSONObject(jsonString)
        } catch (e: Exception) {
            MetricsLogger.e("Map转JSONObject失败: ${e.message}")
            JSONObject()
        }
    }

    /**
     * 获取上报器名称
     */
    override fun getName(): String = "ThinkingData"
    
    /**
     * 实现ReporterData接口的reportData方法
     * @param eventName 事件名称
     * @param data 要上报的数据
     */
    override fun reportData(eventName: String, data: Map<String, Any>) {
        if (!isInitialized) {
            MetricsLogger.w("数数SDK未初始化，无法上报数据")
            return
        }
        try {
            // 将Map转换为JSONObject
            val jsonObject = mapToJsonObject(data)

            // 上报到数数SDK
            TDAnalytics.track(eventName, jsonObject)

            MetricsLogger.d("数数SDK数据上报: $eventName, JSON: ${jsonObject}")

        } catch (e: Exception) {
            MetricsLogger.e("数数SDK数据上报失败", e)
        }
    }

    /**
     * 实现ReporterData接口的setCommonParams方法
     * @param params 公共参数Map
     */
    override fun setCommonParams(params: Map<String, Any>) {
        if (!isInitialized) {
            MetricsLogger.w("数数SDK未初始化，无法设置公共参数")
            return
        }
        try {
            // 将Map转换为JSONObject
            val jsonObject = mapToJsonObject(params)
            
            // 数数SDK使用userSet设置用户属性，传入JSONObject
            TDAnalytics.setSuperProperties(jsonObject)

            MetricsLogger.d("数数SDK公共参数设置完成: $jsonObject")

        } catch (e: Exception) {
            MetricsLogger.e("数数SDK设置公共参数失败", e)
        }
    }

    /**
     * 实现ReporterData接口的setUserParams方法
     * @param params 用户参数Map
     */
    override fun setUserParams(params: Map<String, Any>) {
        if (!isInitialized) {
            MetricsLogger.w("数数SDK未初始化，无法设置用户参数")
            return
        }
        try {
            // 将Map转换为JSONObject
            val jsonObject = mapToJsonObject(params)
            
            // 数数SDK使用userSet设置用户属性，传入JSONObject
            TDAnalytics.userSet(jsonObject)

            MetricsLogger.d("数数SDK用户参数设置完成: $jsonObject")

        } catch (e: Exception) {
            MetricsLogger.e("数数SDK设置用户参数失败", e)
        }
    }

    /**
     * 检查是否已初始化
     */
    fun checkInitialized(): Boolean = isInitialized

}