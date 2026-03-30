package com.android.common.scanner.controller

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService

/**
 * 相机控制器
 * 封装 CameraX 初始化、预览、手电筒、缩放等功能
 */
class CameraController(private val context: Context) {

    companion object {
        private const val TAG = "CameraController"
    }

    /**
     * 相机就绪回调
     */
    interface CameraReadyCallback {
        fun onCameraReady(camera: Camera, previewWidth: Int, previewHeight: Int)
        fun onCameraError(error: String)
    }

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null

    /**
     * 启动相机
     * @param lifecycleOwner 生命周期所有者
     * @param previewView 预览视图
     * @param imageAnalyzer 图像分析器（可选）
     * @param analyzerExecutor 分析器执行器（可选）
     * @param callback 相机就绪回调
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        imageAnalyzer: ImageAnalysis.Analyzer? = null,
        analyzerExecutor: ExecutorService? = null,
        callback: CameraReadyCallback? = null
    ) {
        this.previewView = previewView
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // 构建用例列表
                val useCases = mutableListOf<UseCase>(preview)

                // 如果有图像分析器，添加到用例
                if (imageAnalyzer != null && analyzerExecutor != null) {
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(analyzerExecutor, imageAnalyzer)
                        }
                    useCases.add(imageAnalysis)
                }

                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    *useCases.toTypedArray()
                )

                // 等待预览视图布局完成后回调
                previewView.post {
                    camera?.let { cam ->
                        callback?.onCameraReady(cam, previewView.width, previewView.height)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
                callback?.onCameraError(e.message ?: "Unknown error")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * 设置手电筒状态
     */
    fun setTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    /**
     * 设置缩放级别（线性缩放 0-1）
     */
    fun setZoomLevel(level: Float) {
        camera?.cameraControl?.setLinearZoom(level.coerceIn(0f, 1f))
    }

    /**
     * 获取当前相机实例
     */
    fun getCamera(): Camera? = camera

    /**
     * 释放资源
     */
    fun release() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
        previewView = null
    }
}
