package com.android.common.bill.ads.tracker

import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.util.PositionGet
import net.corekit.core.report.ReportDataManager
import java.util.UUID

/**
 * 广告事件类型枚举
 * 统一定义所有广告事件名称，确保一致性
 */
enum class AdEventType(val eventName: String) {
    // 加载相关
    START_LOAD("ad_start_load"),
    LOADED("ad_loaded"),
    LOAD_FAIL("ad_load_fail"),
    
    // 展示相关
    POSITION("ad_position"),
    IMPRESSION("ad_impression"),
    SHOW_FAIL("ad_show_fail"),
    
    // 交互相关
    CLICK("ad_click"),
    CLOSE("ad_close"),
    
    // 缓存相关
    NO_CACHE("ad_no_cache"),
    TIMEOUT_CACHE("ad_timeout_cache"),
    
    // 激励广告专用
    REWARD_EARNED("ad_reward_earned")
}

/**
 * 广告事件上报器
 * 统一管理所有广告事件的上报，使用 Builder 模式构建事件数据
 * 
 * 使用示例:
 * ```
 * AdEventReporter.builder(AdEventType.IMPRESSION)
 *     .adType(AdType.APP_OPEN)
 *     .platform(AdPlatform.ADMOB)
 *     .adUnitId("ca-app-pub-xxx")
 *     .adUniqueId(uuid)
 *     .adSource("AdMob")
 *     .value(0.001)
 *     .currency("USD")
 *     .report()
 * ```
 */
object AdEventReporter {

    private const val TAG = "AdEventReporter"
    private const val MAX_REASON_LENGTH = 64
    private const val MAX_TERMINAL_SESSION_CACHE_SIZE = 2048
    private const val MAX_TERMINAL_REQUEST_CACHE_SIZE = 4096
    private val terminalSessionIds = LinkedHashSet<String>()
    private val terminalRequestIds = LinkedHashSet<String>()
    private val sessionTerminalListeners = LinkedHashMap<String, (String) -> Unit>()

    /**
     * 创建事件构建器
     */
    fun builder(eventType: AdEventType): EventBuilder {
        return EventBuilder(eventType)
    }

    // ==================== 便捷方法（常用场景）====================

    /**
     * 上报广告位触发（ad_position）
     * @return 生成的 session_id
     */
    fun reportPosition(adType: AdType, number: Int): String {
        val sessionId = UUID.randomUUID().toString()
        builder(AdEventType.POSITION)
            .adType(adType)
            .number(number)
            .sessionId(sessionId)
            .report()
        return sessionId
    }

    /**
     * 上报开始加载（ad_start_load）
     * @return 生成的 request_id
     */
    fun reportStartLoad(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        number: Int
    ): String {
        val requestId = UUID.randomUUID().toString()
        builder(AdEventType.START_LOAD)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .number(number)
            .requestId(requestId)
            .report()
        return requestId
    }

    /**
     * 上报加载成功（ad_loaded）
     */
    fun reportLoaded(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        number: Int,
        adSource: String,
        passTime: Int,
        requestId: String = ""
    ) {
        if (!markRequestTerminalOnce(requestId, AdEventType.LOADED)) return
        builder(AdEventType.LOADED)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .number(number)
            .adSource(adSource)
            .passTime(passTime)
            .requestId(requestId)
            .report()
    }

    /**
     * 上报加载失败（ad_load_fail）
     */
    fun reportLoadFail(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        number: Int,
        adSource: String,
        passTime: Int,
        reason: String,
        requestId: String = ""
    ) {
        if (!markRequestTerminalOnce(requestId, AdEventType.LOAD_FAIL)) return
        builder(AdEventType.LOAD_FAIL)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .number(number)
            .adSource(adSource)
            .passTime(passTime)
            .reason(reason)
            .requestId(requestId)
            .report()
    }

    /**
     * 上报展示成功（ad_impression）
     */
    fun reportImpression(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        adUniqueId: String,
        number: Int,
        adSource: String,
        value: Double,
        currency: String,
        sessionId: String = "",
        isPreload: Boolean = false
    ) {
        if (!markSessionTerminal(sessionId, AdEventType.IMPRESSION)) return
        builder(AdEventType.IMPRESSION)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .adUniqueId(adUniqueId)
            .number(number)
            .adSource(adSource)
            .value(value)
            .currency(currency)
            .sessionId(sessionId)
            .isPreload(isPreload)
            .report()
    }

    /**
     * 上报展示失败（ad_show_fail）
     */
    fun reportShowFail(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        number: Int,
        reason: String,
        adSource: String? = null,
        sessionId: String = "",
        isPreload: Boolean = false
    ) {
        if (!markSessionTerminal(sessionId, AdEventType.SHOW_FAIL)) return
        builder(AdEventType.SHOW_FAIL)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .number(number)
            .reason(reason)
            .sessionId(sessionId)
            .isPreload(isPreload)
            .apply { adSource?.let { adSource(it) } }
            .report()
    }

