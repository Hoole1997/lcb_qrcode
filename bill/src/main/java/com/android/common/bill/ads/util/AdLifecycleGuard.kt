package com.android.common.bill.ads.util

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.log.AdLogger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * 广告生命周期守卫
 * 
 * 核心能力：
 * 1. Resume 检查 - 只有 Activity 处于 Resume 状态才执行 show
 * 2. 等待恢复 - 如果不是 Resume，挂起等待恢复
 * 3. 销毁清理 - 等待过程中 Activity 销毁，取消等待并清理资源
 * 
 * 注意：多次快速调用会覆盖之前的等待
 */
class AdLifecycleGuard private constructor() {
    
    companion object {
        private const val TAG = "AdLifecycleGuard"
        
        val instance by lazy { AdLifecycleGuard() }
    }
    
    /**
     * 展示上下文
     */
    private data class ShowContext(
        var resumeContinuation: CancellableContinuation<AdResult<Unit>>? = null,
        var showContinuation: CancellableContinuation<AdResult<Unit>>? = null,
        var callback: (() -> Unit)? = null,
        var lifecycleObserver: DefaultLifecycleObserver? = null
    )
    
    // 按 Activity hashCode 跟踪
    private val showContexts = ConcurrentHashMap<Int, ShowContext>()
    
    /**
     * 等待 Activity 进入 Resume 状态后执行展示
     * 
     * @param activity 目标 Activity
     * @return AdResult.Success - 可以展示
     *         AdResult.Failure - Activity 已销毁，不可展示
     */
    suspend fun awaitResumeOrCancel(activity: FragmentActivity): AdResult<Unit> {
        val key = System.identityHashCode(activity)
        val currentState = activity.lifecycle.currentState
        
        AdLogger.d("$TAG: awaitResumeOrCancel called, activity=${activity.javaClass.simpleName}, state=$currentState")
        
        // 已销毁，直接失败
        if (currentState == Lifecycle.State.DESTROYED) {
            AdLogger.w("$TAG: Activity already destroyed")
            return AdResult.Failure(AdException(code = -1, message = "Activity已销毁"))
        }
        
        // 已经是 Resume 状态，直接成功
        if (currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            AdLogger.d("$TAG: Activity already resumed, can show ad")
            ensureLifecycleObserver(activity, key)
            return AdResult.Success(Unit)
        }
        
        // 否则挂起等待 Resume
        AdLogger.d("$TAG: Activity not resumed, waiting for onResume...")
        return suspendCancellableCoroutine { continuation ->
            // 取消之前的等待（覆盖模式）
            cancelPreviousWait(key)
            
            val context = showContexts.getOrPut(key) { ShowContext() }
            context.resumeContinuation = continuation
            
            // 监听生命周期
            val observer = object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    AdLogger.d("$TAG: onResume triggered, resuming show")
                    showContexts[key]?.resumeContinuation?.let {
                        if (it.isActive) {
                            it.resume(AdResult.Success(Unit))
                        }
                    }
                    showContexts[key]?.resumeContinuation = null
                }
                
                override fun onDestroy(owner: LifecycleOwner) {
                    AdLogger.d("$TAG: onDestroy triggered, canceling and cleanup")
                    cleanupForKey(key, "Activity已销毁")
                    owner.lifecycle.removeObserver(this)
                }
            }
            
            context.lifecycleObserver = observer
            activity.lifecycle.addObserver(observer)
            
            continuation.invokeOnCancellation {
                AdLogger.d("$TAG: Continuation cancelled")
                showContexts[key]?.resumeContinuation = null
            }
        }
    }
    
    /**
     * 注册展示上下文，用于 onDestroy 时清理
     * 
     * @param activity 展示的 Activity
     * @param continuation 展示的协程 Continuation
     * @param callback 可选的回调（如激励广告的 rewardCallback）
     */
    fun registerShowContext(
        activity: FragmentActivity,
        continuation: CancellableContinuation<AdResult<Unit>>?,
        callback: (() -> Unit)? = null
    ) {
        val key = System.identityHashCode(activity)
        val context = showContexts.getOrPut(key) { ShowContext() }
        context.showContinuation = continuation
        context.callback = callback
        
        ensureLifecycleObserver(activity, key)
        
        AdLogger.d("$TAG: Registered show context for ${activity.javaClass.simpleName}")
    }
    
    /**
     * 清理指定 Activity 相关的展示资源
     */
    fun cleanupForActivity(activity: Activity) {
        val key = System.identityHashCode(activity)
        cleanupForKey(key, "手动清理")
    }
    
    /**
     * 取消指定 Activity 的展示（如果正在等待或展示中）
     * 
     * @return true 如果有活动的展示被取消
     */
    fun cancelShowIfActive(activity: Activity): Boolean {
        val key = System.identityHashCode(activity)
        val context = showContexts[key] ?: return false
        
        var cancelled = false
        
        context.resumeContinuation?.let {
            if (it.isActive) {
                it.resume(AdResult.Failure(AdException(code = -2, message = "展示被取消")))
                cancelled = true
            }
        }
        
        context.showContinuation?.let {
            if (it.isActive) {
                it.resume(AdResult.Failure(AdException(code = -2, message = "展示被取消")))
                cancelled = true
            }
        }
        
        if (cancelled) {
            AdLogger.d("$TAG: Cancelled active show for ${activity.javaClass.simpleName}")
        }
        
        return cancelled
    }
    
    /**
     * 检查 Activity 是否处于可展示状态
     */
    fun isActivityResumed(activity: FragmentActivity): Boolean {
        return activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }
    
    /**
     * 检查 Activity 是否已销毁
     */
    fun isActivityDestroyed(activity: FragmentActivity): Boolean {
        return activity.lifecycle.currentState == Lifecycle.State.DESTROYED
    }
    
    // ============ Private Methods ============
    
    private fun ensureLifecycleObserver(activity: FragmentActivity, key: Int) {
        val context = showContexts.getOrPut(key) { ShowContext() }
        
        if (context.lifecycleObserver == null) {
            val observer = object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    AdLogger.d("$TAG: Activity onDestroy, cleaning up resources")
                    cleanupForKey(key, "Activity已销毁")
                    owner.lifecycle.removeObserver(this)
                }
            }
            context.lifecycleObserver = observer
            activity.lifecycle.addObserver(observer)
        }
    }
    
    private fun cancelPreviousWait(key: Int) {
        showContexts[key]?.resumeContinuation?.let {
            if (it.isActive) {
                AdLogger.d("$TAG: Cancelling previous wait (覆盖模式)")
                it.resume(AdResult.Failure(AdException(code = -3, message = "被新的展示请求覆盖")))
            }
        }
    }
    
    private fun cleanupForKey(key: Int, reason: String) {
        showContexts[key]?.let { context ->
            // 取消等待 Resume 的协程
            context.resumeContinuation?.let {
                if (it.isActive) {
                    it.resume(AdResult.Failure(AdException(code = -1, message = reason)))
                }
            }
            
            // 取消展示中的协程
            context.showContinuation?.let {
                if (it.isActive) {
                    it.resume(AdResult.Failure(AdException(code = -1, message = reason)))
                }
            }
            
            // 清理回调
            context.callback = null
            context.resumeContinuation = null
            context.showContinuation = null
            
            AdLogger.d("$TAG: Cleaned up resources for key=$key, reason=$reason")
        }
        
        showContexts.remove(key)
    }
}
