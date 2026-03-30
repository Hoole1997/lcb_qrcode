package com.android.common.scanner.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.android.common.scanner.R

/**
 * 通用扫光动画 View
 * 支持配置：边框显示、中心文字、扫光颜色、动画时长等
 * 
 * 使用场景：
 * - 负一屏扫描框（显示边框 + 文字）
 * - 开屏页扫光效果（仅扫光，无边框）
 * - 锁屏页扫描按钮（显示边框，无文字）
 */
class ScanAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val solidLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 扫光线的当前Y位置（0-1范围）
    private var scanLinePosition = 0f

    // ==================== 可配置属性 ====================

    // 是否显示边框
    private var showCorners = true

    // 是否显示中心文字
    private var showCenterText = false

    // 中心文字内容
    private var centerText: String = ""

    // 扫光渐变高度占View高度的比例
    private var gradientHeightRatio = 0.5f

    // 底部实线高度（dp）
    private var solidLineHeightDp = 3f

    // 扫光颜色
    private var scanLineColor = 0xFFFB4C46.toInt()

    // 边框颜色
    private var cornerColor = 0xFFFFFFFF.toInt()

    // 边框线宽（dp）
    private var cornerStrokeWidthDp = 5f

    // 边框角的长度（占View宽度的比例）
    private var cornerLengthRatio = 0.25f

    // 边框圆角半径（dp）
    private var cornerRadiusDp = 8f

    // 动画时长
    private var animationDuration = 2000L

    // 文字大小（sp）
    private var textSizeSp = 14f

    // 文字颜色
    private var textColor = 0xFFFFFFFF.toInt()

    // ==================== 内部变量 ====================

    private var scanAnimator: ValueAnimator? = null
    private val density = resources.displayMetrics.density

    // 实际像素值
    private var solidLineHeight = 0f
    private var cornerStrokeWidth = 0f
    private var cornerRadius = 0f
    private var textSize = 0f

    init {
        // 从 XML 属性读取配置
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.ScanAnimationView)
            showCorners = typedArray.getBoolean(R.styleable.ScanAnimationView_showCorners, true)
            showCenterText = typedArray.getBoolean(R.styleable.ScanAnimationView_showCenterText, false)
            centerText = typedArray.getString(R.styleable.ScanAnimationView_centerText) ?: ""
            gradientHeightRatio = typedArray.getFloat(R.styleable.ScanAnimationView_gradientHeightRatio, 0.5f)
            solidLineHeightDp = typedArray.getDimension(R.styleable.ScanAnimationView_solidLineHeight, 3f * density) / density
            scanLineColor = typedArray.getColor(R.styleable.ScanAnimationView_scanLineColor, 0xFFFB4C46.toInt())
            cornerColor = typedArray.getColor(R.styleable.ScanAnimationView_cornerColor, 0xFFFFFFFF.toInt())
            cornerStrokeWidthDp = typedArray.getDimension(R.styleable.ScanAnimationView_cornerStrokeWidth, 5f * density) / density
            cornerLengthRatio = typedArray.getFloat(R.styleable.ScanAnimationView_cornerLengthRatio, 0.25f)
            cornerRadiusDp = typedArray.getDimension(R.styleable.ScanAnimationView_cornerRadius, 8f * density) / density
            animationDuration = typedArray.getInt(R.styleable.ScanAnimationView_animationDuration, 2000).toLong()
            textSizeSp = typedArray.getDimension(R.styleable.ScanAnimationView_textSize, 14f * density) / density
            textColor = typedArray.getColor(R.styleable.ScanAnimationView_textColor, 0xFFFFFFFF.toInt())
            typedArray.recycle()
        }

        // 如果没有设置文字，使用默认文字
        if (centerText.isEmpty() && showCenterText) {
            centerText = context.getString(R.string.qrcode_tap_to_scan)
        }

        // 转换为像素值
        updateDimensionValues()

        // 初始化画笔
        initPaints()
    }

    private fun updateDimensionValues() {
        solidLineHeight = solidLineHeightDp * density
        cornerStrokeWidth = cornerStrokeWidthDp * density
        cornerRadius = cornerRadiusDp * density
        textSize = textSizeSp * density
    }

    private fun initPaints() {
        // 边框画笔
        cornerPaint.color = cornerColor
        cornerPaint.style = Paint.Style.STROKE
        cornerPaint.strokeWidth = cornerStrokeWidth
        cornerPaint.strokeCap = Paint.Cap.ROUND

        // 实线画笔
        solidLinePaint.color = scanLineColor
        solidLinePaint.style = Paint.Style.FILL

        // 文字画笔
        textPaint.color = textColor
        textPaint.textSize = textSize
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (showCorners) {
            drawCorners(canvas)
        }
        if (showCenterText && centerText.isNotEmpty()) {
            drawText(canvas)
        }
        drawScanLine(canvas)
    }

    private fun drawText(canvas: Canvas) {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (viewWidth <= 0 || viewHeight <= 0) return

        val x = viewWidth / 2
        val y = viewHeight / 2 - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(centerText, x, y, textPaint)
    }

    private fun drawCorners(canvas: Canvas) {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (viewWidth <= 0 || viewHeight <= 0) return

        val cornerLength = viewWidth * cornerLengthRatio
        val halfStroke = cornerStrokeWidth / 2

        val path = Path()

        // 左上角
        path.moveTo(halfStroke, cornerLength)
        path.lineTo(halfStroke, cornerRadius + halfStroke)
        path.arcTo(
            RectF(halfStroke, halfStroke, cornerRadius * 2 + halfStroke, cornerRadius * 2 + halfStroke),
            180f, 90f, false
        )
        path.lineTo(cornerLength, halfStroke)
        canvas.drawPath(path, cornerPaint)

        // 右上角
        path.reset()
        path.moveTo(viewWidth - cornerLength, halfStroke)
        path.lineTo(viewWidth - cornerRadius - halfStroke, halfStroke)
        path.arcTo(
            RectF(viewWidth - cornerRadius * 2 - halfStroke, halfStroke, viewWidth - halfStroke, cornerRadius * 2 + halfStroke),
            270f, 90f, false
        )
        path.lineTo(viewWidth - halfStroke, cornerLength)
        canvas.drawPath(path, cornerPaint)

        // 左下角
        path.reset()
        path.moveTo(halfStroke, viewHeight - cornerLength)
        path.lineTo(halfStroke, viewHeight - cornerRadius - halfStroke)
        path.arcTo(
            RectF(halfStroke, viewHeight - cornerRadius * 2 - halfStroke, cornerRadius * 2 + halfStroke, viewHeight - halfStroke),
            180f, -90f, false
        )
        path.lineTo(cornerLength, viewHeight - halfStroke)
        canvas.drawPath(path, cornerPaint)

        // 右下角
        path.reset()
        path.moveTo(viewWidth - cornerLength, viewHeight - halfStroke)
        path.lineTo(viewWidth - cornerRadius - halfStroke, viewHeight - halfStroke)
        path.arcTo(
            RectF(viewWidth - cornerRadius * 2 - halfStroke, viewHeight - cornerRadius * 2 - halfStroke, viewWidth - halfStroke, viewHeight - halfStroke),
            90f, -90f, false
        )
        path.lineTo(viewWidth - halfStroke, viewHeight - cornerLength)
        canvas.drawPath(path, cornerPaint)
    }

    private fun drawScanLine(canvas: Canvas) {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (viewWidth <= 0 || viewHeight <= 0) return

        val gradientHeight = viewHeight * gradientHeightRatio
        val currentY = scanLinePosition * viewHeight

        // 绘制渐变区域
        val gradientTop = currentY - gradientHeight
        val gradientBottom = currentY - solidLineHeight

        if (gradientBottom > 0) {
            val transparentColor = scanLineColor and 0x00FFFFFF
            val semiTransparentColor = (scanLineColor and 0x00FFFFFF) or 0x40000000

            val gradient = LinearGradient(
                0f, gradientTop,
                0f, gradientBottom,
                intArrayOf(transparentColor, semiTransparentColor),
                null,
                Shader.TileMode.CLAMP
            )
            scanLinePaint.shader = gradient

            canvas.drawRect(
                0f,
                maxOf(0f, gradientTop),
                viewWidth,
                gradientBottom,
                scanLinePaint
            )
        }

        // 绘制底部实线
        val lineTop = currentY - solidLineHeight
        val lineBottom = currentY

        if (lineBottom > 0 && lineTop < viewHeight) {
            canvas.drawRect(
                0f,
                maxOf(0f, lineTop),
                viewWidth,
                minOf(viewHeight, lineBottom),
                solidLinePaint
            )
        }
    }

    fun startAnimation() {
        if (scanAnimator?.isRunning == true) return

        scanAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDuration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                scanLinePosition = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopAnimation() {
        scanAnimator?.cancel()
        scanAnimator = null
    }

    // ==================== 外部配置方法 ====================

    fun setShowCorners(show: Boolean) {
        showCorners = show
        invalidate()
    }

    fun setShowCenterText(show: Boolean) {
        showCenterText = show
        invalidate()
    }

    fun setCenterText(text: String) {
        centerText = text
        invalidate()
    }

    fun setScanLineColor(color: Int) {
        scanLineColor = color
        solidLinePaint.color = color
        invalidate()
    }

    fun setCornerColor(color: Int) {
        cornerColor = color
        cornerPaint.color = color
        invalidate()
    }

    fun setAnimationDuration(duration: Long) {
        animationDuration = duration
        if (scanAnimator?.isRunning == true) {
            stopAnimation()
            startAnimation()
        }
    }

    fun setGradientHeightRatio(ratio: Float) {
        gradientHeightRatio = ratio
        invalidate()
    }
}
