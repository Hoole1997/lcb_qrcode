package net.corekit.metrics.adjust

import android.content.Context
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.AdjustEvent
import com.adjust.sdk.OnAttributionChangedListener
import com.adjust.sdk.OnEventTrackingSucceededListener
import com.adjust.sdk.OnEventTrackingFailedListener
import com.adjust.sdk.OnSessionTrackingSucceededListener
import com.adjust.sdk.OnSessionTrackingFailedListener
import com.adjust.sdk.AdjustAttribution
import com.adjust.sdk.AdjustEventFailure
import com.adjust.sdk.AdjustEventSuccess
import com.adjust.sdk.AdjustSessionFailure
import com.adjust.sdk.AdjustSessionSuccess
import com.adjust.sdk.LogLevel
import com.adjust.sdk.OnAdidReadListener
import net.corekit.core.controller.ChannelUserController
import net.corekit.metrics.BuildConfig
import net.corekit.metrics.log.MetricsLogger
import net.corekit.metrics.report.SharedParamsManager
import net.corekit.core.report.ReportDataManager

/**
 * Adjust归因控制器
 * 负责Adjust SDK的初始化和归因数据获取
 */
object AdjustTracker {

    private var isInitialized = false
    private var attributionData: AdjustAttribution? = null
    private var initStartTime: Long = 0

