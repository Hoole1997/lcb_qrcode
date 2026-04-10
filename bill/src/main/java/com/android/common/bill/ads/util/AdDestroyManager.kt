package com.android.common.bill.ads.util

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.android.common.bill.ads.log.AdLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 广告生命周期销毁管理器
 * 
 * 核心功能：
 * 1. 监听 Activity 生命周期的 onDestroy
 * 2. 在 Activity 销毁时清理与该 Activity 相关的广告展示资源
 * 3. 不影响单例控制器的预缓存广告
 * 
 * 使用方式：
 * ```
 * // 在 showAd 时注册
 * AdDestroyManager.instance.register(activity) {
 *     // 清理展示相关资源
 *     showContinuation?.cancel()
 *     showContinuation = null
 * }
 * ```
 */
class AdDestroyManager private constructor() {
    
    companion object {
        private const val TAG = "AdDestroyManager"
        
        val instance by lazy { AdDestroyManager() }
    }
    
    // 按 Activity 跟踪需要清理的回调列表
    // Key: Activity hashCode, Value: List of cleanup actions
    private val cleanupRegistry = ConcurrentHashMap<Int, CopyOnWriteArrayList<() -> Unit>>()
    
    // 已注册的 LifecycleObserver
    private val observerRegistry = ConcurrentHashMap<Int, DefaultLifecycleObserver>()
    
    /**
     * 注册清理回调
     * 在 Activity onDestroy 时会自动调用 cleanupAction
     * 
     * @param activity 关联的 Activity
     * @param cleanupAction 清理回调
     */
    fun register(
        activity: FragmentActivity,
        cleanupAction: () -> Unit
    ) {
        val key = System.identityHashCode(activity)
        
        // 确保该 Activity 有清理列表
        val actions = cleanupRegistry.getOrPut(key) { CopyOnWriteArrayList() }
        actions.add(cleanupAction)
        
        // 确保监听了该 Activity 的生命周期
        ensureLifecycleObserver(activity, key)
        
        AdLogger.d("$TAG: Registered cleanup for ${activity.javaClass.simpleName}, total actions: ${actions.size}")
    }
    
    /**
     * 清理指定 Activity 的所有广告资源
     */
    fun cleanupAll(activity: FragmentActivity) {
        val key = System.identityHashCode(activity)
        
        cleanupRegistry[key]?.let { actions ->
            AdLogger.d("$TAG: Cleanup all for ${activity.javaClass.simpleName}, count=${actions.size}")
            
            actions.forEach { action ->
                try {
                    action.invoke()
                } catch (e: Exception) {
                    AdLogger.e("$TAG: Cleanup error", e)
                }
            }
            
            actions.clear()
        }
        
        cleanupRegistry.remove(key)
        removeLifecycleObserver(activity, key)
    }
    
    // ============ Internal ============
    
    private fun ensureLifecycleObserver(activity: FragmentActivity, key: Int) {
        if (observerRegistry.containsKey(key)) {
            return
        }
        
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                AdLogger.d("$TAG: Activity onDestroy detected, triggering cleanup")
                
                // 执行所有注册的清理回调
                cleanupRegistry[key]?.let { actions ->
                    actions.forEach { action ->
                        try {
                            action.invoke()
                        } catch (e: Exception) {
                            AdLogger.e("$TAG: Cleanup error", e)
                        }
                    }
                    actions.clear()
                }
                
                cleanupRegistry.remove(key)
                observerRegistry.remove(key)
                owner.lifecycle.removeObserver(this)
            }
        }
        
        observerRegistry[key] = observer
        activity.lifecycle.addObserver(observer)
    }
    
    private fun removeLifecycleObserver(activity: FragmentActivity, key: Int) {
        observerRegistry[key]?.let { observer ->
            activity.lifecycle.removeObserver(observer)
            observerRegistry.remove(key)
        }
    }
}
