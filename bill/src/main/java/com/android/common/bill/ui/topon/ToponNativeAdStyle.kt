package com.android.common.bill.ui.topon

/**
 * TopOn原生广告样式
 * 通过 BillConfig 注入 layoutResId，不再硬编码布局资源
 */
data class ToponNativeAdStyle(
    val layoutResId: Int,
    val description: String,
    val heightDp: Int = 0  // 高度dp，0表示自适应
)

