package net.corekit.core.utils

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import net.corekit.core.BuildConfig
import net.corekit.core.ext.autoDecryptIfNeeded
import net.corekit.core.ext.autoEncryptIfNeeded
import net.corekit.core.log.CoreLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import net.corekit.core.utils.RemoteConfigParams.isShowSplash

/**
 * Firebase Remote Config 管理器
 * 负责远程配置的初始化和提供 FirebaseRemoteConfig 实例
 */
object ConfigRemoteManager {
    private const val TAG = "AdModule"
    private const val MINIMUM_FETCH_INTERVAL = 3600L // 1小时最小获取间隔
    private const val WAIT_INTERVAL = 100L // 等待间隔100ms
    private const val MAX_WAIT_CYCLES = 300 // 最大等待循环次数 (30秒)
    
    private lateinit var firebaseRemoteConfig: FirebaseRemoteConfig
    private var isInitialized = false

    // 远程配置更新回调
    private var onConfigUpdatedCallback: (() -> Unit)? = null

    /**
     * 设置远程配置更新回调
     * @param callback 配置更新后的回调
     */
    fun setOnConfigUpdatedCallback(callback: () -> Unit) {
        onConfigUpdatedCallback = callback
    }

    /**
     * 独立的初始化函数
     */
    fun initialize() {
        if (isInitialized) {
            logDebug("Remote Config 已经初始化")
            return
        }

        logDebug("开始初始化 Remote Config")

        try {
            FirebaseApp.initializeApp(ProviderContext.getAppContext())
            // 初始化 Firebase Remote Config
            firebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

            // 配置 Remote Config 设置
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(MINIMUM_FETCH_INTERVAL)
                .setFetchTimeoutInSeconds(60) // 60秒超时
                .build()
            firebaseRemoteConfig.setConfigSettingsAsync(configSettings)
            firebaseRemoteConfig.setDefaultsAsync(
                mapOf(
                    encryptedRemoteKey(RemoteConfigParams.PARAM_IS_SHOW_LOADING_PAGE_WHEN_BACK) to RemoteConfigParams.DEFAULT_IS_SHOW_LOADING_PAGE_WHEN_BACK,
                    encryptedRemoteKey(RemoteConfigParams.PARAM_IS_SHOW_SPLASH) to RemoteConfigParams.DEFAULT_IS_SHOW_SPLASH
                ))

            // 获取并激活配置
            firebaseRemoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    isInitialized = true
                    logDebug("Remote Config 初始化成功")
                    // 更新参数管理器中的配置值
                    updateRemoteConfigParams()
                    // 通知外部配置已更新
                    onConfigUpdatedCallback?.invoke()
                } else {
                    logError("Remote Config 初始化失败", task.exception)
                }
            }

        } catch (e: Exception) {
            logError("Remote Config 初始化异常", e)
        }
    }

    /**
     * 等待初始化完成（用于获取配置值时）
     * @return true 如果初始化成功，false 如果超时
     */
    private suspend fun waitForInitialization(): Boolean {
        var waitCycles = 0

        while (!isInitialized && waitCycles < MAX_WAIT_CYCLES) {
            delay(WAIT_INTERVAL)
            waitCycles++
        }

        if (waitCycles >= MAX_WAIT_CYCLES) {
            logError("等待初始化超时，已等待 ${MAX_WAIT_CYCLES * WAIT_INTERVAL}ms")
            return false
        }

        return isInitialized
    }

    /**
     * 获取字符串配置值
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值，如果初始化超时则返回 null
     */
    suspend fun getString(key: String, defaultValue: String = ""): String? {
        if (!waitForInitialization()) {
            logError("初始化超时，无法获取配置 $key")
            return null
        }

        return try {
            val value = firebaseRemoteConfig.getString(encryptedRemoteKey(key)).autoDecryptIfNeeded(currentPackageName())
            if (value.isNotEmpty()) {
                logDebug("获取配置 $key: '$value'")
                value
            } else {
                logDebug("配置 $key 为空，返回默认值: '$defaultValue'")
                defaultValue
            }
        } catch (e: Exception) {
            logError("获取配置 $key 异常", e)
            defaultValue
        }
    }

    /**
     * 获取布尔配置值
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值，如果初始化超时则返回 null
     */
    suspend fun getBoolean(key: String, defaultValue: Boolean = false): Boolean? {
        if (!waitForInitialization()) {
            logError("初始化超时，无法获取配置 $key")
            return null
        }

        return try {
            val value = firebaseRemoteConfig.getBoolean(encryptedRemoteKey(key))
            logDebug("获取配置 $key: $value")
            value
        } catch (e: Exception) {
            logError("获取配置 $key 异常", e)
            defaultValue
        }
    }

    /**
     * 获取整数配置值
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值，如果初始化超时则返回 null
     */
    suspend fun getInt(key: String, defaultValue: Int = 0): Int? {
        if (!waitForInitialization()) {
            logError("初始化超时，无法获取配置 $key")
            return null
        }

        return try {
            val value = firebaseRemoteConfig.getLong(encryptedRemoteKey(key)).toInt()
            logDebug("获取配置 $key: $value")
            value
        } catch (e: Exception) {
            logError("获取配置 $key 异常", e)
            defaultValue
        }
    }

    /**
     * 获取长整数配置值
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值，如果初始化超时则返回 null
     */
    suspend fun getLong(key: String, defaultValue: Long = 0L): Long? {
        if (!waitForInitialization()) {
            logError("初始化超时，无法获取配置 $key")
            return null
        }

        return try {
            val value = firebaseRemoteConfig.getLong(encryptedRemoteKey(key))
            logDebug("获取配置 $key: $value")
            value
        } catch (e: Exception) {
            logError("获取配置 $key 异常", e)
            defaultValue
        }
    }

    /**
     * 获取双精度浮点数配置值
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值，如果初始化超时则返回 null
     */
    suspend fun getDouble(key: String, defaultValue: Double = 0.0): Double? {
        if (!waitForInitialization()) {
            logError("初始化超时，无法获取配置 $key")
            return null
        }

        return try {
            val value = firebaseRemoteConfig.getDouble(encryptedRemoteKey(key))
            logDebug("获取配置 $key: $value")
            value
        } catch (e: Exception) {
            logError("获取配置 $key 异常", e)
            defaultValue
        }
    }

    /**
     * 强制刷新配置
     * @return 是否刷新成功
     */
    suspend fun refresh(): Boolean = suspendCancellableCoroutine { continuation ->
        if (!isInitialized) {
            logWarning("Remote Config 未初始化，无法刷新")
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        logDebug("开始刷新 Remote Config")

        // 临时设置更短的获取间隔
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0)
            .setFetchTimeoutInSeconds(60)
            .build()
        firebaseRemoteConfig.setConfigSettingsAsync(settings)

        firebaseRemoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                logDebug("Remote Config 刷新成功")
                continuation.resume(true)
            } else {
                logError("Remote Config 刷新失败", task.exception)
                continuation.resume(false)
            }
        }
    }

    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized

    /**
     * 获取 FirebaseRemoteConfig 实例（兼容旧代码）
     * @param context 上下文
     * @return FirebaseRemoteConfig 实例，如果未初始化则返回 null
     */
    fun getFirebaseRemoteConfig(): FirebaseRemoteConfig? {
        return if (isInitialized) firebaseRemoteConfig else null
    }

    /**
     * 更新 RemoteConfigParams 中的配置值
     */
    private fun updateRemoteConfigParams() {
        try {
            val isShowLoadingPageWhenBack = firebaseRemoteConfig.getBoolean(encryptedRemoteKey(RemoteConfigParams.PARAM_IS_SHOW_LOADING_PAGE_WHEN_BACK))
            RemoteConfigParams.updateIsShowLoadingPageWhenBack(isShowLoadingPageWhenBack)
            logDebug("更新参数: ${RemoteConfigParams.PARAM_IS_SHOW_LOADING_PAGE_WHEN_BACK} = $isShowLoadingPageWhenBack")

            val isShowSplash = firebaseRemoteConfig.getBoolean(encryptedRemoteKey(RemoteConfigParams.PARAM_IS_SHOW_SPLASH))
            RemoteConfigParams.updateIsShowSplash(isShowSplash)
            logDebug("更新参数: ${RemoteConfigParams.PARAM_IS_SHOW_SPLASH} = $isShowSplash")
        } catch (e: Exception) {
            logError("更新 RemoteConfigParams 失败", e)
        }
    }

    /**
     * 日志输出方法
     */
    private fun logDebug(message: String) {
        if(CoreLogger.isLogEnabled()){
            Log.d(TAG, message)
        }
    }

    private fun logWarning(message: String) {
        if(CoreLogger.isLogEnabled()){
            Log.w(TAG, message)
        }
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        if(CoreLogger.isLogEnabled()){
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        }
    }

    private fun currentPackageName(): String {
        return ProviderContext.getAppContext().packageName
    }

    private fun encryptedRemoteKey(rawKey: String): String {
        return rawKey.autoEncryptIfNeeded(currentPackageName())
    }
}
