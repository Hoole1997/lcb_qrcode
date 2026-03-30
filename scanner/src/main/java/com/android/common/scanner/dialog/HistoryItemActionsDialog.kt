package com.android.common.scanner.dialog

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.android.common.scanner.R
import com.android.common.scanner.controller.FavoriteController
import com.android.common.scanner.data.entity.ScanHistoryEntity
import com.lxj.xpopup.core.BottomPopupView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryItemActionsDialog(context: Context) : BottomPopupView(context) {

    private var entity: ScanHistoryEntity? = null
    private var showAddToFavorites: Boolean = true
    private var isFavorited: Boolean = false
    private var onFavoriteToggled: ((ScanHistoryEntity, Boolean) -> Unit)? = null
    private var onDeleteClick: ((ScanHistoryEntity) -> Unit)? = null
    private var onShareClick: ((ScanHistoryEntity) -> Unit)? = null

    override fun getImplLayoutId(): Int {
        return R.layout.dialog_history_item_actions
    }

    override fun onCreate() {
        super.onCreate()

        val layoutAddToFavorites = findViewById<View>(R.id.layoutAddToFavorites)
        val ivFavorite = findViewById<ImageView>(R.id.ivFavorite)
        val tvFavorite = findViewById<TextView>(R.id.tvFavorite)
        val layoutDelete = findViewById<View>(R.id.layoutDelete)
        val layoutShare = findViewById<View>(R.id.layoutShare)
        val ivClose = findViewById<View>(R.id.ivClose)

        // 控制添加到收藏选项的显示
        layoutAddToFavorites.visibility = if (showAddToFavorites) View.VISIBLE else View.GONE

        // 检查收藏状态并更新UI
        entity?.let { item ->
            CoroutineScope(Dispatchers.Main).launch {
                isFavorited = FavoriteController.isFavorite(context, item.content)
                updateFavoriteUI(ivFavorite, tvFavorite)
            }
        }

        ivClose.setOnClickListener {
            dismiss()
        }

        layoutAddToFavorites.setOnClickListener {
            entity?.let { item ->
                CoroutineScope(Dispatchers.Main).launch {
                    val newState = FavoriteController.toggleFavorite(context, item)
                    onFavoriteToggled?.invoke(item, newState)
                    dismiss()
                }
            }
        }

        layoutDelete.setOnClickListener {
            entity?.let { onDeleteClick?.invoke(it) }
            dismiss()
        }

        layoutShare.setOnClickListener {
            entity?.let { onShareClick?.invoke(it) }
            dismiss()
        }
    }

    private fun updateFavoriteUI(ivFavorite: ImageView, tvFavorite: TextView) {
        if (isFavorited) {
            tvFavorite.text = context.getString(R.string.qrcode_action_remove_from_favorites)
            ivFavorite.setImageResource(R.drawable.qrcode_ic_favorite_filled)
            ivFavorite.clearColorFilter()
        } else {
            tvFavorite.text = context.getString(R.string.qrcode_action_add_to_favorites)
            ivFavorite.setImageResource(R.drawable.qrcode_ic_favorite_outline)
            ivFavorite.setColorFilter(ContextCompat.getColor(context, R.color.qrcode_favorite_inactive))
        }
    }

    fun setEntity(entity: ScanHistoryEntity): HistoryItemActionsDialog {
        this.entity = entity
        return this
    }

    fun setShowAddToFavorites(show: Boolean): HistoryItemActionsDialog {
        this.showAddToFavorites = show
        return this
    }

    fun setOnFavoriteToggled(listener: (ScanHistoryEntity, Boolean) -> Unit): HistoryItemActionsDialog {
        this.onFavoriteToggled = listener
        return this
    }

    fun setOnDeleteClick(listener: (ScanHistoryEntity) -> Unit): HistoryItemActionsDialog {
        this.onDeleteClick = listener
        return this
    }

    fun setOnShareClick(listener: (ScanHistoryEntity) -> Unit): HistoryItemActionsDialog {
        this.onShareClick = listener
        return this
    }

    companion object {
        fun show(
            context: Context,
            entity: ScanHistoryEntity,
            showAddToFavorites: Boolean = true,
            onFavoriteToggled: ((ScanHistoryEntity, Boolean) -> Unit)? = null,
            onDelete: ((ScanHistoryEntity) -> Unit)? = null,
            onShare: ((ScanHistoryEntity) -> Unit)? = null
        ) {
            com.lxj.xpopup.XPopup.Builder(context)
                .hasNavigationBar(false)
                .asCustom(
                    HistoryItemActionsDialog(context)
                        .setEntity(entity)
                        .setShowAddToFavorites(showAddToFavorites)
                        .apply {
                            onFavoriteToggled?.let { setOnFavoriteToggled(it) }
                            onDelete?.let { setOnDeleteClick(it) }
                            onShare?.let { setOnShareClick(it) }
                        }
                )
                .show()
        }
    }
}
