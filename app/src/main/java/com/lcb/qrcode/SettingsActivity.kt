package com.lcb.qrcode

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.android.common.scanner.controller.ScanFeedbackController
import com.android.common.scanner.R as ScannerR
import com.lcb.qrcode.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        }
    }

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        binding.toolbar.apply {
            setNavigationIcon(ScannerR.drawable.ic_arrow_back)
            setNavigationOnClickListener { finish() }
        }

        binding.switchBeep.isChecked = ScanFeedbackController.isBeepEnabled(this)
        binding.switchVibrate.isChecked = ScanFeedbackController.isVibrateEnabled(this)

        binding.switchBeep.setOnCheckedChangeListener { _, checked ->
            ScanFeedbackController.setBeepEnabled(this, checked)
        }
        binding.switchVibrate.setOnCheckedChangeListener { _, checked ->
            ScanFeedbackController.setVibrateEnabled(this, checked)
        }

        binding.layoutLanguage.setOnClickListener {
            LanguageActivity.start(this)
        }
        binding.layoutPrivacy.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)))
        }
    }

    override fun onResume() {
        super.onResume()
        binding.tvLanguageValue.text = currentLanguageLabel()
    }

    private fun applyWindowInsets() {
        val baseTopPadding = binding.toolbar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = baseTopPadding + statusBarInsets.top)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun currentLanguageLabel(): String {
        return when (AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag()) {
            null -> "System default"
            "en" -> "English"
            "zh-CN" -> "简体中文"
            "zh-TW" -> "繁體中文（台灣）"
            "zh-HK" -> "繁體中文（香港）"
            "zh-MO" -> "繁體中文（澳門）"
            "ja" -> "日本語"
            "ko" -> "한국어"
            "th" -> "ไทย"
            "vi" -> "Tiếng Việt"
            "id" -> "Bahasa Indonesia"
            "de" -> "Deutsch"
            "es" -> "Español"
            "fr" -> "Français"
            "hi" -> "हिन्दी"
            "ru" -> "Русский"
            "pt-BR" -> "Português (Brasil)"
            else -> LocaleListCompat.getAdjustedDefault()[0]?.displayName ?: "System default"
        }
    }
}
