package com.android.common.scanner.controller

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.SPUtils
import com.hjq.permissions.XXPermissions

/**
 * 相机权限控制器
 * 处理相机权限请求、拒绝计数和跳转设置页逻辑
 */
class CameraPermissionController(private val activity: Activity) {

    companion object {
        const val REQUEST_CODE_CAMERA_PERMISSION = 10
        private const val SP_KEY_CAMERA_PERMISSION_DENIED_COUNT = "camera_permission_denied_count"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    /**
     * 权限回调接口
     */
    interface PermissionCallback {
        fun onPermissionGranted()
        fun onPermissionDenied(shouldOpenSettings: Boolean)
    }

    private var callback: PermissionCallback? = null

    /**
     * 设置权限回调
     */
    fun setCallback(callback: PermissionCallback) {
        this.callback = callback
    }

    /**
     * 检查是否已授权相机权限
     */
    fun hasPermission(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 请求相机权限
     */
    fun requestPermission() {
        ActivityCompat.requestPermissions(
            activity,
            REQUIRED_PERMISSIONS,
            REQUEST_CODE_CAMERA_PERMISSION
        )
    }

    /**
     * 处理权限请求结果
     * @return true 如果已处理该请求码
     */
    fun handlePermissionResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode != REQUEST_CODE_CAMERA_PERMISSION) {
            return false
        }

        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            // 授权成功，重置拒绝次数
            resetDeniedCount()
            callback?.onPermissionGranted()
        } else {
            // 记录拒绝次数
            incrementDeniedCount()
            val shouldOpenSettings = getDeniedCount() > 2
            callback?.onPermissionDenied(shouldOpenSettings)
        }
        return true
    }

    /**
     * 获取拒绝次数
     */
    fun getDeniedCount(): Int {
        return SPUtils.getInstance().getInt(SP_KEY_CAMERA_PERMISSION_DENIED_COUNT, 0)
    }

    /**
     * 增加拒绝次数
     */
    private fun incrementDeniedCount() {
        val count = getDeniedCount() + 1
        SPUtils.getInstance().put(SP_KEY_CAMERA_PERMISSION_DENIED_COUNT, count)
    }

    /**
     * 重置拒绝次数
     */
    private fun resetDeniedCount() {
        SPUtils.getInstance().put(SP_KEY_CAMERA_PERMISSION_DENIED_COUNT, 0)
    }

    /**
     * 打开应用权限设置页面
     * 优先尝试打开权限管理页面，失败则回退到应用详情页
     */
    fun openAppSettings() {
        XXPermissions.startPermissionActivity(activity, arrayOf(Manifest.permission.CAMERA))
    }
}
