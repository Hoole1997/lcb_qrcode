package net.corekit.core.utils

import android.content.Context
import net.corekit.core.provider.CoreModuleProvider
import net.corekit.core.log.CoreLogger

/**
 * Context 提供者工具类
 * 统一管理应用上下文的获取，避免直接依赖 Utils.getApp()
 */
object ProviderContext {
    
    /**
     * 获取应用上下文
     * 优先使用 CoreModuleProvider，如果未初始化则尝试其他方式
     * @return 应用上下文，如果获取失败则抛出异常
     */
    fun getAppContext(): Context {
        // 优先使用 CoreModuleProvider
        CoreModuleProvider.getApplicationContext()?.let { context ->
            return context
        }
        
        // 如果 CoreModuleProvider 还未初始化，记录警告并抛出异常
        CoreLogger.w("CoreModuleProvider 尚未初始化，无法获取应用上下文")
        throw IllegalStateException("CoreModuleProvider 尚未初始化，请确保 ContentProvider 已正确注册")
    }
    
    /**
     * 安全获取应用上下文
     * 如果获取失败返回 null，不会抛出异常
     * @return 应用上下文，如果获取失败返回 null
     */
    fun getAppContextOrNull(): Context? {
        return try {
            getAppContext()
        } catch (e: Exception) {
            CoreLogger.e("获取应用上下文失败", e)
            null
        }
    }
    
    /**
     * 检查 Context 是否已准备就绪
     * @return true 如果 Context 可用，false 如果不可用
     */
    fun isContextReady(): Boolean {
        return CoreModuleProvider.getApplicationContext() != null
    }
}
