package com.android.common.scanner.dialog

import android.content.Context
import com.blankj.utilcode.util.SPUtils
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.BottomPopupView
import com.android.common.scanner.R
import com.android.common.scanner.databinding.LayoutScanGuideDialogBinding

/**
 * 扫描引导弹框
 * 首次进入扫描页时显示，告诉用户如何使用扫描功能
 */
class ScanGuideDialog(
    context: Context,
    private val onDismissCallback: (() -> Unit)? = null
) : BottomPopupView(context) {

    companion object {
        private const val SP_KEY_FIRST_SCAN = "isFirstScanGuide"

        /**
         * 检查是否首次进入
         */
        fun isFirstTime(): Boolean {
            return SPUtils.getInstance().getBoolean(SP_KEY_FIRST_SCAN, true)
        }

        /**
         * 检查是否需要显示引导弹框（首次进入时显示）
         * @param onDismiss 弹框关闭后的回调
         */
        fun checkAndShow(context: Context, onDismiss: (() -> Unit)? = null): Boolean {
            if (isFirstTime()) {
                show(context, onDismiss)
                return true
            }
            return false
        }

        /**
         * 强制显示引导弹框
         * @param onDismiss 弹框关闭后的回调
         */
        fun show(context: Context, onDismiss: (() -> Unit)? = null) {
            XPopup.Builder(context)
                .hasNavigationBar(false)
                .asCustom(ScanGuideDialog(context, onDismiss))
                .show()
        }
    }

    override fun getImplLayoutId(): Int {
        return R.layout.layout_scan_guide_dialog
    }

    override fun onCreate() {
        super.onCreate()
        // 标记已显示过引导
        SPUtils.getInstance().put(SP_KEY_FIRST_SCAN, false)
        val binding = LayoutScanGuideDialogBinding.bind(popupImplView)
        binding.btnContinue.setOnClickListener {
            dismiss()
        }
    }

    override fun onDismiss() {
        super.onDismiss()
        onDismissCallback?.invoke()
    }
}
