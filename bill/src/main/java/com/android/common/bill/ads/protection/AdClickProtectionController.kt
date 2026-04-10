package com.android.common.bill.ads.protection

import android.content.Context
import com.blankj.utilcode.util.ToastUtils
import com.android.common.bill.ads.BillCryptoScope
import com.google.gson.Gson
import net.corekit.core.controller.ChannelUserController
import net.corekit.core.ext.DataStoreStringDelegate
import net.corekit.core.ext.autoDecryptIfNeeded
import net.corekit.core.report.ReportDataManager
import net.corekit.core.utils.ConfigRemoteManager
import com.android.common.bill.ads.interceptor.GlobalAdSwitchInterceptor
import com.android.common.bill.ads.log.AdLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 广告重复点击保护控制器
 * 用于识别同一广告当天被持续点击多次的异常行为并上报
 * 使用 Room 数据库持久化存储，0点自动刷新
 */
object AdClickProtectionController {

    private const val TAG = "重复点击防护"

    // 本地配置文件名
    private const val CONFIG_FILE = "ad_click_protection_config.json"

    // 远程配置 Key
    private const val REMOTE_CONFIG_KEY = "adClickProtectionConfig"

    // 持久化远程配置（使用属性委托）
    private var configJsonFromRemote by DataStoreStringDelegate("adClickProtectionConfigRemote", "")

    // 配置数据（包含 natural 和 paid）
    private var configData: AdClickProtectionConfigData? = null

    // Room 数据库
    private var database: AdClickProtectionDatabase? = null
    private val dao: AdClickRecordDao?
        get() = database?.adClickRecordDao()

    private val gson = Gson()

    /**
     * 初始化
     */
    fun init(context: Context) {
        // 初始化数据库
        database = AdClickProtectionDatabase.getInstance(context)
        
        // 加载本地配置（优先使用持久化的远程配置）
        loadLocalConfig(context)
        
        // 尝试加载远程配置
        loadRemoteConfig()
        
        // 检查封禁状态和0点刷新
        CoroutineScope(Dispatchers.IO).launch {
            checkAndRestoreBlockStatus()
        }
        
        AdLogger.d("$TAG 初始化完成，当前渠道: ${ChannelUserController.getCurrentChannel()}, 配置: protectionEnabled=${getCurrentConfig().protectionEnabled}, threshold=${getCurrentConfig().threshold}")
    }

    /**
     * 检查封禁状态和0点刷新
     */
    private suspend fun checkAndRestoreBlockStatus() {
        val today = LocalDate.now().toString()
        try {
            val blockStatus = dao?.getBlockStatus()
            val isNewDay = blockStatus == null || blockStatus.lastResetDate != today
            val isBlockedToday = blockStatus?.isBlocked == true && blockStatus.blockedDate == today
            
            // 新的一天，清空点击记录并更新 lastResetDate
            if (isNewDay) {
                dao?.deleteAll()
                dao?.insertOrUpdateBlockStatus(
                    AdBlockStatusEntity(
                        id = 1,
                        isBlocked = isBlockedToday,
                        blockedDate = if (isBlockedToday) today else "",
                        lastResetDate = today
                    )
                )
            }
            
            // 根据封禁状态处理
            if (isBlockedToday) {
                GlobalAdSwitchInterceptor.disableGlobalAd()
                AdLogger.w("$TAG 当日已被封禁，广告停播中")
            } else {
                GlobalAdSwitchInterceptor.enableGlobalAd()
                AdLogger.d("$TAG 无封禁记录，广告正常展示")
            }
        } catch (e: Exception) {
            AdLogger.e("$TAG 检查封禁状态失败", e)
        }
    }

    /**
     * 加载本地配置文件（优先使用持久化的远程配置）
     */
    private fun loadLocalConfig(context: Context) {
        try {
            val json = (configJsonFromRemote.orEmpty().takeIf { it.isNotEmpty() }
                ?: context.assets.open(CONFIG_FILE).bufferedReader().use { it.readText() }
                ).autoDecryptIfNeeded(BillCryptoScope.STATIC_ASSET_PACKAGE_NAME)
            configData = gson.fromJson(json, AdClickProtectionConfigData::class.java)
            AdLogger.d("$TAG 加载本地配置成功: $configData")
        } catch (e: Exception) {
            AdLogger.e("$TAG 加载本地配置失败，使用默认配置", e)
            configData = AdClickProtectionConfigData()
        }
    }

