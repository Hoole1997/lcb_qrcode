package net.corekit.core.ext

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import net.corekit.core.utils.ActivityLauncher
import kotlin.apply

fun Context.isDefaultLauncher(): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        val currentHomePackage = resolveInfo?.activityInfo?.packageName
        currentHomePackage == packageName
    } catch (e: Exception) {
        Log.e("DefaultLauncherExt", "检查默认桌面异常", e)
        false
    }
}

/**
 * 检查是否可以发送通知（权限 + 系统设置）
 */
fun Context.canSendNotification(): Boolean {
    // 检查权限
    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true // Android 13 以下默认有权限
    }

    // 检查系统设置
    val isEnabled = try {
        NotificationManagerCompat.from(this).areNotificationsEnabled()
    } catch (e: Exception) {
        true // 异常时默认返回 true
    }

    return hasPermission && isEnabled
}

/**
 * 请求通知权限
 * @param launcher ActivityLauncher 实例
 * @param result 权限结果回调
 */
fun Context. requestNotificationPermission(
    launcher: ActivityLauncher,
    result: (flag: Boolean) -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+ 需要请求通知权限
        launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) { permissions ->
            val isGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
            result(isGranted)
        }
    } else {
        // Android 13 以下版本默认有权限，但需要检查系统设置
        result(canSendNotification())
    }
}

/**
 * 跳转到系统通知设置页面
 * @param launcher ActivityLauncher 实例
 * @param result 设置结果回调
 */
fun Context.openNotificationSettings(
    launcher: ActivityLauncher,
    result: (flag: Boolean) -> Unit
) {
    val intent = Intent().apply {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                // Android 8.0+ 使用应用通知设置
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            else -> {
                // Android 8.0 以下使用通用通知设置
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", packageName, null)
            }
        }
    }

    launcher.launch(intent) { activityResult ->
        // 检查设置后的状态
        result(canSendNotification())
    }
}
