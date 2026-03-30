package com.android.common.scanner.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.Camera
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.ui.NativeAdStyleType
import com.android.common.scanner.R
import com.android.common.scanner.base.BaseActivity
import com.android.common.scanner.controller.ScanFeedbackController
import com.android.common.scanner.data.repository.ScanHistoryRepository
import com.android.common.scanner.databinding.ActivityQrcodeScanBinding
import com.android.common.scanner.dialog.ScanFailedDialog
import com.android.common.scanner.dialog.ScanGuideDialog
import com.android.common.scanner.dialog.ScanSuccessDialog
import com.android.common.scanner.util.loadNative
import com.android.common.scanner.controller.BarcodeScannerController
import com.android.common.scanner.controller.CameraController
import com.android.common.scanner.controller.CameraPermissionController
import com.android.common.scanner.controller.ZoomButtonController
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import kotlinx.coroutines.launch

class QRCodeScanActivity : BaseActivity<ActivityQrcodeScanBinding, QRCodeScanModel>(),
    BarcodeScannerController.ScanResultCallback,
    CameraController.CameraReadyCallback,
    CameraPermissionController.PermissionCallback {

    companion object {
        private const val TAG = "QRCodeScanActivity"

        fun start(context: Context) {
            context.startActivity(Intent(context, QRCodeScanActivity::class.java))
        }
    }

    private val cameraController by lazy { CameraController(this) }
    private val scannerController = BarcodeScannerController()
    private val permissionController by lazy { CameraPermissionController(this) }
    private var zoomButtonController: ZoomButtonController? = null
    private var scanLineAnimator: ObjectAnimator? = null
    private var scaleGestureDetector: ScaleGestureDetector? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { scannerController.scanFromUri(this, it) }
    }

    override fun initBinding(): ActivityQrcodeScanBinding {
        return ActivityQrcodeScanBinding.inflate(layoutInflater)
    }

    override fun initModel(): QRCodeScanModel {
        return viewModels<QRCodeScanModel>().value
    }

    override fun initView() {
        // 设置沉浸式状态栏
        ImmersionBar.with(this)
            .statusBarDarkFont(false)
            .hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR)
            .init()

        // 初始化扫描控制器
        scannerController.initialize(this)
        scannerController.setScanResultCallback(this)

        // 初始化扫描反馈控制器
        ScanFeedbackController.init(this)

        // 返回按钮
        binding.ivBack.setOnClickListener {
            finish()
        }

        // 上传图片按钮
        binding.btnUploadPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // 手电筒按钮
        binding.btnFlashLight.setOnClickListener {
            model.toggleFlash()
        }

        // 缩放按钮控制器
        zoomButtonController = ZoomButtonController(binding.zoomSlider) { zoomLevel ->
            model.setZoomLevel(zoomLevel)
        }
        zoomButtonController?.setupZoomButton(binding.ivZoomOut, ZoomButtonController.DIRECTION_ZOOM_OUT)
        zoomButtonController?.setupZoomButton(binding.ivZoomIn, ZoomButtonController.DIRECTION_ZOOM_IN)

        // 缩放滑块
        binding.zoomSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val zoomRatio = progress / 100f
                    model.setZoomLevel(zoomRatio)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 启动扫描线动画
        startScanLineAnimation()

        // 设置扫描框捆合手势缩放
        setupPinchZoom()

        // 首次进入显示引导弹框，关闭后再申请相机权限
        val showed = ScanGuideDialog.checkAndShow(this) {
            checkPermissionAndStartCamera()
        }
        // 非首次进入，直接检查权限并启动相机
        if (!showed) {
            checkPermissionAndStartCamera()
        }

        // 加载原生ad
        loadNativeAd()
    }

    private fun loadNativeAd() {
        loadNative(binding.adContainer, styleType = NativeAdStyleType.STANDARD, call = { isShow->

        })
    }

    private fun checkPermissionAndStartCamera() {
        permissionController.setCallback(this)
        if (permissionController.hasPermission()) {
            startCamera()
        } else {
            permissionController.requestPermission()
        }
    }

    private fun startCamera() {
        cameraController.startCamera(
            lifecycleOwner = this,
            previewView = binding.previewView,
            imageAnalyzer = scannerController.getImageAnalyzer(),
            analyzerExecutor = scannerController.getScanExecutor(),
            callback = this
        )
    }

    private fun startScanLineAnimation() {
        binding.scannerContainer.post {
            val scannerHeight = binding.scannerContainer.height
            scanLineAnimator = ObjectAnimator.ofFloat(
                binding.scanLine,
                "translationY",
                -scannerHeight.toFloat(),
                scannerHeight.toFloat()
            ).apply {
                duration = 3000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun stopScanLineAnimation() {
        scanLineAnimator?.cancel()
        scanLineAnimator = null
    }

    /**
     * 设置扫描框区域的捏合手势缩放
     * 在扫描框上捏合可以控制相机缩放，联动进度条
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupPinchZoom() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var lastSpan = 0f

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                lastSpan = detector.currentSpan
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentSpan = detector.currentSpan
                val spanDelta = currentSpan - lastSpan
                lastSpan = currentSpan

                // 根据手指间距变化调整缩放
                // spanDelta > 0 表示放大，< 0 表示缩小
                val sensitivity = 0.3f  // 灵敏度系数
                val progressDelta = (spanDelta / 100f * sensitivity * 100).toInt()

                val currentProgress = binding.zoomSlider.progress
                val newProgress = (currentProgress + progressDelta).coerceIn(0, 100)

                if (newProgress != currentProgress) {
                    binding.zoomSlider.progress = newProgress
                    model.setZoomLevel(newProgress / 100f)
                }

                return true
            }
        })

        // 在扫描框容器上设置触摸监听
        binding.scannerContainer.setOnTouchListener { _, event ->
            scaleGestureDetector?.onTouchEvent(event)
            true
        }
    }

    override fun initObserve() {
        model.isFlashOn.observe(this) { isOn ->
            cameraController.setTorch(isOn)
            binding.ivFlashLight.setImageResource(
                if (isOn) R.drawable.ic_flashlight_on else R.drawable.ic_flashlight_off
            )
        }

        model.zoomLevel.observe(this) { level ->
            cameraController.setZoomLevel(level)
        }
    }

    override fun initTag(): String {
        return TAG
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionController.handlePermissionResult(requestCode, grantResults)
    }

    // ==================== PermissionCallback 实现 ====================

    override fun onPermissionGranted() {
        startCamera()
    }

    override fun onPermissionDenied(shouldOpenSettings: Boolean) {
        showErrorDialog(
            title = getString(R.string.qrcode_camera_permission_title),
            subtitle = getString(R.string.qrcode_camera_permission_subtitle),
            buttonText = getString(R.string.qrcode_action_ok),
            onButtonClick = {
                if (shouldOpenSettings) {
                    permissionController.openAppSettings()
                }
                finish()
            }
        )
    }

    // ==================== CameraReadyCallback 实现 ====================

    override fun onCameraReady(camera: Camera, previewWidth: Int, previewHeight: Int) {
        Log.d(TAG, "Camera ready, preview size: ${previewWidth}x${previewHeight}")
        scannerController.setCameraController(
            camera,
            createZoomController(),
            previewWidth,
            previewHeight
        )
    }

    override fun onCameraError(error: String) {
        Log.e(TAG, "Camera error: $error")
        Toast.makeText(this, "Camera error: $error", Toast.LENGTH_SHORT).show()
    }

    /**
     * 创建缩放控制器
     */
    private fun createZoomController(): BarcodeScannerController.ZoomController {
        return object : BarcodeScannerController.ZoomController {
            override fun getCurrentZoom(): Float {
                return binding.zoomSlider.progress / 100f
            }

            override fun setZoom(zoom: Float, animate: Boolean, onComplete: (() -> Unit)?) {
                val targetProgress = (zoom * 100).toInt().coerceIn(0, 100)
                if (animate) {
                    smoothZoomTo(targetProgress, onComplete)
                } else {
                    binding.zoomSlider.progress = targetProgress
                    model.setZoomLevel(zoom)
                    onComplete?.invoke()
                }
            }
        }
    }

    /**
     * 平滑缩放到目标值
     */
    private fun smoothZoomTo(targetProgress: Int, onComplete: (() -> Unit)? = null) {
        val currentProgress = binding.zoomSlider.progress

        if (currentProgress == targetProgress) {
            onComplete?.invoke()
            return
        }

        ObjectAnimator.ofInt(binding.zoomSlider, "progress", currentProgress, targetProgress).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val progress = it.animatedValue as Int
                model.setZoomLevel(progress / 100f)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete?.invoke()
                }
            })
            start()
        }
    }

    // ==================== ScanResultCallback 实现 ====================

    override fun onScanSuccess(result: String, barcodeType: Int, typeName: String) {
        Log.d(TAG, "Scan result: $result, type: $typeName")

        // 先播放声音和震动反馈
        ScanFeedbackController.performScanFeedback(this)

        // 保存扫描结果到数据库
        lifecycleScope.launch {
            ScanHistoryRepository.getInstance(this@QRCodeScanActivity)
                .insert(result, barcodeType, typeName)
        }

        // 停止扫描线动画，显示成功弹框
        stopScanLineAnimation()
        binding.scanLine.visibility = android.view.View.INVISIBLE
        ScanSuccessDialog.show(this) {
            // 动画结束后跳转到扫描结果页
            ScanResultActivity.start(this, result, barcodeType, typeName)
        }
    }

    override fun onScanError(error: String) {
        showErrorDialog(
            title = getString(R.string.qrcode_scan_error_title),
            subtitle = error,
            buttonText = getString(R.string.qrcode_scan_try_again)
        )
    }

    override fun onNoBarcodeFound() {
        showErrorDialog(
            title = getString(R.string.qrcode_scan_failed_title),
            subtitle = getString(R.string.qrcode_scan_failed_subtitle),
            buttonText = getString(R.string.qrcode_scan_try_again)
        )
    }

    /**
     * 显示错误弹框
     * 暂停扫描 -> 显示弹框 -> 弹框消失后恢复扫描
     * @param title 标题
     * @param subtitle 副标题
     * @param buttonText 按钮文字
     * @param onButtonClick 按钮点击回调（可选）
     */
    private fun showErrorDialog(
        title: String,
        subtitle: String,
        buttonText: String,
        onButtonClick: (() -> Unit)? = null
    ) {
        scannerController.pauseScanning()
        ScanFailedDialog.show(
            context = this,
            title = title,
            subtitle = subtitle,
            buttonText = buttonText,
            onButtonClick = onButtonClick,
            onDismiss = {
                scannerController.resumeScanning()
            }
        )
    }

    override fun onResume() {
        super.onResume()
        // 恢复焦点时继续扫描
        scannerController.resumeScanning()
    }

    override fun onPause() {
        super.onPause()
        // 失去焦点时暂停扫描
        scannerController.pauseScanning()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanLineAnimation()
        zoomButtonController?.release()
        scannerController.release()
        cameraController.release()
        ScanFeedbackController.release()
    }
}
