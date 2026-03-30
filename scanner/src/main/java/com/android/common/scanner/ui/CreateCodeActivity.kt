package com.android.common.scanner.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.activity.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.android.common.scanner.R
import com.android.common.scanner.base.BaseActivity
import com.android.common.scanner.base.BaseModel
import com.android.common.scanner.databinding.ActivityCreateCodeBinding
import com.gyf.immersionbar.ImmersionBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CreateCodeActivity : BaseActivity<ActivityCreateCodeBinding, BaseModel>() {

    companion object {
        private const val TAG = "CreateCodeActivity"

        fun start(context: Context) {
            val intent = Intent(context, CreateCodeActivity::class.java)
            context.startActivity(intent)
        }
    }

    private var isQrCodeTab = true
    private lateinit var adapter: CreateCodeTypeAdapter

    override fun initBinding(): ActivityCreateCodeBinding {
        return ActivityCreateCodeBinding.inflate(layoutInflater)
    }

    override fun initModel(): BaseModel {
        return viewModels<BaseModel>().value
    }

    override fun initView() {
        ImmersionBar.with(this)
            .statusBarDarkFont(true)
            .navigationBarColor(android.R.color.white)
            .init()

        binding.ivBack.setOnClickListener {
            finish()
        }

        setupTabs()
        setupRecyclerView()
        checkClipboard()
    }

    private fun setupTabs() {
        binding.tabQrCode.setOnClickListener {
            if (!isQrCodeTab) {
                isQrCodeTab = true
                updateTabState()
                updateGridItems()
            }
        }

        binding.tabBarcode.setOnClickListener {
            if (isQrCodeTab) {
                isQrCodeTab = false
                updateTabState()
                updateGridItems()
            }
        }
    }

    private fun updateTabState() {
        if (isQrCodeTab) {
            binding.tabQrCode.setBackgroundResource(R.drawable.qrcode_bg_tab_selected)
            binding.tabQrCode.setTextColor(resources.getColor(android.R.color.white, null))
            binding.tabQrCode.textSize = 15f

            binding.tabBarcode.setBackgroundResource(android.R.color.transparent)
            binding.tabBarcode.setTextColor(resources.getColor(R.color.qrcode_text_dark, null))
            binding.tabBarcode.textSize = 14f

            binding.tvSectionTitle.text = getString(R.string.qrcode_create_standard_qr_codes)
        } else {
            binding.tabBarcode.setBackgroundResource(R.drawable.qrcode_bg_tab_selected)
            binding.tabBarcode.setTextColor(resources.getColor(android.R.color.white, null))
            binding.tabBarcode.textSize = 15f

            binding.tabQrCode.setBackgroundResource(android.R.color.transparent)
            binding.tabQrCode.setTextColor(resources.getColor(R.color.qrcode_text_dark, null))
            binding.tabQrCode.textSize = 14f

            binding.tvSectionTitle.text = getString(R.string.qrcode_create_standard_barcodes)
        }
    }

    private fun setupRecyclerView() {
        adapter = CreateCodeTypeAdapter { item ->
            onItemClick(item)
        }

        val spacing = resources.getDimensionPixelSize(R.dimen.qrcode_grid_spacing)
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.addItemDecoration(GridSpacingItemDecoration(2, spacing, true))
        binding.recyclerView.itemAnimator = null
        binding.recyclerView.adapter = adapter

        updateGridItems()
    }

    private fun updateGridItems() {
        val items = if (isQrCodeTab) getQrCodeTypes() else getBarcodeTypes()
        adapter.submitList(items)
    }

    private fun getQrCodeTypes(): List<CreateCodeType> {
        return listOf(
            CreateCodeType(CreateCodeTypeEnum.WEBSITE, R.drawable.qrcode_ic_link, getString(R.string.qrcode_create_type_website)),
            CreateCodeType(CreateCodeTypeEnum.WIFI, R.drawable.qrcode_ic_wifi, getString(R.string.qrcode_create_type_wifi)),
            CreateCodeType(CreateCodeTypeEnum.CONTACT, R.drawable.qrcode_ic_contact, getString(R.string.qrcode_create_type_contact)),
            CreateCodeType(CreateCodeTypeEnum.PHONE, R.drawable.qrcode_ic_phone, getString(R.string.qrcode_create_type_phone)),
            CreateCodeType(CreateCodeTypeEnum.EMAIL, R.drawable.qrcode_ic_mail, getString(R.string.qrcode_create_type_email)),
            CreateCodeType(CreateCodeTypeEnum.MY_CARD, R.drawable.qrcode_ic_id_card, getString(R.string.qrcode_create_type_my_card)),
            CreateCodeType(CreateCodeTypeEnum.TEXT, R.drawable.qrcode_ic_text, getString(R.string.qrcode_create_type_text)),
            CreateCodeType(CreateCodeTypeEnum.SMS, R.drawable.qrcode_ic_sms, getString(R.string.qrcode_create_type_sms)),
            CreateCodeType(CreateCodeTypeEnum.EVENT, R.drawable.qrcode_ic_event, getString(R.string.qrcode_create_type_event)),
            CreateCodeType(CreateCodeTypeEnum.LOCATION, R.drawable.qrcode_ic_location, getString(R.string.qrcode_create_type_location)),
            CreateCodeType(CreateCodeTypeEnum.BOOKMARK, R.drawable.qrcode_ic_book_mark, getString(R.string.qrcode_create_type_bookmark)),
            CreateCodeType(CreateCodeTypeEnum.APP, R.drawable.qrcode_ic_app, getString(R.string.qrcode_create_type_app))
        )
    }

    private fun getBarcodeTypes(): List<CreateCodeType> {
        return listOf(
            CreateCodeType(CreateCodeTypeEnum.TEXT, R.drawable.qrcode_ic_text, getString(R.string.qrcode_create_type_text)),
            CreateCodeType(CreateCodeTypeEnum.WEBSITE, R.drawable.qrcode_ic_link, getString(R.string.qrcode_create_type_website))
        )
    }

    private fun checkClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip

        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()
            if (!text.isNullOrBlank()) {
                binding.cardClipboard.visibility = View.VISIBLE
                binding.tvClipboardContent.text = text
                binding.tvClipboardTime.text = getCurrentTime()

                binding.cardClipboard.setOnClickListener {
                    onClipboardClick(text)
                }
            } else {
                binding.cardClipboard.visibility = View.GONE
            }
        } else {
            binding.cardClipboard.visibility = View.GONE
        }
    }

    private fun getCurrentTime(): String {
        val dateFormat = SimpleDateFormat("dd MMMM yyyy | hh:mm a", Locale.ENGLISH)
        return dateFormat.format(Date())
    }

    private fun onItemClick(item: CreateCodeType) {
        // TODO: 跳转到对应的创建页面
        when (item.type) {
            CreateCodeTypeEnum.WEBSITE -> {}
            CreateCodeTypeEnum.WIFI -> {}
            CreateCodeTypeEnum.CONTACT -> {}
            CreateCodeTypeEnum.PHONE -> {}
            CreateCodeTypeEnum.EMAIL -> {}
            CreateCodeTypeEnum.MY_CARD -> {}
            CreateCodeTypeEnum.TEXT -> {}
            CreateCodeTypeEnum.SMS -> {}
            CreateCodeTypeEnum.EVENT -> {}
            CreateCodeTypeEnum.LOCATION -> {}
            CreateCodeTypeEnum.BOOKMARK -> {}
            CreateCodeTypeEnum.APP -> {}
        }
    }

    private fun onClipboardClick(content: String) {
        // TODO: 使用剪贴板内容创建二维码
    }

    override fun initObserve() {}

    override fun initTag(): String = TAG
}

enum class CreateCodeTypeEnum {
    WEBSITE,
    WIFI,
    CONTACT,
    PHONE,
    EMAIL,
    MY_CARD,
    TEXT,
    SMS,
    EVENT,
    LOCATION,
    BOOKMARK,
    APP
}

data class CreateCodeType(
    val type: CreateCodeTypeEnum,
    val iconRes: Int,
    val name: String
)
