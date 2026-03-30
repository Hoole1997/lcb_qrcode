package com.android.common.scanner.controller

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.android.common.scanner.R

/**
 * 扫描反馈控制器
 * 管理扫描成功后的声音和震动反馈
 */
object ScanFeedbackController {

    private const val PREFS_NAME = "qrcode_settings"
    private const val KEY_BEEP = "beep_enabled"
    private const val KEY_VIBRATE = "vibrate_enabled"

    private var soundPool: SoundPool? = null
    private var beepSoundId: Int = 0
    private var isInitialized = false

    /**
     * 初始化声音资源
     */
    fun init(context: Context) {
        if (isInitialized) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()

        beepSoundId = soundPool?.load(context, R.raw.scan_beep, 1) ?: 0
        isInitialized = true
    }

    /**
     * 释放资源
     */
    fun release() {
        soundPool?.release()
        soundPool = null
        isInitialized = false
    }

    /**
     * 检查声音是否启用
     */
    fun isBeepEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BEEP, true)
    }

    /**
     * 检查震动是否启用
     */
    fun isVibrateEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VIBRATE, true)
    }

    /**
     * 设置声音启用状态
     */
    fun setBeepEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BEEP, enabled)
            .apply()
    }

    /**
     * 设置震动启用状态
     */
    fun setVibrateEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VIBRATE, enabled)
            .apply()
    }

    /**
     * 执行扫描成功反馈
     */
    fun performScanFeedback(context: Context) {
        if (isBeepEnabled(context)) {
            playBeep()
        }
        if (isVibrateEnabled(context)) {
            vibrate(context)
        }
    }

    /**
     * 播放提示音
     */
    private fun playBeep() {
        soundPool?.play(beepSoundId, 1f, 1f, 1, 0, 1f)
    }

    /**
     * 轻触短震动
     */
    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 轻触短震动 - 50ms
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }
}
