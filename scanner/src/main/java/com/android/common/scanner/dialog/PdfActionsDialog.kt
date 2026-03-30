package com.android.common.scanner.dialog

import android.content.Context
import android.view.View
import com.android.common.scanner.R
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.BottomPopupView

class PdfActionsDialog(context: Context) : BottomPopupView(context) {

    private var onShareClick: (() -> Unit)? = null
    private var onOpenWithClick: (() -> Unit)? = null

    override fun getImplLayoutId(): Int {
        return R.layout.dialog_pdf_actions
    }

    override fun onCreate() {
        super.onCreate()

        findViewById<View>(R.id.ivClose).setOnClickListener {
            dismiss()
        }

        findViewById<View>(R.id.layoutShare).setOnClickListener {
            onShareClick?.invoke()
            dismiss()
        }

        findViewById<View>(R.id.layoutOpenWith).setOnClickListener {
            onOpenWithClick?.invoke()
            dismiss()
        }
    }

    fun setOnShareClick(listener: () -> Unit): PdfActionsDialog {
        this.onShareClick = listener
        return this
    }

    fun setOnOpenWithClick(listener: () -> Unit): PdfActionsDialog {
        this.onOpenWithClick = listener
        return this
    }

    companion object {
        fun show(
            context: Context,
            onShare: () -> Unit,
            onOpenWith: () -> Unit
        ) {
            XPopup.Builder(context)
                .hasNavigationBar(false)
                .asCustom(
                    PdfActionsDialog(context)
                        .setOnShareClick(onShare)
                        .setOnOpenWithClick(onOpenWith)
                )
                .show()
        }
    }
}
