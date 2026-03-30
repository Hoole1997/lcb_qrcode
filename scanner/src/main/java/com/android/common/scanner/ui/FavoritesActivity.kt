package com.android.common.scanner.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.viewModels
import android.graphics.Color
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.common.bill.ui.NativeAdStyleType
import com.android.common.scanner.widget.InsetDividerItemDecoration
import com.android.common.scanner.R
import com.android.common.scanner.base.BaseActivity
import com.android.common.scanner.data.entity.FavoriteEntity
import com.android.common.scanner.databinding.ActivityFavoritesBinding
import com.android.common.scanner.dialog.FavoriteItemActionsDialog
import com.android.common.scanner.util.QRCodeShareUtils
import com.android.common.scanner.util.ScanItemNavigator
import com.android.common.scanner.util.loadNative
import com.gyf.immersionbar.ImmersionBar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavoritesActivity : BaseActivity<ActivityFavoritesBinding, FavoritesModel>() {

    companion object {
        private const val TAG = "FavoritesActivity"

        fun start(context: Context) {
            context.startActivity(Intent(context, FavoritesActivity::class.java))
        }
    }

    private lateinit var adapter: FavoritesAdapter

    override fun initBinding(): ActivityFavoritesBinding {
        return ActivityFavoritesBinding.inflate(layoutInflater)
    }

    override fun initModel(): FavoritesModel {
        return viewModels<FavoritesModel>().value
    }

    override fun initView() {
        ImmersionBar.with(this)
            .statusBarDarkFont(true)
            .navigationBarColor(android.R.color.white)
            .init()

        model.init(this)

        adapter = FavoritesAdapter(
            onItemClick = { entity ->
                ScanItemNavigator.openItem(
                    context = this,
                    typeName = entity.typeName,
                    content = entity.content,
                    barcodeType = entity.barcodeType,
                    extraData = entity.extraData
                )
            },
            onMoreClick = { entity ->
                showItemActionsDialog(entity)
            }
        )

        binding.rvFavorites.layoutManager = LinearLayoutManager(this)
        binding.rvFavorites.adapter = adapter

        // 添加分割线（带16dp左右边距）
        binding.rvFavorites.addItemDecoration(
            InsetDividerItemDecoration.create(this, 1f, Color.parseColor("#F8F8F8"), 16f)
        )

        binding.ivBack.setOnClickListener {
            finish()
        }

        loadNativeAd()
    }

    private fun loadNativeAd() {
        loadNative(binding.adContainer, styleType = NativeAdStyleType.STANDARD, call = { isShow->

        })
    }

    override fun onResume() {
        super.onResume()
        model.loadFavorites()
    }

    override fun initObserve() {
        lifecycleScope.launch {
            model.favoritesList.collectLatest { list ->
                adapter.submitList(list)
                binding.emptyView.isVisible = list.isEmpty()
                binding.rvFavorites.isVisible = list.isNotEmpty()
            }
        }
    }

    override fun initTag(): String {
        return TAG
    }

    private fun showItemActionsDialog(entity: FavoriteEntity) {
        FavoriteItemActionsDialog.show(
            context = this,
            entity = entity,
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
}
