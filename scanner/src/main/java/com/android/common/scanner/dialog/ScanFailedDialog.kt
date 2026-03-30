package com.android.common.scanner.dialog

import android.content.Context
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.BottomPopupView
import com.android.common.scanner.R
import com.android.common.scanner.databinding.LayoutScanFailedDialogBinding

/**
 * 扫描失败弹框
 * 选择图片识别失败时显示
 */
class ScanFailedDialog(
    context: Context,
    private val title: String,
    private val subtitle: String,
    private val buttonText: String,
    private val onButtonClick: (() -> Unit)? = null,
    private val onDismissCallback: (() -> Unit)? = null
) : BottomPopupView(context) {

    companion object {
        /**
         * 显示扫描失败弹框（默认文案）
         * @param onDismiss 弹框关闭后的回调（用于恢复扫描）
         */
        fun show(context: Context, onDismiss: (() -> Unit)? = null) {
            show(
                context = context,
                title = "Couldn't read QR Code",
                subtitle = "Try Scanning the QR Code again",
                buttonText = "Try again",
                onButtonClick = null,
                onDismiss = onDismiss
            )
        }

        /**
         * 显示自定义文案的弹框
         * @param title 标题
         * @param subtitle 副标题
         * @param buttonText 按钮文字
         * @param onButtonClick 按钮点击回调（可选）
         * @param onDismiss 弹框关闭后的回调
         */
        fun show(
            context: Context,
            title: String,
            subtitle: String,
            buttonText: String,
            onButtonClick: (() -> Unit)? = null,
            onDismiss: (() -> Unit)? = null
        ) {
            XPopup.Builder(context)
                .hasNavigationBar(false)
                .asCustom(ScanFailedDialog(context, title, subtitle, buttonText, onButtonClick, onDismiss))
                .show()
        }
    }

    override fun getImplLayoutId(): Int {
        return R.layout.layout_scan_failed_dialog
    }

    override fun onCreate() {
        super.onCreate()
        val binding = LayoutScanFailedDialogBinding.bind(popupImplView)

        binding.tvTitle.text = title
        binding.tvSubtitle.text = subtitle
        binding.btnTryAgain.text = buttonText

        binding.btnTryAgain.setOnClickListener {
            onButtonClick?.invoke()
            dismiss()
        }
    }

    override fun onDismiss() {
        super.onDismiss()
        onDismissCallback?.invoke()
    }
}
