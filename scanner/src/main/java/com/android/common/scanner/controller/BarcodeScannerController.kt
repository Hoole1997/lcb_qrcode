package com.android.common.scanner.controller

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 条码/二维码扫描控制器
 * 封装 MLKit 扫描逻辑、自动聚焦、自动缩放、音效等功能
 */
class BarcodeScannerController {

    companion object {
        private const val TAG = "BarcodeScannerController"
        private const val MIN_BARCODE_RATIO = 0.12f // 条码占画面最小比例，小于此值则放大
        private const val TARGET_BARCODE_RATIO = 0.25f // 目标条码占画面比例
        private const val FOCUS_INTERVAL = 500L // 聚焦间隔
    }

    /**
     * 扫描结果回调接口（简化版）
     */
    interface ScanResultCallback {
        fun onScanSuccess(result: String, barcodeType: Int, typeName: String)
        fun onScanError(error: String)
        fun onNoBarcodeFound()
    }

    /**
     * 缩放控制接口
     */
    interface ZoomController {
        fun getCurrentZoom(): Float
        fun setZoom(zoom: Float, animate: Boolean = true, onComplete: (() -> Unit)? = null)
    }

    private var barcodeScanner: BarcodeScanner? = null
    private var scanExecutor: ExecutorService? = null
    private var isScanning = true
    private var callback: ScanResultCallback? = null

    // 相机和缩放控制
    private var camera: Camera? = null
    private var zoomController: ZoomController? = null
    private var previewWidth: Int = 0
    private var previewHeight: Int = 0

    // 自动聚焦相关
    private var lastFocusTime = 0L

