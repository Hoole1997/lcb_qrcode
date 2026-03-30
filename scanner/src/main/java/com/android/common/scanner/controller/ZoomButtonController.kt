package com.android.common.scanner.controller

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.SeekBar

/**
 * 缩放按钮控制器
 * 处理点击和长按缩放逻辑
 */
class ZoomButtonController(
    private val seekBar: SeekBar,
    private val onZoomChanged: (Float) -> Unit
) {

    companion object {
        const val DIRECTION_ZOOM_OUT = -1
        const val DIRECTION_ZOOM_IN = 1
        private const val LONG_PRESS_DELAY = 300L
        private const val LONG_PRESS_INTERVAL = 50L
        private const val LONG_PRESS_STEP = 5
        private const val CLICK_STEP = 10
        private const val ANIMATION_DURATION = 150L
    }

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var isLongPressing = false

    /**
     * 设置缩放按钮
     * @param view 按钮视图
     * @param direction 缩放方向：DIRECTION_ZOOM_OUT(-1) 或 DIRECTION_ZOOM_IN(1)
     */
    @SuppressLint("ClickableViewAccessibility")
    fun setupZoomButton(view: View, direction: Int) {
        val longPressRunnable = object : Runnable {
            override fun run() {
                if (isLongPressing) {
                    adjustZoom(direction, LONG_PRESS_STEP)
                    longPressHandler.postDelayed(this, LONG_PRESS_INTERVAL)
                }
            }
        }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressHandler.postDelayed({
                        isLongPressing = true
                        longPressRunnable.run()
                    }, LONG_PRESS_DELAY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isLongPressing) {
                        adjustZoomWithAnimation(direction, CLICK_STEP)
                    }
                    isLongPressing = false
                    longPressHandler.removeCallbacksAndMessages(null)
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 调整缩放（无动画）
     */
    private fun adjustZoom(direction: Int, step: Int) {
        val currentProgress = seekBar.progress
        val newProgress = (currentProgress + direction * step).coerceIn(0, 100)
        seekBar.progress = newProgress
        onZoomChanged(newProgress / 100f)
    }

    /**
     * 调整缩放（带动画）
     */
    private fun adjustZoomWithAnimation(direction: Int, step: Int) {
        val currentProgress = seekBar.progress
        val targetProgress = (currentProgress + direction * step).coerceIn(0, 100)

        ObjectAnimator.ofInt(seekBar, "progress", currentProgress, targetProgress).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val progress = it.animatedValue as Int
                onZoomChanged(progress / 100f)
            }
            start()
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        isLongPressing = false
        longPressHandler.removeCallbacksAndMessages(null)
    }
}
