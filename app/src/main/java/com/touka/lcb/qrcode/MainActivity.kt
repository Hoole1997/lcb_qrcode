package com.touka.lcb.qrcode

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.WindowInsetsCompat
import com.android.common.bill.ui.NativeAdStyleType
import com.android.common.scanner.ui.DocumentScannerActivity
import com.android.common.scanner.ui.OpenBarcodeScanActivity
import com.android.common.scanner.ui.QRCodeScanActivity
import com.android.common.scanner.ui.ScanHistoryActivity
import com.android.common.scanner.util.loadNative
import com.touka.lcb.qrcode.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        bindActions()
        loadNativeAd()
    }

    override fun onResume() {
        super.onResume()
        loadNativeAd()
    }

    private fun bindActions() = with(binding) {
        cardQrScan.setOnClickListener { QRCodeScanActivity.start(this@MainActivity) }
        cardBarcodeScan.setOnClickListener {
            startActivity(Intent(this@MainActivity, OpenBarcodeScanActivity::class.java))
        }
        cardPdfScan.setOnClickListener { DocumentScannerActivity.start(this@MainActivity) }
        cardHistory.setOnClickListener { ScanHistoryActivity.start(this@MainActivity) }
        btnSettings.setOnClickListener { SettingsActivity.start(this@MainActivity) }
    }

    private fun applyWindowInsets() {
        val baseTopMargin = (binding.headerContainer.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            binding.headerContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = baseTopMargin + statusBarInsets.top
            }
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun loadNativeAd() {
        loadNative(binding.nativeAdContainer, styleType = NativeAdStyleType.STANDARD) { shown ->
            binding.adSection.isVisible = shown
            binding.nativeAdContainer.isVisible = shown
        }
    }
}
