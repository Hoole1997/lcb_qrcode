package com.android.common.scanner.dialog

import android.content.Context
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.android.common.scanner.R
import com.android.common.scanner.databinding.LayoutScanSuccessDialogBinding
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.animator.PopupAnimator
import com.lxj.xpopup.impl.FullScreenPopupView

/**
 * 扫描成功弹框
 * 显示 Lottie 动画，动画结束后自动关闭并回调
 */
class ScanSuccessDialog(
    context: Context,
    private val onAnimationComplete: () -> Unit
) : FullScreenPopupView(context) {

    companion object {
        /**
         * 显示扫描成功弹框
         * @param context 上下文
         * @param onComplete 动画完成后的回调
         */
        fun show(context: Context, onComplete: () -> Unit) {
            val dialog = ScanSuccessDialog(context, onComplete)
            XPopup.Builder(context)
                .hasNavigationBar(false)
                .hasStatusBar(false)
                .dismissOnBackPressed(false)
                .dismissOnTouchOutside(false)
                .enableDrag(false)
                .asCustom(dialog)
                .show()
        }
    }

    override fun getImplLayoutId(): Int {
        return R.layout.layout_scan_success_dialog
    }

    override fun onCreate() {
        super.onCreate()
        val binding = LayoutScanSuccessDialogBinding.bind(popupImplView)

        binding.lottieScanSuccess.apply {
            speed = 2f // 2倍速播放，2秒 -> 1秒
            addAnimatorListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    dismiss()
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    dismiss()
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            playAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onAnimationComplete.invoke()
    }

    override fun getPopupAnimator(): PopupAnimator {
        return ScaleBounceAnimator(popupImplView, 300)
    }

    /**
     * 自定义动画器 - 中间弹出 + 回弹效果
     */
    private class ScaleBounceAnimator(
        target: View,
        animationDuration: Int
    ) : PopupAnimator(target, animationDuration) {

        override fun initAnimator() {
            targetView.scaleX = 0f
            targetView.scaleY = 0f
            targetView.alpha = 0f
        }

        override fun animateShow() {
            targetView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(animationDuration.toLong())
                .setInterpolator(OvershootInterpolator(1.0f))
                .start()
        }

        override fun animateDismiss() {
            targetView.animate()
                .scaleX(0f)
                .scaleY(0f)
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }
    }
}
