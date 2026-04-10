package com.android.common.bill.ads.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.android.common.bill.ads.AdActivityInterceptor
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.protection.AdClickProtectionController

/**
 * 广告模块内容提供者
 * 用于在模块初始化时获取 Context 并初始化 AdConfigManager
 */
class AdModuleProvider : ContentProvider() {
    
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
                AdConfigManager.initialize(ctx)
                AdClickProtectionController.init(ctx)
            } catch (e: Exception) {
                AdLogger.e("AdConfigManager 初始化失败", e)
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
