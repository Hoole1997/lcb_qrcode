package com.android.common.bill.ui.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * 自定义 AD 标签视图
 * 完全自定义绘制蓝色对角线形状的标签，带有白色 "AD" 文字
 */
class AdLabelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    // 标签颜色
    private val labelColor = Color.parseColor("#2ABDFC")
    private val textColor = Color.WHITE

    // 文字内容
    private val adText = "AD"

    // 标签尺寸（dp）
    private val labelWidthDp = 30f
    private val labelHeightDp = 30f

    init {
        setupPaints()
    }

    private fun setupPaints() {
        // 背景画笔
        paint.color = labelColor
        paint.style = Paint.Style.FILL

        // 文字画笔
        textPaint.color = textColor
        textPaint.textSize = 11f * resources.displayMetrics.density
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (labelWidthDp * resources.displayMetrics.density).toInt()
        val height = (labelHeightDp * resources.displayMetrics.density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        // 创建对角线形状的路径
        createDiagonalPath(width, height)

        // 绘制背景
        canvas.drawPath(path, paint)

        // 绘制文字
        drawText(canvas, width, height)
    }

    private fun createDiagonalPath(width: Float, height: Float) {
        path.reset()

        val cornerRadius = 12f * resources.displayMetrics.density

        // 从左上角圆角后开始
        path.moveTo(cornerRadius, 0f)

        // 上边线到右上角（无圆角）
        path.lineTo(width, 0f)

        // 对角线：从右上角到左下角
        path.lineTo(0f, height)

        // 左边线到左上角圆角前
        path.lineTo(0f, cornerRadius)

        // 左上角圆角
        path.quadTo(0f, 0f, cornerRadius, 0f)

        path.close()
    }

    private fun drawText(canvas: Canvas, width: Float, height: Float) {
        val textBounds = Rect()
        textPaint.getTextBounds(adText, 0, adText.length, textBounds)

        // 计算文字位置（在左边部分的中间）
        val textX = width * 0.4f
        val textY = height * 0.5f

        // 保存画布状态
        canvas.save()

        // 逆时针旋转文字（大约-45度）
        canvas.rotate(-45f, textX, textY)

        // 绘制文字
        canvas.drawText(adText, textX, textY, textPaint)

        // 恢复画布状态
        canvas.restore()
    }
}
