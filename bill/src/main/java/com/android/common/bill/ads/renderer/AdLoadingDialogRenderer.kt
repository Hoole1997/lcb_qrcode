package com.android.common.bill.ads.renderer

import android.view.View

/**
 * 广告加载弹框渲染器接口
 * 宿主项目实现此接口以自定义 Loading 弹框的内容布局和交互
 * bill 内部保留 XPopup 弹框框架控制 show/hide，外部负责内容 UI 渲染
 */
interface AdLoadingDialogRenderer {

    /**
     * 提供弹框内容布局资源 ID
     * @return layout resource ID
     */
    fun getLayoutResId(): Int

    /**
     * 布局创建后回调，外部在此设置动画、初始化控件等
     * 准备就绪后必须调用 onReady 通知 bill 弹框可以展示
     * @param view 已 inflate 的布局根 View
     * @param onReady 外部准备就绪后调用此回调
     */
    fun onViewCreated(view: View, onReady: () -> Unit)

    /**
     * 更新加载文本（如倒计时 "Loading 2s"）
     * @param view 弹框内容根 View
     * @param text 要显示的文本
     */
    fun updateText(view: View, text: String)

    /**
     * 返回关闭按钮 View
     * SDK 内部会默认给该 View 绑定点击关闭弹框的行为
     * 如果不需要关闭按钮，请显式返回 null
     */
    fun findCloseView(view: View): View?

    /**
     * 弹框销毁时清理资源（如停止动画）
     * @param view 弹框内容根 View
     */
    fun onDestroy(view: View)
}
