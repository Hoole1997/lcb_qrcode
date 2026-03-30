package com.lcb.qrcode

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.lcb.qrcode.databinding.ActivityLanguageBinding

class LanguageActivity : AppCompatActivity() {

    companion object {
        private const val SYSTEM_LANGUAGE_TAG = "system"

        fun start(context: Context) {
            context.startActivity(Intent(context, LanguageActivity::class.java))
        }
    }

    private lateinit var binding: ActivityLanguageBinding
    private lateinit var languageAdapter: LanguageAdapter

    private val languageOptions = listOf(
        LanguageOption(SYSTEM_LANGUAGE_TAG, "System default"),
        LanguageOption("en", "English"),
        LanguageOption("zh-CN", "简体中文"),
        LanguageOption("zh-TW", "繁體中文（台灣）"),
        LanguageOption("zh-HK", "繁體中文（香港）"),
        LanguageOption("zh-MO", "繁體中文（澳門）"),
        LanguageOption("ja", "日本語"),
        LanguageOption("ko", "한국어"),
        LanguageOption("th", "ไทย"),
        LanguageOption("vi", "Tiếng Việt"),
        LanguageOption("id", "Bahasa Indonesia"),
        LanguageOption("de", "Deutsch"),
        LanguageOption("es", "Español"),
        LanguageOption("fr", "Français"),
        LanguageOption("hi", "हिन्दी"),
        LanguageOption("ru", "Русский"),
        LanguageOption("pt-BR", "Português (Brasil)")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        bindToolbar()
        bindList()
        bindActions()
    }

    private fun bindToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun bindList() {
        languageAdapter = LanguageAdapter(
            items = languageOptions,
            selectedTag = currentLanguageTag()
        )

        binding.rvLanguage.apply {
            layoutManager = LinearLayoutManager(this@LanguageActivity)
            adapter = languageAdapter
        }
    }

    private fun bindActions() {
        binding.btnConfirm.setOnClickListener {
            val selectedTag = languageAdapter.selectedTag
            if (selectedTag == currentLanguageTag()) {
                finish()
                return@setOnClickListener
            }

            val locales = if (selectedTag == SYSTEM_LANGUAGE_TAG) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(selectedTag)
            }
            AppCompatDelegate.setApplicationLocales(locales)

            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
            finishAffinity()
        }
    }

    private fun applyWindowInsets() {
        val toolbarBaseTopPadding = binding.toolbar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = toolbarBaseTopPadding + statusBarInsets.top)
            insets
        }

        val actionBasePadding = binding.bottomActionContainer.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomActionContainer) { view, insets ->
            val navigationInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = actionBasePadding + navigationInsets.bottom)
            insets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun currentLanguageTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return locales[0]?.toLanguageTag() ?: SYSTEM_LANGUAGE_TAG
    }
}

data class LanguageOption(
    val tag: String,
    val label: String
)
