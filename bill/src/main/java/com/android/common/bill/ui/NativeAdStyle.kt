package com.android.common.bill.ui

/**
 * AdMob 原生广告样式
 * 通过 BillConfig 注入 layoutResId，不再硬编码布局资源
 */
data class NativeAdStyle(
    val layoutResId: Int,
    val description: String
)
