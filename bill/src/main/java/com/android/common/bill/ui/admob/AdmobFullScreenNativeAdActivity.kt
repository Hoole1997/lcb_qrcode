package com.android.common.bill.ui.admob

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.BillConfig
import com.android.common.bill.R
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.admob.AdmobFullScreenNativeAdController
import com.android.common.bill.ads.admob.AdMobManager
import com.android.common.bill.ads.log.AdLogger
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 全屏原生广告Activity
 * 展示全屏的原生广告内容，通常用于应用启动或重要操作前
 */
class AdmobFullScreenNativeAdActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AdmobFullScreenNativeAdActivity"

        /**
         * 启动全屏原生广告Activity
         * @param activity 启动Activity
         * @return AdResult<Unit> 广告显示结果
         */
        private var pendingSessionId: String = ""

        suspend fun start(
            activity: Activity,
            sessionId: String = "",
            onAdDisplayedCallback: ((FragmentActivity) -> Unit)? = null
        ): AdResult<Unit> {
            return suspendCancellableCoroutine { continuation ->
                pendingSessionId = sessionId
                onAdDisplayed = onAdDisplayedCallback

                val intent = Intent(activity, AdmobFullScreenNativeAdActivity::class.java)
                activity.startActivity(intent)
                activity.overridePendingTransition(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                )

                // 存储continuation以便在Activity中调用
                AdmobFullScreenNativeAdActivity.continuation = continuation
            }
        }

        // 用于存储continuation的变量
        @Volatile
        private var continuation: kotlinx.coroutines.CancellableContinuation<AdResult<Unit>>? = null
        @Volatile
        private var onAdDisplayed: ((FragmentActivity) -> Unit)? = null

        private fun consumeOnAdDisplayedCallback(): ((FragmentActivity) -> Unit)? {
            val callback = onAdDisplayed
            onAdDisplayed = null
            return callback
        }

        /**
         * 设置结果并恢复continuation
         */
        fun setResult(result: AdResult<Unit>) {
            continuation?.let { cont ->
                if (cont.isActive) {
                    cont.resume(result)
                }
            }
            continuation = null
            onAdDisplayed = null
        }
    }

    private val fullScreenNativeController = AdMobManager.Controllers.fullScreenNative

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.apply {
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

            @Suppress("DEPRECATION")
            navigationBarColor = Color.TRANSPARENT
        }
        setContentView(R.layout.activity_admob_full_screen_native_ad)
        loadAndShowFullScreenNativeAd()
    }

    /**
     * 加载并显示全屏原生广告
     */
    private fun loadAndShowFullScreenNativeAd() {
        lifecycleScope.launch {
            try {
                when (val result = fullScreenNativeController.showAdInContainer(
                    context = this@AdmobFullScreenNativeAdActivity,
                    container = findViewById<ViewGroup>(R.id.adContainer),
                    lifecycleOwner = this@AdmobFullScreenNativeAdActivity,
                    adUnitId = BillConfig.admob.fullNativeId,
                    sessionId = pendingSessionId,
                    onAdDisplayed = { notifyAdDisplayedIfNeeded() }
                )) {
                    is AdResult.Success -> {
                        findViewById<View>(R.id.rl_top_buttons).apply {
                            isVisible = true
                            findViewById<View>(R.id.btn_close).setOnClickListener {
                                AdmobFullScreenNativeAdController.getInstance().closeEvent( adUnitId = BillConfig.admob.fullNativeId)
                                closeAdAndFinish()
                            }
                        }
                        AdLogger.d("全屏原生广告页面加载成功")
                        // 广告加载成功，展示页面，等待用户关闭时回调结果
                        // 不在这里设置结果，而是在页面关闭时设置
                    }

                    is AdResult.Failure -> {
                        // 广告加载失败，立即返回失败结果
                        setResult(result)
                        closeAdAndFinish()
                    }
                }
            } catch (e: Exception) {
                // 异常情况，立即返回失败结果
                AdLogger.e("全屏原生广告页面加载失败:${e.message}")
                setResult(
                    AdResult.Failure(
                        AdException(
                            code = -2,
                            message = "全屏原生广告加载异常: ${e.message}",
                            cause = e
                        )
                    )
                )
                closeAdAndFinish()
            }
        }
    }

    private fun notifyAdDisplayedIfNeeded() {
        val callback = consumeOnAdDisplayedCallback() ?: return
        try {
            callback.invoke(this)
        } catch (e: Exception) {
            AdLogger.e("$TAG onAdDisplayed 回调异常", e)
        }
    }

    /**
     * 关闭广告并结束Activity
     */
    private fun closeAdAndFinish() {
        // 如果还没有设置结果（说明是用户主动关闭），设置成功结果
        if (continuation != null) {
            setResult(AdResult.Success(Unit))
        }
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 如果Activity被销毁但还没有设置结果，设置失败结果
        if (continuation != null) {
            setResult(
                AdResult.Failure(
                    AdException(
                        code = -3,
                        message = "Activity被销毁"
                    )
                )
            )
        }
    }

    override fun onBackPressed() {
        // 禁用返回键，只能通过广告关闭按钮关闭
    }
} 
