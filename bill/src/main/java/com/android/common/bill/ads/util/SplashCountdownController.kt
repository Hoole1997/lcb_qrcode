package com.android.common.bill.ads.util

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.android.common.bill.ads.log.AdLogger
import kotlinx.coroutines.*

import androidx.lifecycle.Lifecycle

/**
 * 开屏页兜底倒计时控制器
 * 
 * 核心功能：
 * 1. 监听开屏页生命周期
 * 2. 在不可见时暂停倒计时
 * 3. 在可见时恢复倒计时
 * 4. 提供回调给外部
 * 5. start() 时检查 resume 状态，不在 resume 则等待
 * 
 * 使用方式：
 * ```
 * val controller = SplashCountdownController(
 *     activity = this,
 *     totalSeconds = 5,
 *     onTick = { remaining -> updateUI(remaining) },
 *     onFinish = { navigateToMain() }
 * )
 * controller.start()
 * ```
 */
class SplashCountdownController(
    private val activity: FragmentActivity,
    private val totalSeconds: Int = 5,
    private val onTick: ((remainingSeconds: Int) -> Unit)? = null,
    private val onFinish: (() -> Unit)? = null
) : DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "SplashCountdownController"
    }
    
    // 倒计时状态
    private var remainingMillis: Long = totalSeconds * 1000L
    private var isRunning: Boolean = false
    private var isPaused: Boolean = false
    private var isFinished: Boolean = false
    private var isPendingStart: Boolean = false  // 等待 Resume 后启动
    
    // 协程相关
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countdownJob: Job? = null
    
    // 上次暂停时间（用于计算暂停期间流逝的时间）
    private var lastTickTime: Long = 0L
    
    init {
        // 自动监听 Activity 生命周期
        activity.lifecycle.addObserver(this)
    }
    
    /**
     * 开始倒计时
     * 如果 Activity 不在 Resume 状态，会等待 Resume 后自动启动
     */
    fun start() {
        if (isRunning || isFinished) {
            AdLogger.d("$TAG: Already running or finished, ignoring start()")
            return
        }
        
        // 检查 Activity 是否在 Resume 状态
        val currentState = activity.lifecycle.currentState
        if (!currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            AdLogger.d("$TAG: Activity not resumed (state=$currentState), waiting for onResume...")
            isPendingStart = true
            return
        }
        
        startInternal()
    }
    
    /**
     * 内部启动方法
     */
    private fun startInternal() {
        isPendingStart = false
        
        AdLogger.d("$TAG: Starting countdown, total=${totalSeconds}s")
        isRunning = true
        isPaused = false
        remainingMillis = totalSeconds * 1000L
        
        // 立即触发一次 onTick
        onTick?.invoke(totalSeconds)
        
        startCountdownInternal()
    }
    
    /**
     * 暂停倒计时
     */
    fun pause() {
        if (!isRunning || isPaused || isFinished) {
            return
        }
        
        AdLogger.d("$TAG: Pausing countdown, remaining=${remainingMillis}ms")
        isPaused = true
        countdownJob?.cancel()
        countdownJob = null
    }
    
    /**
     * 恢复倒计时
     */
    fun resume() {
        if (!isRunning || !isPaused || isFinished) {
            return
        }
        
        AdLogger.d("$TAG: Resuming countdown, remaining=${remainingMillis}ms")
        isPaused = false
        startCountdownInternal()
    }
    
    /**
     * 停止并取消倒计时（不触发 onFinish）
     */
    fun cancel() {
        AdLogger.d("$TAG: Cancelling countdown")
        isRunning = false
        isPaused = false
        isPendingStart = false
        countdownJob?.cancel()
        countdownJob = null
        activity.lifecycle.removeObserver(this)
    }
    
    /**
     * 立即完成倒计时（触发 onFinish）
     */
    fun finishImmediately() {
        if (isFinished) {
            return
        }
        
        AdLogger.d("$TAG: Finishing immediately")
        isFinished = true
        isRunning = false
        countdownJob?.cancel()
        countdownJob = null
        activity.lifecycle.removeObserver(this)
        
        onFinish?.invoke()
    }
    
    /**
     * 获取剩余秒数
     */
    fun getRemainingSeconds(): Int {
        return ((remainingMillis + 999) / 1000).toInt().coerceAtLeast(0)
    }
    
    /**
     * 是否正在运行
     */
    fun isCountdownRunning(): Boolean = isRunning && !isPaused
    
    /**
     * 是否已暂停
     */
    fun isCountdownPaused(): Boolean = isPaused
    
    /**
     * 是否已完成
     */
    fun isCountdownFinished(): Boolean = isFinished
    
    // ============ Lifecycle Observer ============
    
    override fun onResume(owner: LifecycleOwner) {
        AdLogger.d("$TAG: Activity onResume")
        
        // 如果有等待启动的倒计时，现在启动
        if (isPendingStart) {
            startInternal()
            return
        }
        
        // 如果正在运行但暂停了，恢复倒计时
        if (isRunning && isPaused) {
            resume()
        }
    }
    
    override fun onPause(owner: LifecycleOwner) {
        AdLogger.d("$TAG: Activity onPause")
        if (isRunning && !isPaused) {
            pause()
        }
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        AdLogger.d("$TAG: Activity onDestroy, cleaning up")
        cancel()
        scope.cancel()
    }
    
    // ============ Internal ============
    
    private fun startCountdownInternal() {
        countdownJob?.cancel()
        lastTickTime = System.currentTimeMillis()
        
        countdownJob = scope.launch {
            while (remainingMillis > 0 && isActive) {
                delay(1000L)
                
                if (!isActive || isPaused) {
                    break
                }
                
                // 计算实际流逝的时间
                val now = System.currentTimeMillis()
                val elapsed = now - lastTickTime
                lastTickTime = now
                
                remainingMillis -= elapsed
                
                if (remainingMillis <= 0) {
                    remainingMillis = 0
                    AdLogger.d("$TAG: Countdown finished")
                    isFinished = true
                    isRunning = false
                    activity.lifecycle.removeObserver(this@SplashCountdownController)
                    onTick?.invoke(0)
                    onFinish?.invoke()
                } else {
                    val remaining = getRemainingSeconds()
                    AdLogger.d("$TAG: Tick, remaining=${remaining}s")
                    onTick?.invoke(remaining)
                }
            }
        }
    }
}
