package net.corekit.metrics.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import net.corekit.metrics.adjust.AdjustTracker
import net.corekit.metrics.data.FirebaseReporter
import net.corekit.metrics.log.MetricsLogger
import net.corekit.metrics.data.ThinkingReporter
import net.corekit.metrics.revenue.AdjustRevenueReporter
import net.corekit.metrics.revenue.FirebaseRevenueReporter
import net.corekit.core.ads.RevenueAdManager
import net.corekit.core.report.ReportDataManager

/**
 * 分析统计模块内容提供者
 * 用于在模块初始化时获取 Context 并初始化 AdjustController
 */
class MetricsModuleProvider : ContentProvider() {
    
    companion object {
        private var applicationContext: android.content.Context? = null
        
        /**
         * 获取应用上下文
         */
        fun getApplicationContext(): android.content.Context? = applicationContext
    }
    
    override fun onCreate(): Boolean {
        applicationContext = context?.applicationContext
        applicationContext?.let { ctx ->
            try {
                // 注入上报具体实现
                // 收益：adjust、firebase
                // 数据：thinkingData、firebase
                RevenueAdManager.setReporters(
                    listOf(
                        AdjustRevenueReporter(),
                        FirebaseRevenueReporter()
                    )
                )
                ReportDataManager.setReporters(listOf(ThinkingReporter(), FirebaseReporter()))

                // 初始化数数SDK控制器
                ThinkingReporter.init(ctx)

                // 初始化Adjust控制器
                AdjustTracker.init(ctx)
                
                MetricsLogger.d("MetricsModuleProvider 初始化完成")
            } catch (e: Exception) {
                MetricsLogger.e("MetricsModuleProvider 初始化失败", e)
            }
        }

        return true
    }
    
    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null
    
    override fun getType(uri: Uri): String? = null
    
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0
}
