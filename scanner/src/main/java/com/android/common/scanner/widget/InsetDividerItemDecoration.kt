package com.android.common.scanner.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.RecyclerView

/**
 * 带左右边距的分割线
 */
class InsetDividerItemDecoration(
    private val height: Int,
    @ColorInt private val color: Int,
    private val leftInset: Int,
    private val rightInset: Int
) : RecyclerView.ItemDecoration() {

    private val paint = Paint().apply {
        this.color = this@InsetDividerItemDecoration.color
        style = Paint.Style.FILL
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        val itemCount = parent.adapter?.itemCount ?: 0
        // 最后一项不添加分割线
        if (position < itemCount - 1) {
            outRect.bottom = height
        }
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val left = parent.paddingLeft + leftInset
        val right = parent.width - parent.paddingRight - rightInset

        val childCount = parent.childCount
        for (i in 0 until childCount - 1) {
            val child = parent.getChildAt(i)
            val params = child.layoutParams as RecyclerView.LayoutParams
            val top = child.bottom + params.bottomMargin
            val bottom = top + height
            c.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
        }
    }

    companion object {
        fun create(context: Context, heightDp: Float, @ColorInt color: Int, insetDp: Float): InsetDividerItemDecoration {
            val density = context.resources.displayMetrics.density
            val height = (heightDp * density).toInt()
            val inset = (insetDp * density).toInt()
            return InsetDividerItemDecoration(height, color, inset, inset)
        }
    }
}