    /**
     * 上报展示失败（ad_show_fail）- 无平台信息版本
     * 用于 checkCanShow 提前返回时（全局开关关闭、总控限制等）
     */
    fun reportShowFailNoAd(
        adType: AdType,
        reason: String,
        sessionId: String = "",
        isPreload: Boolean = false
    ) {
        if (!markSessionTerminal(sessionId, AdEventType.SHOW_FAIL)) return
        builder(AdEventType.SHOW_FAIL)
            .adType(adType)
            .reason(reason)
            .sessionId(sessionId)
            .isPreload(isPreload)
            .report()
    }

    /**
     * 判断某个 session 是否已产生终态（impression/show_fail）
     */
    fun isSessionTerminal(sessionId: String): Boolean {
        if (sessionId.isBlank()) return false
        synchronized(terminalSessionIds) {
            return terminalSessionIds.contains(sessionId)
        }
    }

    /**
     * 注册 session 终态监听器（仅会在该 session 首次进入终态时触发）
     */
    fun registerSessionTerminalListener(key: String, listener: (String) -> Unit) {
        if (key.isBlank()) return
        synchronized(sessionTerminalListeners) {
            sessionTerminalListeners[key] = listener
        }
    }

    /**
     * 反注册 session 终态监听器
     */
    fun unregisterSessionTerminalListener(key: String) {
        if (key.isBlank()) return
        synchronized(sessionTerminalListeners) {
            sessionTerminalListeners.remove(key)
        }
    }

    private fun markSessionTerminal(sessionId: String, eventType: AdEventType): Boolean {
        if (sessionId.isBlank()) return true
        val isNewTerminal: Boolean
        synchronized(terminalSessionIds) {
            isNewTerminal = terminalSessionIds.add(sessionId)
            if (isNewTerminal && terminalSessionIds.size > MAX_TERMINAL_SESSION_CACHE_SIZE) {
                val oldest = terminalSessionIds.iterator()
                if (oldest.hasNext()) {
                    oldest.next()
                    oldest.remove()
                }
            }
        }
        if (!isNewTerminal) {
            AdLogger.w(
                "忽略重复展示终态事件: event=%s, session_id=%s",
                eventType.eventName,
                sessionId
            )
            return false
        }
        if (isNewTerminal) {
            notifySessionTerminalListeners(sessionId)
        }
        return true
    }

    private fun notifySessionTerminalListeners(sessionId: String) {
        val listeners = synchronized(sessionTerminalListeners) {
            sessionTerminalListeners.values.toList()
        }
        listeners.forEach { listener ->
            try {
                listener.invoke(sessionId)
            } catch (e: Exception) {
                AdLogger.e("session terminal listener 执行异常: session_id=$sessionId", e)
            }
        }
    }

    private fun markRequestTerminalOnce(requestId: String, eventType: AdEventType): Boolean {
        if (requestId.isBlank()) return true
        synchronized(terminalRequestIds) {
            if (!terminalRequestIds.add(requestId)) {
                AdLogger.w(
                    "忽略重复加载终态事件: event=%s, request_id=%s",
                    eventType.eventName,
                    requestId
                )
                return false
            }
            if (terminalRequestIds.size > MAX_TERMINAL_REQUEST_CACHE_SIZE) {
                val oldest = terminalRequestIds.iterator()
                if (oldest.hasNext()) {
                    oldest.next()
                    oldest.remove()
                }
            }
            return true
        }
    }

    /**
     * 上报无缓存（ad_no_cache）
     */
    fun reportNoCache(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String
    ) {
        builder(AdEventType.NO_CACHE)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .report()
    }

    /**
     * 上报缓存过期（ad_timeout_cache）
     */
    fun reportTimeoutCache(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String
    ) {
        builder(AdEventType.TIMEOUT_CACHE)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .report()
    }

    /**
     * 上报点击（ad_click）
     */
    fun reportClick(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        adUniqueId: String,
        number: Int,
        adSource: String,
        value: Double,
        currency: String
    ) {
        builder(AdEventType.CLICK)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .adUniqueId(adUniqueId)
            .number(number)
            .adSource(adSource)
            .value(value)
            .currency(currency)
            .report()
    }

    /**
     * 上报关闭（ad_close）
     */
    fun reportClose(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        number: Int,
        adSource: String,
        value: Double,
        currency: String
    ) {
        builder(AdEventType.CLOSE)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .number(number)
            .adSource(adSource)
            .value(value)
            .currency(currency)
            .report()
    }

