package com.touka.lcb.qrcode

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.android.common.scanner.controller.ScanFeedbackController
import com.android.common.scanner.R as ScannerR
import com.touka.lcb.qrcode.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PRIVACY_URL = "https://devs343.com/privacy.html"

        fun start(context: Context) {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        }
    }

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        binding.layoutPrivacy.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
        }
    }
}