    // 自动缩放相关
    private var isAutoZooming = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 初始化扫描器
     * @param context 上下文
     * @param formats 要支持的条码格式，默认支持所有常用格式
     */
    fun initialize(context: Context, formats: List<Int> = getDefaultFormats()) {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(formats.first(), *formats.drop(1).toIntArray())
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)
        scanExecutor = Executors.newSingleThreadExecutor()
        isScanning = true
    }

    /**
     * 设置扫描结果回调
     */
    fun setScanResultCallback(callback: ScanResultCallback) {
        this.callback = callback
    }

    /**
     * 设置相机和缩放控制器
     */
    fun setCameraController(camera: Camera, zoomController: ZoomController, previewWidth: Int, previewHeight: Int) {
        this.camera = camera
        this.zoomController = zoomController
        this.previewWidth = previewWidth
        this.previewHeight = previewHeight
    }

    /**
     * 获取用于 CameraX 的图像分析器
     */
    fun getImageAnalyzer(): ImageAnalysis.Analyzer {
        return BarcodeImageAnalyzer()
    }

    /**
     * 获取扫描执行器
     */
    fun getScanExecutor(): ExecutorService {
        return scanExecutor ?: Executors.newSingleThreadExecutor().also { scanExecutor = it }
    }

    /**
     * 从 Uri 扫描图片中的条码
     */
    fun scanFromUri(context: Context, uri: Uri) {
        val scanner = barcodeScanner ?: run {
            callback?.onScanError("Scanner not initialized")
            return
        }

        try {
            val image = InputImage.fromFilePath(context, uri)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val barcode = barcodes.first()
                        barcode.rawValue?.let { value ->
                            callback?.onScanSuccess(value, barcode.format, getBarcodeTypeName(barcode.format))
                        } ?: callback?.onNoBarcodeFound()
                    } else {
                        callback?.onNoBarcodeFound()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Barcode scanning failed", e)
                    callback?.onScanError(e.message ?: "Unknown error")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image", e)
            callback?.onScanError(e.message ?: "Failed to load image")
        }
    }

    /**
     * 暂停扫描
     */
    fun pauseScanning() {
        isScanning = false
    }

    /**
     * 恢复扫描
     */
    fun resumeScanning() {
        isScanning = true
    }

    /**
     * 是否正在扫描
     */
    fun isScanning(): Boolean = isScanning

    /**
     * 释放资源
     */
    fun release() {
        barcodeScanner?.close()
        barcodeScanner = null
        scanExecutor?.shutdown()
        scanExecutor = null
        callback = null
        camera = null
        zoomController = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    /**
     * 自动聚焦到指定点
     */
    private fun focusOnPoint(centerX: Float, centerY: Float) {
        val cam = camera ?: return
        if (previewWidth <= 0 || previewHeight <= 0) return

        try {
            val factory = SurfaceOrientedMeteringPointFactory(
                previewWidth.toFloat(),
                previewHeight.toFloat()
            )
            val point = factory.createPoint(centerX * previewWidth, centerY * previewHeight)
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                .setAutoCancelDuration(2, TimeUnit.SECONDS)
                .build()
            cam.cameraControl.startFocusAndMetering(action)
            Log.d(TAG, "Auto focus at ($centerX, $centerY)")
        } catch (e: Exception) {
            Log.e(TAG, "Focus failed", e)
        }
    }

    /**
     * 处理检测到的条码（自动缩放和聚焦）
     * @return true 表示需要等待（正在缩放），false 表示可以继续识别
     */
    private fun handleBarcodeDetected(centerX: Float, centerY: Float, barcodeRatio: Float): Boolean {
        // 如果正在自动缩放中，继续等待
        if (isAutoZooming) return true

        val zoom = zoomController ?: return false

        // 条码太小，需要先放大再识别
        if (barcodeRatio < MIN_BARCODE_RATIO) {
            isAutoZooming = true
            mainHandler.post {
                // 先聚焦
                focusOnPoint(centerX, centerY)
                // 计算目标缩放值
                val currentZoom = zoom.getCurrentZoom()
                val zoomMultiplier = TARGET_BARCODE_RATIO / barcodeRatio
                val targetZoom = ((currentZoom + 0.1f) * zoomMultiplier).coerceIn(0.2f, 0.8f)

                Log.d(TAG, "Auto zoom: ratio=$barcodeRatio, currentZoom=$currentZoom, targetZoom=$targetZoom")

                // 平滑缩放，完成后允许继续识别
                zoom.setZoom(targetZoom, true) {
                    // 缩放完成后延迟一点再允许识别，让相机稳定
                    mainHandler.postDelayed({
                        isAutoZooming = false
                    }, 300)
                }
            }
            return true // 暂不触发识别
        }

        // 自动聚焦
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFocusTime >= FOCUS_INTERVAL) {
            lastFocusTime = currentTime
            mainHandler.post {
                focusOnPoint(centerX, centerY)
            }
        }

        return false // 继续识别
    }

    /**
     * 获取条码类型名称
     */
    fun getBarcodeTypeName(format: Int): String {
        return when (format) {
            Barcode.FORMAT_QR_CODE -> "QR Code"
            Barcode.FORMAT_AZTEC -> "Aztec"
            Barcode.FORMAT_CODE_128 -> "Code 128"
            Barcode.FORMAT_CODE_39 -> "Code 39"
            Barcode.FORMAT_CODE_93 -> "Code 93"
            Barcode.FORMAT_CODABAR -> "Codabar"
            Barcode.FORMAT_EAN_8 -> "EAN-8"
            Barcode.FORMAT_EAN_13 -> "EAN-13"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_UPC_A -> "UPC-A"
            Barcode.FORMAT_UPC_E -> "UPC-E"
            Barcode.FORMAT_PDF417 -> "PDF417"
            Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
            else -> "Unknown"
        }
    }

    /**
     * 默认支持的条码格式
     */
    private fun getDefaultFormats(): List<Int> {
        return listOf(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_AZTEC,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_CODABAR,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_PDF417,
            Barcode.FORMAT_DATA_MATRIX
        )
    }

    /**
     * CameraX 图像分析器实现
     */
    private inner class BarcodeImageAnalyzer : ImageAnalysis.Analyzer {

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            if (!isScanning) {
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage != null && barcodeScanner != null) {
                val imageWidth = imageProxy.width
                val imageHeight = imageProxy.height
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                barcodeScanner?.process(image)
                    ?.addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty() && isScanning) {
                            val barcode = barcodes.first()

                            // 计算条码中心点（归一化坐标）和占画面比例
                            barcode.boundingBox?.let { box ->
                                val centerX = (box.left + box.right) / 2f / imageWidth
                                val centerY = (box.top + box.bottom) / 2f / imageHeight
                                // 计算条码占画面的比例（取宽高中较大的边）
                                val barcodeSize = maxOf(box.width(), box.height()).toFloat()
                                val imageSize = minOf(imageWidth, imageHeight).toFloat()
                                val barcodeRatio = barcodeSize / imageSize

                                // 处理自动缩放和聚焦
                                if (handleBarcodeDetected(centerX, centerY, barcodeRatio)) {
                                    return@addOnSuccessListener
                                }
                            }

                            barcode.rawValue?.let { value ->
                                isScanning = false
                                // 回调扫描结果
                                mainHandler.post {
                                    callback?.onScanSuccess(value, barcode.format, getBarcodeTypeName(barcode.format))
                                }
                            }
                        }
                    }
                    ?.addOnFailureListener { e ->
                        Log.e(TAG, "Barcode analysis failed", e)
                    }
                    ?.addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}
