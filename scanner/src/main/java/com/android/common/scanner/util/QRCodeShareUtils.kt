package com.android.common.scanner.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.blankj.utilcode.util.IntentUtils
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

object QRCodeShareUtils {

    private const val QR_CODE_SIZE = 512
    private const val BARCODE_WIDTH = 600
    private const val BARCODE_HEIGHT = 200

    /**
     * 分享PDF文件
     * @param context 上下文
     * @param uriString PDF文件的URI字符串
     * @param fileName 文件名（用于显示）
     */
    fun sharePdf(context: Context, uriString: String, fileName: String) {
        try {
            val uri = Uri.parse(uriString)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, fileName))
        } catch (e: Exception) {
            e.printStackTrace()
            // 分享失败，分享文件名文本
            context.startActivity(IntentUtils.getShareTextIntent(fileName))
        }
    }

    /**
     * 分享条码内容，优先生成条码图片分享，失败则分享文本
     * @param context 上下文
     * @param content 条码内容
     * @param barcodeType MLKit Barcode.FORMAT_* 常量，默认为0表示生成二维码
     */
    fun shareQRCode(context: Context, content: String, barcodeType: Int = 0) {
        try {
            // 根据条码类型生成对应的条码图片
            val bitmap = if (barcodeType != 0) {
                generateBarcode(content, barcodeType)
            } else {
                generateQRCode(content)
            }
            if (bitmap != null) {
                // 保存到缓存目录
                val cachePath = File(context.cacheDir, "images")
                cachePath.mkdirs()
                val file = File(cachePath, "shared_barcode.png")
                FileOutputStream(file).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()

                // 使用 IntentUtils 分享图片
                context.startActivity(IntentUtils.getShareImageIntent(file))
            } else {
                // 生成失败，分享文本
                context.startActivity(IntentUtils.getShareTextIntent(content))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果分享图片失败，分享文本
            context.startActivity(IntentUtils.getShareTextIntent(content))
        }
    }

    /**
     * 生成二维码图片
     */
    fun generateQRCode(content: String): Bitmap? {
        return try {
            val hints = hashMapOf<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 2
            hints[EncodeHintType.ERROR_CORRECTION] = com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 根据条码类型生成对应的条码图片
     * @param content 条码内容
     * @param barcodeType MLKit Barcode.FORMAT_* 常量
     */
    fun generateBarcode(content: String, barcodeType: Int): Bitmap? {
        return try {
            val format = mlKitFormatToZxing(barcodeType)
            val hints = hashMapOf<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 2

            // 二维码类型使用正方形尺寸
            val (width, height) = when (format) {
                BarcodeFormat.QR_CODE, BarcodeFormat.AZTEC, BarcodeFormat.DATA_MATRIX, BarcodeFormat.PDF_417 -> {
                    hints[EncodeHintType.ERROR_CORRECTION] = com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H
                    Pair(QR_CODE_SIZE, QR_CODE_SIZE)
                }
                else -> Pair(BARCODE_WIDTH, BARCODE_HEIGHT)
            }

            val writer = MultiFormatWriter()
            val bitMatrix = writer.encode(content, format, width, height, hints)

            val w = bitMatrix.width
            val h = bitMatrix.height
            val pixels = IntArray(w * h)

            for (y in 0 until h) {
                for (x in 0 until w) {
                    pixels[y * w + x] = if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
            }

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果生成失败，尝试生成二维码
            generateQRCode(content)
        }
    }

    /**
     * 将 MLKit Barcode.FORMAT_* 转换为 ZXing BarcodeFormat
     */
    private fun mlKitFormatToZxing(mlKitFormat: Int): BarcodeFormat {
        return when (mlKitFormat) {
            Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
            Barcode.FORMAT_AZTEC -> BarcodeFormat.AZTEC
            Barcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
            Barcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
            Barcode.FORMAT_CODE_93 -> BarcodeFormat.CODE_93
            Barcode.FORMAT_CODABAR -> BarcodeFormat.CODABAR
            Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
            Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
            Barcode.FORMAT_ITF -> BarcodeFormat.ITF
            Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
            Barcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
            Barcode.FORMAT_PDF417 -> BarcodeFormat.PDF_417
            Barcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
            else -> BarcodeFormat.QR_CODE
        }
    }
}