    /**
     * 初始化Adjust SDK
     * @param context 应用上下文
     */
    fun init(context: Context,network: String?,campaign: String?,adgroup: String?,creative: String?,jsonResponse: String?) {
        if (isInitialized) {
            MetricsLogger.w("Adjust SDK 已经初始化过了")
            return
        }

        // 记录初始化开始时间
        initStartTime = System.currentTimeMillis()
        MetricsLogger.d("Adjust SDK 初始化开始")

        // 初始化登录相关参数
        SharedParamsManager.initLoginData()
        MetricsLogger.d("登录参数初始化完成 - login_day: ${SharedParamsManager.loginDay ?: ""}, is_new: ${SharedParamsManager.isNew ?: ""}")

        // 先设置登录参数到ReportDataManager
        val loginParams = SharedParamsManager.retrieveAllCommonParams()
        val userParams = SharedParamsManager.retrieveUserCommonParams()
        ReportDataManager.setCommonParams(loginParams)
        ReportDataManager.setUserParams(userParams)
        MetricsLogger.d("登录参数已设置到ReportDataManager: $loginParams")

        // 初始化埋点
        ReportDataManager.reportData("adjust_init", mapOf())
        try {
//            val appToken = BuildConfig.ADJUST_APP_TOKEN
//            if (appToken.isBlank()) {
//                MetricsLogger.e("Adjust App Token 未配置或使用默认值")
//                return
//            }

            // 创建Adjust配置
//            val adjustConfig = AdjustConfig(
//                context,
//                appToken,
//                if (MetricsLogger.checkLogEnabled()) AdjustConfig.ENVIRONMENT_SANDBOX else AdjustConfig.ENVIRONMENT_PRODUCTION
//            )
//            adjustConfig.setLogLevel(LogLevel.VERBOSE)
//
//            // 启用成本数据在归因信息中
//            adjustConfig.enableCostDataInAttribution()

            // 设置公共参数，并限制长度
            SharedParamsManager.adNetwork = (network ?: "").take(10)
            SharedParamsManager.campaign = (campaign ?: "").take(20)
            SharedParamsManager.adgroup = (adgroup ?: "").take(10)
            SharedParamsManager.creative = (creative ?: "").take(20)

            MetricsLogger.d("公共参数设置完成 - ad_network: ${SharedParamsManager.adNetwork}, campaign: ${SharedParamsManager.campaign}, adgroup: ${SharedParamsManager.adgroup}, creative: ${SharedParamsManager.creative}")

            // 将公共参数设置到ReportDataManager
            val commonParams = SharedParamsManager.retrieveAllCommonParams()
            val userParams = SharedParamsManager.retrieveUserCommonParams()
            ReportDataManager.setCommonParams(commonParams)
            ReportDataManager.setUserParams(userParams)
            MetricsLogger.d("公共参数已设置到ReportDataManager: $commonParams")

            // 计算从初始化开始到归因回调的总耗时（秒数，向上取整）
            val totalDurationSeconds = kotlin.math.ceil((System.currentTimeMillis() - initStartTime) / 1000.0).toInt()
            MetricsLogger.d("Adjust初始化到归因回调总耗时: ${totalDurationSeconds}秒")
            ReportDataManager.reportData("adjust_get_success", mapOf("pass_time" to totalDurationSeconds))

            // 设置当前用户渠道类型
            val userChannelType = if (MetricsLogger.checkLogEnabled()) {
                // 内部版本强制设置为买量类型
                MetricsLogger.d("内部版本强制设置为买量类型")
                ChannelUserController.UserChannelType.PAID
            } else {
                determineUserChannelType(network,jsonResponse)
            }
            MetricsLogger.d("根据归因数据判断用户渠道类型: $userChannelType")

            // 设置用户渠道类型
            val success = ChannelUserController.setChannel(userChannelType)
            if (success) {
                MetricsLogger.i("用户渠道类型设置成功: $userChannelType")
            } else {
                MetricsLogger.w("用户渠道类型已经设置过，无法修改")
            }

            // 设置归因回调
//            adjustConfig.setOnAttributionChangedListener(object : OnAttributionChangedListener {
//                override fun onAttributionChanged(attribution: AdjustAttribution?) {
//
//                    attributionData = attribution
//                    MetricsLogger.d("Adjust归因数据更新: $attribution")
//
//                    // 设置公共参数
//                    attribution?.let { attr ->
//
//                    }
//                }
//            })
//
//            // 设置事件跟踪成功回调
//            adjustConfig.setOnEventTrackingSucceededListener(object :
//                OnEventTrackingSucceededListener {
//                override fun onEventTrackingSucceeded(eventSuccessResponseData: AdjustEventSuccess?) {
//                    MetricsLogger.d("Adjust事件跟踪成功: ${eventSuccessResponseData?.message}")
//                }
//            })
//
//            // 设置事件跟踪失败回调
//            adjustConfig.setOnEventTrackingFailedListener(object : OnEventTrackingFailedListener {
//                override fun onEventTrackingFailed(eventFailureResponseData: AdjustEventFailure?) {
//                    MetricsLogger.e("Adjust事件跟踪失败: ${eventFailureResponseData?.message}")
//                }
//            })
//
//            // 设置会话跟踪成功回调
//            adjustConfig.setOnSessionTrackingSucceededListener(object :
//                OnSessionTrackingSucceededListener {
//                override fun onSessionTrackingSucceeded(sessionSuccessResponseData: AdjustSessionSuccess?) {
//                    MetricsLogger.d("Adjust会话跟踪成功: ${sessionSuccessResponseData?.message}")
//                }
//            })
//
//            // 设置会话跟踪失败回调
//            adjustConfig.setOnSessionTrackingFailedListener(object :
//                OnSessionTrackingFailedListener {
//                override fun onSessionTrackingFailed(sessionFailureResponseData: AdjustSessionFailure?) {
//                    MetricsLogger.e("Adjust会话跟踪失败: ${sessionFailureResponseData?.message}")
//                }
//            })
//
//            // 启动Adjust SDK
//            Adjust.initSdk(adjustConfig)

            isInitialized = true
            MetricsLogger.i("Adjust SDK 初始化成功")

        } catch (e: Exception) {
            MetricsLogger.e("Adjust SDK 初始化失败", e)
        }
    }

    /**
     * 根据归因数据判断用户渠道类型
     * @param attribution Adjust归因数据
     * @return 用户渠道类型
     */
    private fun determineUserChannelType(network: String?,jsonResponse: String?): ChannelUserController.UserChannelType {
        // 获取归因数据的关键字段
        val network = network?.lowercase()

        MetricsLogger.d("归因数据 - network: $network")

        // 判断是否为自然渠道的条件
        val isOrganic = when {
            // 1. Organic - 有机渠道
            network == "organic" -> {
                MetricsLogger.d("检测到Organic渠道")
                true
            }
            // 2. Untrusted Devices - 不可信设备
            network == "untrusted devices" -> {
                MetricsLogger.d("检测到Untrusted Devices渠道")
                true
            }
            // 3. Google Organic Search - Google有机搜索
            network == "google organic search" -> {
                MetricsLogger.d("检测到Google Organic Search渠道")
                true
            }
            // 4. 其他情况都认为是买量渠道
            else -> {
                MetricsLogger.d("检测到买量渠道 - network: $network")
                false
            }
        }

        return if (isOrganic) {
            ChannelUserController.UserChannelType.NATURAL
        } else {
            ChannelUserController.UserChannelType.PAID
        }
    }

