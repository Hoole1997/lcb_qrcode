package com.android.common.bill.ui.dialog

import android.content.Context
import android.view.View
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.BasePopupView
import com.lxj.xpopup.core.CenterPopupView
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.renderer.AdLoadingDialogRenderer
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 全屏Loading弹框
 * 基于 XPopup CenterPopupView 实现
 * 内容 UI 渲染委托给外部 AdLoadingDialogRenderer
 * 提供show和hide伴生对象函数
 * show时不允许点空白或返回关闭；关闭按钮 View 由 renderer 提供，内部默认绑定点击关闭
 */
class ADLoadingDialog(context: Context) : CenterPopupView(context) {

    private var contentRootView: View? = null
    private var onRendererReady: (() -> Unit)? = null

    private val renderer: AdLoadingDialogRenderer
        get() = BillConfig.adLoadingDialogRenderer
            ?: throw IllegalStateException("AdLoadingDialogRenderer 未注册，请在 BillConfig 中设置 adLoadingDialogRenderer")

    override fun getImplLayoutId(): Int = renderer.getLayoutResId()

    override fun onCreate() {
        super.onCreate()
        contentRootView = this
        renderer.onViewCreated(contentRootView!!) {
            onRendererReady?.invoke()
            onRendererReady = null
        }
        renderer.findCloseView(contentRootView!!)?.setOnClickListener {
            hide()
        }
    }

    fun setOnRendererReadyCallback(callback: () -> Unit) {
        onRendererReady = callback
    }

    /**
     * 更新加载文本
     */
    fun updateLoadingText(text: String) {
        contentRootView?.let { renderer.updateText(it, text) }
    }

    override fun onDismiss() {
        contentRootView?.let { renderer.onDestroy(it) }
        contentRootView = null
        Companion.resumePendingShowIfNeeded()
        super.onDismiss()
    }

    companion object {
        private var popup: BasePopupView? = null
        private var currentDialog: ADLoadingDialog? = null
        private var pendingShowContinuation: CancellableContinuation<Unit>? = null

        private fun resumePendingShowIfNeeded() {
            val continuation = pendingShowContinuation ?: return
            pendingShowContinuation = null
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }

        /**
         * 显示Loading弹框（挂起函数）
         * 等待外部 Renderer 准备就绪后再返回
         * @param context 必须是 Activity Context
         */
        suspend fun show(context: Context) {
            if (isShowing()) return
            suspendCancellableCoroutine { continuation ->
                pendingShowContinuation = continuation
                continuation.invokeOnCancellation {
                    if (pendingShowContinuation === continuation) {
                        pendingShowContinuation = null
                    }
                }
                runCatching {
                    val dialog = ADLoadingDialog(context)
                    currentDialog = dialog
                    popup = XPopup.Builder(context)
                        .dismissOnTouchOutside(false)
                        .dismissOnBackPressed(false)
                        .isDestroyOnDismiss(true)
                        .animationDuration(0)
                        .asCustom(dialog)

                    dialog.setOnRendererReadyCallback {
                        resumePendingShowIfNeeded()
                    }

                    popup?.show()
                }.onFailure {
                    resumePendingShowIfNeeded()
                }
            }
        }

        /**
         * 隐藏Loading弹框
         */
        fun hide() {
            runCatching { popup?.dismiss() }
            resumePendingShowIfNeeded()
            popup = null
            currentDialog = null
        }

        /**
         * 检查是否正在显示
         */
        fun isShowing(): Boolean {
            return popup?.isShow ?: false
        }

        /**
         * 更新加载文本
         */
        fun updateText(text: String) {
            currentDialog?.updateLoadingText(text)
        }
    }
}
