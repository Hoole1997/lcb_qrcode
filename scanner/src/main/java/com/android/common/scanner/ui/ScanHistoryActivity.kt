package com.android.common.scanner.ui

import android.content.Context
import android.content.Intent
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import android.graphics.Color
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.common.bill.ui.NativeAdStyleType
import com.android.common.scanner.widget.InsetDividerItemDecoration
import com.android.common.scanner.R
import com.android.common.scanner.base.BaseActivity
import com.android.common.scanner.data.entity.ScanHistoryEntity
import com.android.common.scanner.databinding.ActivityScanHistoryBinding
import com.android.common.scanner.dialog.HistoryItemActionsDialog
import com.android.common.scanner.util.QRCodeShareUtils
import com.android.common.scanner.util.ScanItemNavigator
import com.android.common.scanner.util.loadNative
import com.gyf.immersionbar.ImmersionBar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ScanHistoryActivity : BaseActivity<ActivityScanHistoryBinding, ScanHistoryModel>() {

    companion object {
        private const val TAG = "ScanHistoryActivity"
        private const val TAB_ALL = 0
        private const val TAB_QRCODE = 1
        private const val TAB_URL = 2
        private const val TAB_DOCUMENT = 3

        fun start(context: Context) {
            context.startActivity(Intent(context, ScanHistoryActivity::class.java))
        }
    }

    private lateinit var adapter: ScanHistoryAdapter
    private var currentTab = TAB_ALL
    private var allHistoryList: List<ScanHistoryEntity> = emptyList()

    override fun initBinding(): ActivityScanHistoryBinding {
        return ActivityScanHistoryBinding.inflate(layoutInflater)
    }

    override fun initModel(): ScanHistoryModel {
        return viewModels<ScanHistoryModel>().value
    }

    override fun initView() {
        ImmersionBar.with(this)
            .statusBarDarkFont(true)
            .navigationBarColor(android.R.color.white)
            .init()

        model.init(this)

        adapter = ScanHistoryAdapter(
            onItemClick = { entity ->
                ScanItemNavigator.openItem(
                    context = this,
                    typeName = entity.typeName,
                    content = entity.content,
                    barcodeType = entity.barcodeType,
                    extraData = entity.extraData
                )
            },
            onDeleteClick = { entity ->
                showItemActionsDialog(entity)
            }
        )

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        // 添加分割线（带16dp左右边距）
        binding.rvHistory.addItemDecoration(
            InsetDividerItemDecoration.create(this, 1f, Color.parseColor("#F8F8F8"), 16f)
        )

        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.ivFavorite.setOnClickListener {
            FavoritesActivity.start(this)
        }

        // 空状态按钮点击
        binding.btnScanQRCode.setOnClickListener {
            QRCodeScanActivity.start(this)
            finish()
        }

        binding.btnScanBarcode.setOnClickListener {
            QRCodeScanActivity.start(this)
            finish()
        }

        binding.btnScanPdf.setOnClickListener {
            DocumentScannerActivity.start(this)
            finish()
        }

        setupTabs()
        loadNativeAd()
    }

    private fun setupTabs() {
        val tabs = listOf(binding.tabAll, binding.tabQrcode, binding.tabUrl, binding.tabDocument)

        tabs.forEachIndexed { index, tab ->
            tab.setOnClickListener {
                selectTab(index)
            }
        }

        // Set initial indicator position under first tab (no animation)
        binding.tabAll.post {
            updateIndicatorPosition(binding.tabAll, animate = false)
        }
    }

    private fun selectTab(tabIndex: Int) {
        if (currentTab == tabIndex) return
        currentTab = tabIndex

        val tabs = listOf(binding.tabAll, binding.tabQrcode, binding.tabUrl, binding.tabDocument)
        tabs.forEachIndexed { index, tab ->
            tab.setTextColor(if (index == tabIndex) Color.parseColor("#4594FF") else Color.parseColor("#666666"))
        }

        updateIndicatorPosition(tabs[tabIndex])
        filterAndDisplayList()
    }

    private fun updateIndicatorPosition(selectedTab: TextView, animate: Boolean = true) {
        selectedTab.post {
            // Reset translation first to get original indicator position
            val currentTranslation = binding.tabIndicator.translationX
            binding.tabIndicator.translationX = 0f

            val tabLocation = IntArray(2)
            val indicatorLocation = IntArray(2)
            selectedTab.getLocationOnScreen(tabLocation)
            binding.tabIndicator.getLocationOnScreen(indicatorLocation)

            // Restore translation for animation starting point
            binding.tabIndicator.translationX = currentTranslation

            // Calculate target translation to center indicator under tab
            val tabCenterX = tabLocation[0] + selectedTab.width / 2
            val indicatorCenterX = indicatorLocation[0] + binding.tabIndicator.width / 2
            val targetTranslation = (tabCenterX - indicatorCenterX).toFloat()

            if (animate) {
                binding.tabIndicator.animate()
                    .translationX(targetTranslation)
                    .setDuration(200)
                    .start()
            } else {
                binding.tabIndicator.translationX = targetTranslation
            }
        }
    }

    private fun filterAndDisplayList() {
        val filteredList = when (currentTab) {
            TAB_ALL -> allHistoryList
            TAB_QRCODE -> allHistoryList.filter { it.typeName.contains("QR", ignoreCase = true) }
            TAB_URL -> allHistoryList.filter { it.typeName.equals("URL", ignoreCase = true) || it.content.startsWith("http") }
            TAB_DOCUMENT -> allHistoryList.filter { it.typeName.equals("Document", ignoreCase = true) || it.typeName.equals("PDF", ignoreCase = true) }
            else -> allHistoryList
        }
        adapter.submitList(filteredList)

        val isEmpty = filteredList.isEmpty()
        binding.emptyView.isVisible = isEmpty
        binding.rvHistory.isVisible = !isEmpty

        if (isEmpty) {
            updateEmptyStateForTab()
        }
    }

    private fun updateEmptyStateForTab() {
        val isDocumentTab = currentTab == TAB_DOCUMENT
        val isQRCodeTab = currentTab == TAB_QRCODE
        val isAllTab = currentTab == TAB_ALL

        // Update text
        binding.tvEmptyTitle.setText(
            if (isDocumentTab) R.string.qrcode_history_document_empty_title
            else R.string.qrcode_history_empty_title
        )
        binding.tvEmptySubtitle.setText(
            if (isDocumentTab) R.string.qrcode_history_document_empty_subtitle
            else R.string.qrcode_history_empty_subtitle
        )

        // Show/hide buttons based on tab
        // All tab: QR code + Barcode + Document
        // QR code tab: QR code only (hide barcode)
        // URL tab: QR code + Barcode
        // Document tab: PDF only
        binding.btnScanQRCode.isVisible = !isDocumentTab
        binding.btnScanBarcode.isVisible = !isDocumentTab && !isQRCodeTab
        binding.btnScanPdf.isVisible = isDocumentTab || isAllTab
    }

    private fun loadNativeAd() {
        loadNative(binding.adContainer, styleType = NativeAdStyleType.STANDARD, call = { isShow->

        })
    }

    private fun showItemActionsDialog(entity: ScanHistoryEntity) {
        HistoryItemActionsDialog.show(
            context = this,
            entity = entity,
            showAddToFavorites = true,
            onFavoriteToggled = { _, _ ->
                // FavoriteController already handles toast
            },
            onDelete = { item ->
                model.deleteItem(item)
                Toast.makeText(this, R.string.qrcode_history_deleted, Toast.LENGTH_SHORT).show()
            },
            onShare = { item ->
                ScanItemNavigator.shareItem(
                    context = this,
                    typeName = item.typeName,
                    content = item.content,
                    barcodeType = item.barcodeType,
                    extraData = item.extraData
                )
            }
        )
    }

    override fun initObserve() {
        lifecycleScope.launch {
            model.historyList.collectLatest { list ->
                allHistoryList = list
                filterAndDisplayList()
            }
        }
    }

    override fun initTag(): String {
        return TAG
    }

}