    /**
     * 获取归因数据
     * @return AdjustAttribution对象，如果未初始化或没有数据则返回null
     */
    fun retrieveAttribution(): AdjustAttribution? {
        if (!isInitialized) {
            MetricsLogger.w("Adjust SDK 未初始化，无法获取归因数据")
            return null
        }

        return attributionData
    }

    /**
     * 获取设备的Adjust ID（异步）
     * @param callback 回调函数，参数为adid字符串
     */
    fun retrieveAdid(callback: (String?) -> Unit) {
        if (!isInitialized) {
            MetricsLogger.w("Adjust SDK 未初始化，无法获取Adid")
            callback(null)
            return
        }

        Adjust.getAdid(object : OnAdidReadListener {
            override fun onAdidRead(adid: String?) {
                MetricsLogger.d("获取到Adjust Adid: $adid")
                callback(adid)
            }
        })
    }

    /**
     * 获取归因信息（简化版本）
     * 注意：adid字段需要通过异步回调获取，这里返回null
     * @return 包含归因信息的Map
     */
    fun retrieveAttributionInfo(): Map<String, String?> {
        val attribution = retrieveAttribution() ?: return emptyMap()

        return mapOf(
            "trackerToken" to attribution.trackerToken,
            "trackerName" to attribution.trackerName,
            "network" to attribution.network,
            "campaign" to attribution.campaign,
            "adgroup" to attribution.adgroup,
            "creative" to attribution.creative,
            "clickLabel" to attribution.clickLabel,
            "adid" to null, // adid需要通过getAdid(callback)异步获取
            "costType" to attribution.costType,
            "costAmount" to attribution.costAmount?.toString(),
            "costCurrency" to attribution.costCurrency,
            "fbInstallReferrer" to attribution.fbInstallReferrer,
            "jsonResponse" to attribution.jsonResponse
        )
    }

    /**
     * 获取完整的归因信息（包括异步adid）
     * @param callback 回调函数，参数为包含adid的完整归因信息Map
     */
    fun retrieveCompleteAttributionInfo(callback: (Map<String, String?>) -> Unit) {
        val attribution = retrieveAttribution() ?: return callback(emptyMap())

        // 先获取基本归因信息
        val baseInfo = mapOf(
            "trackerToken" to attribution.trackerToken,
            "trackerName" to attribution.trackerName,
            "network" to attribution.network,
            "campaign" to attribution.campaign,
            "adgroup" to attribution.adgroup,
            "creative" to attribution.creative,
            "clickLabel" to attribution.clickLabel,
            "costType" to attribution.costType,
            "costAmount" to attribution.costAmount?.toString(),
            "costCurrency" to attribution.costCurrency,
            "fbInstallReferrer" to attribution.fbInstallReferrer,
            "jsonResponse" to attribution.jsonResponse
        )

        // 异步获取adid
        retrieveAdid { adid ->
            val completeInfo = baseInfo + ("adid" to adid)
            callback(completeInfo)
        }
    }

    /**
     * 跟踪事件
     * @param eventToken 事件Token
     * @param revenue 收入（可选）
     * @param currency 货币（可选）
     * @param callbackParams 回调参数（可选）
     * @param partnerParams 合作伙伴参数（可选）
     */
    fun track(
        eventToken: String,
        revenue: Double? = null,
        currency: String? = null,
        callbackParams: Map<String, String>? = null,
        partnerParams: Map<String, String>? = null
    ) {
        if (!isInitialized) {
            MetricsLogger.w("Adjust SDK 未初始化，无法跟踪事件")
            return
        }

        try {
            val adjustEvent = AdjustEvent(eventToken)

            // 设置收入
            revenue?.let { adjustEvent.setRevenue(it, currency) }

            // 设置回调参数
            callbackParams?.forEach { (key, value) ->
                adjustEvent.addCallbackParameter(key, value)
            }

            // 设置合作伙伴参数
            partnerParams?.forEach { (key, value) ->
                adjustEvent.addPartnerParameter(key, value)
            }

            Adjust.trackEvent(adjustEvent)
            MetricsLogger.d("Adjust事件跟踪: $eventToken")

        } catch (e: Exception) {
            MetricsLogger.e("Adjust事件跟踪失败: $eventToken", e)
        }
    }

    /**
     * 检查是否已初始化
     */
    fun checkInitialized(): Boolean = isInitialized
}