    /**
     * 上报激励获得（ad_reward_earned）
     */
    fun reportRewardEarned(
        adType: AdType,
        platform: AdPlatform,
        adUnitId: String,
        number: Int,
        adSource: String,
        rewardType: String,
        rewardAmount: Int
    ) {
        builder(AdEventType.REWARD_EARNED)
            .adType(adType)
            .platform(platform)
            .adUnitId(adUnitId)
            .number(number)
            .adSource(adSource)
            .param("reward_label", rewardType)
            .param("reward_amount", rewardAmount)
            .report()
    }

    /**
     * 事件构建器
     * 使用 Builder 模式灵活构建事件参数
     */
    class EventBuilder(private val eventType: AdEventType) {
        private val params = mutableMapOf<String, Any>()
        private var adType: AdType? = null
        private var platform: AdPlatform? = null

        fun adType(adType: AdType): EventBuilder {
            this.adType = adType
            return this
        }

        fun platform(platform: AdPlatform): EventBuilder {
            this.platform = platform
            return this
        }

        fun adUnitId(adUnitId: String): EventBuilder {
            params["ad_unit_name"] = adUnitId
            return this
        }

        fun adUniqueId(adUniqueId: String): EventBuilder {
            params["ad_unique_id"] = adUniqueId
            return this
        }

        fun number(number: Int): EventBuilder {
            params["number"] = number
            return this
        }

        fun adSource(adSource: String): EventBuilder {
            params["ad_source"] = adSource
            return this
        }

        fun passTime(passTime: Int): EventBuilder {
            params["pass_time"] = passTime
            return this
        }

        fun reason(reason: String): EventBuilder {
            params["reason"] = normalizeReason(reason)
            return this
        }

        fun sessionId(sessionId: String): EventBuilder {
            if (sessionId.isNotEmpty()) {
                params["session_id"] = sessionId
            }
            return this
        }

        fun requestId(requestId: String): EventBuilder {
            if (requestId.isNotEmpty()) {
                params["request_id"] = requestId
            }
            return this
        }

        fun isPreload(isPreload: Boolean): EventBuilder {
            params["is_preload"] = isPreload
            return this
        }

        fun value(value: Double): EventBuilder {
            params["value"] = value
            return this
        }

        fun currency(currency: String): EventBuilder {
            params["currency"] = currency
            return this
        }

        fun position(position: String): EventBuilder {
            params["position"] = position
            return this
        }

        /**
         * 添加自定义参数
         */
        fun param(key: String, value: Any): EventBuilder {
            params[key] = value
            return this
        }

        /**
         * 执行上报
         */
        fun report() {
            val data = mutableMapOf<String, Any>()

            // 添加基础参数
            adType?.let { data["ad_format"] = it.configKey }
            platform?.let { data["ad_platform"] = it.key }

            // 自动添加 position（如果未手动设置）
            if (!params.containsKey("position")) {
                data["position"] = PositionGet.get()
            }

            // 合并自定义参数
            data.putAll(params)

            // 上报事件
            val eventName = eventType.eventName
            AdLogger.d("$TAG 上报事件: $eventName, 参数: $data")

            // ad_impression 需要单独通过 ThinkingData 上报
            if (eventType == AdEventType.IMPRESSION) {
                ReportDataManager.reportDataByName("ThinkingData", eventName, data)
            } else {
                ReportDataManager.reportData(eventName, data)
            }
        }

        private fun normalizeReason(rawReason: String): String {
            val trimmed = rawReason.trim()
            if (trimmed.isEmpty()) return "unknown"

            // 已经是短码/短文本则直接使用，避免改动已有埋点语义
            if (trimmed.length <= MAX_REASON_LENGTH && !trimmed.contains('\n')) {
                return trimmed
            }

            val lower = trimmed.lowercase()
            val mapped = when {
                lower.contains("ad unit") && lower.contains("invalid or disabled") -> "invalid_or_disabled_ad_unit"
                lower.contains("ad unit") && lower.contains("invalid") -> "invalid_ad_unit"
                lower.contains("cannot load ads until") -> "ad_unit_not_ready"
                lower.contains("no eligible ads") -> "no_fill"
                lower.contains("no fill") -> "no_fill"
                lower.contains("return ad is empty") -> "no_fill"
                lower.contains("timed out") || lower.contains("timeout") -> "timeout"
                lower.contains("activity destroyed") -> "activity_destroyed"
                lower.contains("not ready") -> "ad_not_ready"
                lower.contains("network error") -> "network_error"
                lower.contains("show exception") -> "show_exception"
                lower.contains("load exception") -> "load_exception"
                else -> {
                    val firstLine = trimmed.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                    firstLine.replace(Regex("\\s+"), " ")
                }
            }

            return mapped
                .trim()
                .ifEmpty { "unknown" }
                .take(MAX_REASON_LENGTH)
        }
    }
}
