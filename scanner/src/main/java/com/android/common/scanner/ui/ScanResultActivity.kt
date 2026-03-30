package com.android.common.scanner.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.ui.NativeAdStyleType
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.IntentUtils
import com.android.common.scanner.R
import com.android.common.scanner.base.BaseActivity
import com.android.common.scanner.controller.FavoriteController
import com.android.common.scanner.data.entity.ScanHistoryEntity
import com.android.common.scanner.databinding.ActivityScanResultBinding
import com.android.common.scanner.dialog.ViewQRDialog
import com.android.common.scanner.util.BarcodeTypeUtils
import com.android.common.scanner.util.loadInterstitial
import com.android.common.scanner.util.loadNative
import com.gyf.immersionbar.ImmersionBar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanResultActivity : BaseActivity<ActivityScanResultBinding, ScanResultModel>() {

    companion object {
        private const val TAG = "ScanResultActivity"
        const val EXTRA_SCAN_RESULT = "scan_result"
        const val EXTRA_BARCODE_TYPE = "barcode_type"
        const val EXTRA_TYPE_NAME = "type_name"

        fun start(context: Context, result: String, barcodeType: Int, typeName: String) {
            val intent = Intent(context, ScanResultActivity::class.java).apply {
                putExtra(EXTRA_SCAN_RESULT, result)
                putExtra(EXTRA_BARCODE_TYPE, barcodeType)
                putExtra(EXTRA_TYPE_NAME, typeName)
            }
            context.startActivity(intent)
        }
    }

    private var scanResult: String = ""
    private var barcodeType: Int = 0
    private var typeName: String = ""
    private var isFavorited: Boolean = false

    override fun initBinding(): ActivityScanResultBinding {
        return ActivityScanResultBinding.inflate(layoutInflater)
    }

    override fun initModel(): ScanResultModel {
        return viewModels<ScanResultModel>().value
    }

    override fun initView() {
        // 设置沉浸式状态栏
        ImmersionBar.with(this)
            .statusBarDarkFont(true)
            .navigationBarColor(android.R.color.white)
            .init()

        // 获取传递的数据
        scanResult = intent.getStringExtra(EXTRA_SCAN_RESULT) ?: ""
        barcodeType = intent.getIntExtra(EXTRA_BARCODE_TYPE, 0)
        typeName = intent.getStringExtra(EXTRA_TYPE_NAME) ?: "QR_CODE"

        // 设置内容
        binding.tvContent.text = scanResult
        binding.tvType.text = typeName
        binding.tvTime.text = getCurrentTime()

        // 设置类型图标
        binding.ivTypeIcon.setImageResource(BarcodeTypeUtils.getTypeIcon(barcodeType, scanResult))

        // 检查是否已收藏
        checkFavoriteStatus()

        // 返回按钮
        binding.ivBack.setOnClickListener {
            finish()
        }

        // 收藏按钮
        binding.ivFavorite.setOnClickListener {
            toggleFavorite()
        }

        // View QR 按钮
        binding.btnViewQR.setOnClickListener {
            ViewQRDialog.show(this, scanResult, barcodeType)
        }

        // Share 按钮
        binding.btnShare.setOnClickListener {
            shareResult()
        }

        // Search Web / Open URL 按钮
        updateSearchButton()
        binding.btnSearchWeb.setOnClickListener {
            if (isUrl(scanResult)) {
                openUrl()
            } else {
                searchWeb()
            }
        }

        loadNativeAd()

    }

    private fun loadNativeAd() {
        loadNative(binding.adContainer, styleType = NativeAdStyleType.STANDARD, call = { isShow->

        })
    }


    override fun initObserve() {
        // 暂无需要观察的数据
    }

    override fun initTag(): String {
        return TAG
    }

    private fun getCurrentTime(): String {
        val dateFormat = SimpleDateFormat("dd MMMM yyyy | hh:mm a", Locale.ENGLISH)
        return dateFormat.format(Date())
    }

    private fun shareResult() {
        startActivity(IntentUtils.getShareTextIntent(scanResult))
    }

    private fun searchWeb() {
        try {
            val searchUrl = "https://www.google.com/search?q=${Uri.encode(scanResult)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open browser", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openUrl() {
        try {
            var url = scanResult
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open URL", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isUrl(text: String): Boolean {
        val urlPattern = "^(https?://)?([\\w-]+\\.)+[\\w-]+(/[\\w-./?%&=]*)?$"
        return text.matches(Regex(urlPattern, RegexOption.IGNORE_CASE)) ||
               text.startsWith("http://") ||
               text.startsWith("https://")
    }

    private fun updateSearchButton() {
        if (isUrl(scanResult)) {
            binding.btnSearchWeb.text = getString(R.string.qrcode_result_open_url)
            binding.btnSearchWeb.setIconResource(R.drawable.qrcode_ic_link)
        } else {
            binding.btnSearchWeb.text = getString(R.string.qrcode_result_search_web)
            binding.btnSearchWeb.setIconResource(R.drawable.qrcode_ic_search)
        }
    }

    private fun toggleFavorite() {
        lifecycleScope.launch {
            val entity = ScanHistoryEntity(
                content = scanResult,
                barcodeType = barcodeType,
                typeName = typeName
            )
            isFavorited = FavoriteController.toggleFavorite(this@ScanResultActivity, entity)
            updateFavoriteIcon()
        }
    }

    private fun checkFavoriteStatus() {
        lifecycleScope.launch {
            isFavorited = FavoriteController.isFavorite(this@ScanResultActivity, scanResult)
            updateFavoriteIcon()
        }
    }

    private fun updateFavoriteIcon() {
        if (isFavorited) {
            binding.ivFavorite.setImageResource(R.drawable.qrcode_ic_favorite_filled)
            binding.ivFavorite.clearColorFilter()
        } else {
            binding.ivFavorite.setImageResource(R.drawable.qrcode_ic_favorite_outline)
            binding.ivFavorite.setColorFilter(ContextCompat.getColor(this, R.color.qrcode_favorite_inactive))
        }
    }

    override fun finish() {
        loadInterstitial {
            super.finish()
            ActivityUtils.finishActivity(QRCodeScanActivity::class.java)
        }
    }
}
