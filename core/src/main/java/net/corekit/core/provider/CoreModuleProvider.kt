package net.corekit.core.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import net.corekit.core.log.CoreLogger
import net.corekit.core.utils.ConfigRemoteManager

/**
 * Core模块的ContentProvider
 * 用于在应用启动时自动初始化Core模块
 * 通过ContentProvider的onCreate方法获取ApplicationContext
 */
class CoreModuleProvider : ContentProvider() {

    companion object {
        private var applicationContext: android.content.Context? = null

        /**
         * 获取应用上下文
         * @return ApplicationContext，如果未初始化则返回null
         */
        fun getApplicationContext(): android.content.Context? = applicationContext
    }

    override fun onCreate(): Boolean {
        applicationContext = context?.applicationContext
        applicationContext?.let { ctx ->
            try {
                CoreLogger.d("CoreModuleProvider 初始化完成，Context 已准备就绪")
                ConfigRemoteManager.initialize()
            } catch (e: Exception) {
                CoreLogger.e("CoreModuleProvider 初始化失败", e)
            }
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
