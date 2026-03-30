package net.corekit.metrics.log

import android.util.Log
import net.corekit.metrics.BuildConfig

/**
 * 分析统计日志工具类
 * 提供统一的日志输出控制和管理
 */
object MetricsLogger {
    private const val TAG = "AnalyticsModule"
    
    /**
     * 日志开关，默认为true
     */
    private var isLogEnabled = BuildConfig.DEBUG
    
    /**
     * 设置日志开关
     * @param enabled 是否启用日志
     */
    fun enableLog(enabled: Boolean) {
        isLogEnabled = enabled
    }
    
    /**
     * 获取日志开关状态
     * @return 是否启用日志
     */
    fun checkLogEnabled(): Boolean = isLogEnabled
    
    /**
     * Debug日志
     * @param message 日志消息
     */
    fun d(message: String) {
        if (isLogEnabled) {
            Log.d(TAG, message)
        }
    }
    
    /**
     * Debug日志（带参数）
     * @param message 日志消息模板
     * @param args 参数列表
     */
    fun d(message: String, vararg args: Any?) {
        if (isLogEnabled) {
            Log.d(TAG, message.format(*args))
        }
    }
    
    /**
     * Warning日志
     * @param message 日志消息
     */
    fun w(message: String) {
        if (isLogEnabled) {
            Log.w(TAG, message)
        }
    }
    
    /**
     * Warning日志（带参数）
     * @param message 日志消息模板
     * @param args 参数列表
     */
    fun w(message: String, vararg args: Any?) {
        if (isLogEnabled) {
            Log.w(TAG, message.format(*args))
        }
    }
    
    /**
     * Error日志
     * @param message 日志消息
     */
    fun e(message: String) {
        if (isLogEnabled) {
            Log.e(TAG, message)
        }
    }
    
    /**
     * Error日志（带异常）
     * @param message 日志消息
     * @param throwable 异常对象
     */
    fun e(message: String, throwable: Throwable?) {
        if (isLogEnabled) {
            Log.e(TAG, message, throwable)
        }
    }
    
    /**
     * Error日志（带参数）
     * @param message 日志消息模板
     * @param args 参数列表
     */
    fun e(message: String, vararg args: Any?) {
        if (isLogEnabled) {
            Log.e(TAG, message.format(*args))
        }
    }
    
    /**
     * Error日志（带参数和异常）
     * @param message 日志消息模板
     * @param throwable 异常对象
     * @param args 参数列表
     */
    fun e(message: String, throwable: Throwable?, vararg args: Any?) {
        if (isLogEnabled) {
            Log.e(TAG, message.format(*args), throwable)
        }
    }
    
    /**
     * Info日志
     * @param message 日志消息
     */
    fun i(message: String) {
        if (isLogEnabled) {
            Log.i(TAG, message)
        }
    }
    
    /**
     * Info日志（带参数）
     * @param message 日志消息模板
     * @param args 参数列表
     */
    fun i(message: String, vararg args: Any?) {
        if (isLogEnabled) {
            Log.i(TAG, message.format(*args))
        }
    }
    
    /**
     * Verbose日志
     * @param message 日志消息
     */
    fun v(message: String) {
        if (isLogEnabled) {
            Log.v(TAG, message)
        }
    }
    
    /**
     * Verbose日志（带参数）
     * @param message 日志消息模板
     * @param args 参数列表
     */
    fun v(message: String, vararg args: Any?) {
        if (isLogEnabled) {
            Log.v(TAG, message.format(*args))
        }
    }
}
