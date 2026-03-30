package com.android.common.scanner.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.android.common.scanner.R
import com.android.common.scanner.databinding.LayoutViewQrDialogBinding
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.android.common.scanner.util.QRCodeShareUtils
import com.lxj.xpopup.XPopup
import com.lxj.xpopup.core.CenterPopupView
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class ViewQRDialog(
    context: Context,
    private val content: String,
    private val barcodeType: Int = 0
) : CenterPopupView(context) {

    private var qrBitmap: Bitmap? = null

    companion object {
        private const val QR_CODE_SIZE = 512

        fun show(context: Context, content: String, barcodeType: Int = 0) {
            val dialog = ViewQRDialog(context, content, barcodeType)
            XPopup.Builder(context)
                .hasNavigationBar(false)
                .hasStatusBar(false)
                .dismissOnBackPressed(true)
                .dismissOnTouchOutside(true)
                .enableDrag(false)
                .asCustom(dialog)
                .show()
        }
    }

    override fun getImplLayoutId(): Int {
        return R.layout.layout_view_qr_dialog
    }

    override fun onCreate() {
        super.onCreate()
        val binding = LayoutViewQrDialogBinding.bind(popupImplView)

        // 根据条码类型生成对应的条码图片
        qrBitmap = if (barcodeType != 0) {
            QRCodeShareUtils.generateBarcode(content, barcodeType)
        } else {
            QRCodeShareUtils.generateQRCode(content)
        }
        qrBitmap?.let {
            binding.ivQrCode.setImageBitmap(it)
        }

        // Download 按钮
        binding.btnDownload.setOnClickListener {
            saveQRCodeToGallery()
        }

        // Copy Link 按钮
        binding.btnCopyLink.setOnClickListener {
            copyToClipboard()
        }

        // Share 按钮
        binding.btnShare.setOnClickListener {
            shareQRCode()
        }
    }

    private fun generateQRCode(content: String): Bitmap? {
        return QRCodeShareUtils.generateQRCode(content)
    }

    private fun saveQRCodeToGallery() {
        val bitmap = qrBitmap ?: return

        // Android 9 及以下需要存储权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (!XXPermissions.isGrantedPermissions(context, Permission.WRITE_EXTERNAL_STORAGE)) {
                XXPermissions.with(context)
                    .permission(Permission.WRITE_EXTERNAL_STORAGE)
                    .request(object : OnPermissionCallback {
                        override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                            if (allGranted) {
                                doSaveQRCodeToGallery(bitmap)
                            }
                        }

                        override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                            if (doNotAskAgain) {
                                Toast.makeText(context, R.string.qrcode_view_qr_save_failed, Toast.LENGTH_SHORT).show()
                                XXPermissions.startPermissionActivity(context, permissions)
                            }
                        }
                    })
                return
            }
        }

        doSaveQRCodeToGallery(bitmap)
    }

    private fun doSaveQRCodeToGallery(bitmap: Bitmap) {
        try {
            val filename = "QRCode_${System.currentTimeMillis()}.png"

            val outputStream: OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/QRCode")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { context.contentResolver.openOutputStream(it) }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val qrDir = File(imagesDir, "QRCode")
                if (!qrDir.exists()) qrDir.mkdirs()
                val imageFile = File(qrDir, filename)
                FileOutputStream(imageFile)
            }

            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            Toast.makeText(context, R.string.qrcode_view_qr_saved, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, R.string.qrcode_view_qr_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("QR Content", content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, R.string.qrcode_result_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareQRCode() {
        QRCodeShareUtils.shareQRCode(context, content, barcodeType)
    }

    override fun onDismiss() {
        super.onDismiss()
        qrBitmap?.recycle()
        qrBitmap = null
    }
}