    /**
     * 加载远程配置
     */
    private fun loadRemoteConfig() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val remoteJson = ConfigRemoteManager.getString(REMOTE_CONFIG_KEY, "")
                if (!remoteJson.isNullOrBlank()) {
                    configData = gson.fromJson(remoteJson, AdClickProtectionConfigData::class.java)
                    configJsonFromRemote = remoteJson
                    AdLogger.d("$TAG 远程配置更新成功: $configData")
                }
            } catch (e: Exception) {
                AdLogger.e("$TAG 加载远程配置失败，使用本地配置", e)
            }
        }
    }

    /**
     * 获取当前渠道的配置
     */
    private fun getCurrentConfig(): AdClickProtectionConfig {
        val data = configData ?: AdClickProtectionConfigData()
        return try {
            when (ChannelUserController.getCurrentChannel()) {
                ChannelUserController.UserChannelType.NATURAL -> data.natural
                ChannelUserController.UserChannelType.PAID -> data.paid
            }
        } catch (e: Exception) {
            AdLogger.e("$TAG 获取用户渠道失败，使用默认配置", e)
            data.natural
        }
    }

    /**
     * 记录广告点击
     * @param adUniqueId 广告展示时生成的唯一 ID（每次展示生成新的 UUID + 广告位 + 创意ID）
     */
    fun recordClick(adUniqueId: String) {
        if (!isEnabled()) {
            AdLogger.d("$TAG 点击保护已禁用")
            return
        }

        val threshold = getThreshold()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 尝试增加点击次数（使用 adUniqueId 作为唯一标识）
                val updatedRows = dao?.incrementClickCount(adUniqueId) ?: 0
                
                if (updatedRows == 0) {
                    // 不存在记录，插入新记录（点击数为1）
                    dao?.insertOrUpdate(AdClickRecordEntity(adIdentifier = adUniqueId, clickCount = 1))
                }
                
                // 查询当前点击数
                val newClickCount = dao?.getRecord(adUniqueId)?.clickCount ?: 1
                
                AdLogger.d("$TAG 广告点击记录: adUniqueId=$adUniqueId, 当天点击次数: $newClickCount/$threshold")

                // 第2次及以上点击都上报埋点
                if (newClickCount >= 2) {
                    reportClickEvent(adUniqueId, newClickCount)
                }

                // 达到阈值触发保护
                if (newClickCount >= threshold) {
                    triggerProtection(adUniqueId, newClickCount)
                }
            } catch (e: Exception) {
                AdLogger.e("$TAG 记录点击失败", e)
            }
        }
    }

    /**
     * 上报重复点击埋点（第2次及以上）
     */
    private fun reportClickEvent(
        adUniqueId: String,
        clickCount: Int
    ) {
        val params = mapOf(
            "ad_unique_id" to adUniqueId,
            "click_count" to clickCount,
            "threshold" to getThreshold()
        )

        ReportDataManager.reportData("ad_repeat_click", params)
        AdLogger.d("$TAG 上报重复点击埋点: $params")
    }

    /**
     * 触发保护：禁用广告 + 退出APP
     */
    private suspend fun triggerProtection(
        adUniqueId: String,
        clickCount: Int
    ) {
        val today = LocalDate.now().toString()
        
        AdLogger.w("$TAG 触发保护: adUniqueId=$adUniqueId, clickCount=$clickCount")

        // 1. 禁用全局广告
        GlobalAdSwitchInterceptor.disableGlobalAd()
        
        // 2. 记录封禁日期到数据库
        dao?.insertOrUpdateBlockStatus(
            AdBlockStatusEntity(
                id = 1,
                isBlocked = true,
                blockedDate = today
            )
        )
        
        AdLogger.w("$TAG 已禁用全局广告，当日广告停播")

        // 3. 上报异常点击埋点
        val params = mapOf(
            "ad_unique_id" to adUniqueId,
            "click_count" to clickCount,
            "threshold" to getThreshold()
        )

        ReportDataManager.reportData("ad_abnormal_click", params)
        AdLogger.w("$TAG 已上报异常点击: $params")
        AdLogger.w("$TAG 即将关闭App进程...")

        // 4. 显示 Toast 并退出 APP
        withContext(Dispatchers.Main) {
            ToastUtils.showShort("Abnormal click detected. App will exit.")
            delay(3000)
            killProcess()
        }
    }

    /**
     * 关闭当前进程
     */
    private fun killProcess() {
        try {
            android.os.Process.killProcess(android.os.Process.myPid())
        } catch (e: Exception) {
            AdLogger.e("$TAG 关闭进程失败", e)
        }
    }

    /**
     * 是否启用点击保护
     */
    private fun isEnabled(): Boolean = getCurrentConfig().protectionEnabled

    /**
     * 获取点击次数阈值
     */
    private fun getThreshold(): Int = getCurrentConfig().threshold

    /**
     * 清理所有记录
     */
    fun clearAll() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dao?.deleteAll()
                AdLogger.d("$TAG 已清理所有点击记录")
            } catch (e: Exception) {
                AdLogger.e("$TAG 清理记录失败", e)
            }
        }
    }
}
