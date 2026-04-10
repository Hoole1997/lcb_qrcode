package com.android.common.bill.ui

/**
 * 统一原生广告样式类型
 * 各平台通过 BillConfig 注入具体的样式实现
 */
enum class NativeAdStyleType {
    /** 标准样式：小尺寸/列表项 */
    STANDARD,
    /** 大样式：大尺寸/卡片 */
    LARGE
}
