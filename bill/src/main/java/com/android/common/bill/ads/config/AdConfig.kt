package com.android.common.bill.ads.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.concurrent.TimeUnit

/**
 * 广告聚合平台枚举
 */
enum class AdPlatform(val key: String) {
    ADMOB("Admob"),
    TOPON("TopOn"),
    PANGLE("Pangle")
}

/**
 * 广告类型枚举
 * @param configKey 配置文件中对应的键名
 */
enum class AdType(val configKey: String) {
    INTERSTITIAL("Interstitial"),
    BANNER("Banner"),
    APP_OPEN("Splash"),
    NATIVE("Native"),
    FULL_SCREEN_NATIVE("FullNative"),
    REWARDED("Rewarded");

    companion object {
        fun fromConfigKey(key: String): AdType? = entries.find { it.configKey == key }
    }
}

/**
 * 广告配置管理器
 */
class AdConfig private constructor(
    private val context: Context,
    private val adType: AdType,
    private val maxDailyShow: Int,
    private val maxDailyClick: Int,
    private val minInterval: Long,
    private val platform: AdPlatform,  // 聚合平台，用于独立计数（必传）
    private val customSpName: String?  // 自定义 SP 名称，用于总控等特殊场景
) {
    companion object {
        // SP存储键前缀
        private const val SP_NAME_PREFIX = "ad_config_"
        private const val KEY_DAILY_SHOW_COUNT = "daily_show_count"
        private const val KEY_DAILY_CLICK_COUNT = "daily_click_count"
        private const val KEY_LAST_SHOW_TIME = "last_show_time"
        private const val KEY_LAST_DATE = "last_date"
        
        // 默认配置
        private const val DEFAULT_MAX_DAILY_SHOW = 50  // 每日最大展示次数
        private const val DEFAULT_MAX_DAILY_CLICK = 10 // 每日最大点击次数
        private const val DEFAULT_MIN_INTERVAL = 30L   // 最小展示间隔（秒）
    }
    
    // SP 名称：优先使用自定义名称，否则使用平台+广告类型生成
    private val spName: String = customSpName ?: "${SP_NAME_PREFIX}${platform.key}_${adType.configKey}"
    
    private val sp: SharedPreferences = context.getSharedPreferences(
        spName,
        Context.MODE_PRIVATE
    )
    
    /**
     * 获取当前平台
     */
    fun getPlatform(): AdPlatform = platform
    
    /**
     * 获取当日展示次数
     */
    fun getDailyShowCount(): Int {
        checkAndResetDaily()
        return sp.getInt(KEY_DAILY_SHOW_COUNT, 0)
    }
    
    /**
     * 获取当日点击次数
     */
    fun getDailyClickCount(): Int {
        checkAndResetDaily()
        return sp.getInt(KEY_DAILY_CLICK_COUNT, 0)
    }
    
    /**
     * 获取距离上次展示的间隔（秒）
     */
    fun getLastShowInterval(): Long {
        val lastShowTime = sp.getLong(KEY_LAST_SHOW_TIME, 0L)
        if (lastShowTime == 0L) return Long.MAX_VALUE
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - lastShowTime)
    }
    
    /**
     * 获取最大每日展示次数
     */
    fun getMaxDailyShow(): Int = maxDailyShow
    
    /**
     * 获取最大每日点击次数
     */
    fun getMaxDailyClick(): Int = maxDailyClick
    
    /**
     * 获取最小展示间隔（秒）
     */
    fun getMinInterval(): Long = minInterval
    
    /**
     * 获取广告类型
     */
    fun getAdType(): AdType = adType
    
    /**
     * 记录展示
     */
    fun recordShow() {
        synchronized(sp) {
            checkAndResetDaily()
            val current = sp.getInt(KEY_DAILY_SHOW_COUNT, 0)
            sp.edit {
                putInt(KEY_DAILY_SHOW_COUNT, current + 1)
                putLong(KEY_LAST_SHOW_TIME, System.currentTimeMillis())
            }
        }
    }
    
    /**
     * 重置上次展示时间（用于处理系统时间异常）
     */
    fun resetLastShowTime() {
        sp.edit {
            putLong(KEY_LAST_SHOW_TIME, 0L)
        }
    }
    
    /**
     * 记录点击
     */
    fun recordClick() {
        synchronized(sp) {
            checkAndResetDaily()
            val current = sp.getInt(KEY_DAILY_CLICK_COUNT, 0)
            sp.edit {
                putInt(KEY_DAILY_CLICK_COUNT, current + 1)
            }
        }
    }
    
    /**
     * 检查并重置每日统计
     */
    private fun checkAndResetDaily() {
        val today = java.time.LocalDate.now().toString()
        val lastDate = sp.getString(KEY_LAST_DATE, "")
        
        if (today != lastDate) {
            // 新的一天，重置统计
            sp.edit {
                putString(KEY_LAST_DATE, today)
                putInt(KEY_DAILY_SHOW_COUNT, 0)
                putInt(KEY_DAILY_CLICK_COUNT, 0)
            }
        }
    }
    
    /**
     * 建造者
     */
    class Builder(
        private val context: Context,
        private val adType: AdType,
        private val platform: AdPlatform  // 必传参数
    ) {
        private var maxDailyShow: Int = DEFAULT_MAX_DAILY_SHOW
        private var maxDailyClick: Int = DEFAULT_MAX_DAILY_CLICK
        private var minInterval: Long = DEFAULT_MIN_INTERVAL
        private var customSpName: String? = null
        
        /**
         * 设置每日最大展示次数
         * 如果远程配置下发 <= 0，则使用默认值避免崩溃
         */
        fun setMaxDailyShow(count: Int): Builder {
            maxDailyShow = count.coerceAtLeast(1)
            return this
        }
        
        /**
         * 设置每日最大点击次数
         * 如果远程配置下发 <= 0，则使用默认值避免崩溃
         */
        fun setMaxDailyClick(count: Int): Builder {
            maxDailyClick = count.coerceAtLeast(1)
            return this
        }
        
        /**
         * 设置最小展示间隔（秒）
         * 如果远程配置下发 < 0，则使用 0 避免崩溃
         */
        fun setMinInterval(seconds: Long): Builder {
            minInterval = seconds.coerceAtLeast(0)
            return this
        }
        
        /**
         * 设置自定义 SP 名称（用于总控等特殊场景）
         */
        fun setSpName(spName: String): Builder {
            customSpName = spName
            return this
        }
        
        /**
         * 构建配置实例
         */
        fun build(): AdConfig {
            return AdConfig(
                context = context.applicationContext,
                adType = adType,
                maxDailyShow = maxDailyShow,
                maxDailyClick = maxDailyClick,
                minInterval = minInterval,
                platform = platform,
                customSpName = customSpName
            )
        }
    }
} 
