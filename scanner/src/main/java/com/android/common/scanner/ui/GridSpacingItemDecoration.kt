package com.android.common.scanner.ui

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * 网格布局间距装饰器
 * 确保左右边距和中间间距一致
 */
class GridSpacingItemDecoration(
    private val spanCount: Int,
    private val spacing: Int,
    private val includeEdge: Boolean = true
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val column = position % spanCount

        if (includeEdge) {
            // 左边距: spacing - column * spacing / spanCount
            outRect.left = spacing - column * spacing / spanCount
            // 右边距: (column + 1) * spacing / spanCount
            outRect.right = (column + 1) * spacing / spanCount

            // 顶部间距
            if (position < spanCount) {
                outRect.top = spacing
            }
            outRect.bottom = spacing
        } else {
            // 左边距: column * spacing / spanCount
            outRect.left = column * spacing / spanCount
            // 右边距: spacing - (column + 1) * spacing / spanCount
            outRect.right = spacing - (column + 1) * spacing / spanCount

            if (position >= spanCount) {
                outRect.top = spacing
            }
        }
    }
}
