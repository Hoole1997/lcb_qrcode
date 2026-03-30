package net.corekit.metrics.revenue

import android.content.Context
import com.adjust.sdk.AdjustAdRevenue
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import net.corekit.metrics.adjust.AdjustTracker
import net.corekit.metrics.log.MetricsLogger
import net.corekit.metrics.provider.MetricsModuleProvider
import net.corekit.core.ads.RevenueAdData
import net.corekit.core.ads.RevenueAdReporter
import net.corekit.core.utils.ConfigRemoteManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import java.io.IOException

/**
 * 收益配置项数据类
 */
data class RevenueConfigItem(
    @SerializedName("name")
    val name: String,
    @SerializedName("rate")
    val rate: Int
)

/**
 * Adjust广告收益上报器实现
 * 将广告收益数据上报到Adjust平台
 */
class AdjustRevenueReporter : RevenueAdReporter {
    
    private val gson = Gson()
    private var revenueConfigs: List<RevenueConfigItem> = emptyList()
    private var isConfigLoaded = false
    
    /**
     * 构造函数，异步获取Firebase Remote Config配置
     */
    init {
        loadRevenueConfig()
    }
    
    /**
     * 异步加载收益配置
     */
    private fun loadRevenueConfig() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 首先尝试从Firebase Remote Config获取
                val remoteConfigJson = ConfigRemoteManager.getString("rev_adj", "")
                
                if (remoteConfigJson != null && remoteConfigJson.isNotEmpty()) {
                    // 使用Remote Config的配置
                    revenueConfigs = gson.fromJson(remoteConfigJson, Array<RevenueConfigItem>::class.java).toList()
                    MetricsLogger.d("从Remote Config加载收益配置成功: $revenueConfigs")
                } else {
                    // 如果Remote Config没有配置，从assets文件读取
                    val assetsConfigJson = loadConfigFromAssets()
                    if (assetsConfigJson != null) {
                        revenueConfigs = gson.fromJson(assetsConfigJson, Array<RevenueConfigItem>::class.java).toList()
                        MetricsLogger.d("从assets加载收益配置成功: $revenueConfigs")
                    } else {
                        // 如果assets文件也读取失败，使用硬编码默认配置
                        revenueConfigs = getDefaultConfig()
                        MetricsLogger.d("使用硬编码默认收益配置: $revenueConfigs")
                    }
                }
                
                isConfigLoaded = true
                
            } catch (e: Exception) {
                MetricsLogger.e("加载收益配置失败", e)
                // 使用默认配置
                revenueConfigs = getDefaultConfig()
                isConfigLoaded = true
            }
        }
    }
    
    /**
     * 从assets文件读取配置
     * @return JSON字符串，读取失败返回null
     */
    private fun loadConfigFromAssets(): String? {
        return try {
            val context = MetricsModuleProvider.getApplicationContext()
            if (context != null) {
                context.assets.open("revenue_config.json").use { inputStream ->
                    inputStream.bufferedReader().use { reader ->
                        reader.readText()
                    }
                }
            } else {
                MetricsLogger.w("无法获取Context，跳过从assets读取配置")
                null
            }
        } catch (e: IOException) {
            MetricsLogger.e("从assets读取revenue_config.json失败", e)
            null
        } catch (e: Exception) {
            MetricsLogger.e("读取assets配置异常", e)
            null
        }
    }
    
    /**
     * 获取默认配置
     * @return 默认配置列表
     */
    private fun getDefaultConfig(): List<RevenueConfigItem> {
        return listOf(
            RevenueConfigItem("applovin_max_sdk", 70),
            RevenueConfigItem("ironsource_sdk", 30)
        )
    }
    
    /**
     * 根据随机数选择收益配置
     * @return 选中的配置项名称
     */
    private fun selectRevenueConfig(): String {
        if (!isConfigLoaded || revenueConfigs.isEmpty()) {
            MetricsLogger.w("收益配置未加载或为空，使用默认值")
            return "admob_sdk"
        }
        
        val randomValue = Random.nextInt(0, 101) // 0-100随机数
        var cumulativeRate = 0
        
        for (config in revenueConfigs) {
            cumulativeRate += config.rate
            if (randomValue <= cumulativeRate) {
                MetricsLogger.d("随机数: $randomValue, 选中配置: ${config.name}")
                return config.name
            }
        }
        
        // 如果没有匹配到，返回最后一个配置
        val lastConfig = revenueConfigs.last()
        MetricsLogger.d("随机数: $randomValue, 未匹配到配置，使用最后一个: ${lastConfig.name}")
        return lastConfig.name
    }

    /**
     * 异步等待Adjust SDK初始化
     * @return 是否初始化成功
     */
    private suspend fun waitForAdjustInit(): Boolean {
        var attempts = 0
        val maxAttempts = 10 // 最多尝试10次
        val delayMs = 100L // 每次等待100ms

        while (attempts < maxAttempts) {
            if (AdjustTracker.checkInitialized()) {
                return true
            }
            
            MetricsLogger.d("Adjust SDK未就绪，等待中... (${attempts + 1}/$maxAttempts)")
            delay(delayMs)
            attempts++
        }
        
        MetricsLogger.e("等待Adjust SDK初始化超时")
        return false
    }
    
    override fun reportAdRevenue(adRevenueData: RevenueAdData) {
        try {
            // 先检查Adjust SDK是否已初始化
            var isInitialized = AdjustTracker.checkInitialized()
            
            // 如果没有初始化，则异步等待阻塞获取
            if (!isInitialized) {
                MetricsLogger.d("Adjust SDK未就绪，开始异步等待...")
                isInitialized = runBlocking {
                    waitForAdjustInit()
                }
            }
            
            if (!isInitialized) {
                MetricsLogger.w("无法等待Adjust SDK初始化完成，跳过广告收益上报")
                return
            }
            
            // 根据随机数选择收益配置
            val selectedConfigName = selectRevenueConfig()
            
            // 创建Adjust广告收益对象，使用选中的配置名称
            val adjustAdRevenue = AdjustAdRevenue(selectedConfigName)
            
            // 设置收益数据
            adjustAdRevenue.setRevenue(
                adRevenueData.revenue.value,
                adRevenueData.revenue.currencyCode
            )
            
            // 设置网络类型
            adjustAdRevenue.setAdRevenueNetwork(adRevenueData.adRevenueNetwork)
            
            // 设置广告相关参数
            adjustAdRevenue.setAdRevenueUnit(adRevenueData.adRevenueUnit)
            adjustAdRevenue.setAdRevenuePlacement(adRevenueData.adRevenuePlacement)
            
            // 发送广告收益数据
            com.adjust.sdk.Adjust.trackAdRevenue(adjustAdRevenue)
            
            MetricsLogger.d("广告收益数据已上报到Adjust: $adRevenueData, 使用配置: $selectedConfigName")
            
        } catch (e: Exception) {
            MetricsLogger.e("上报广告收益数据到Adjust失败", e)
        }
    }
}
