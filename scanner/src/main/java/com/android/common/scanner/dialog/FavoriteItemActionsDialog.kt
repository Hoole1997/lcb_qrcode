package com.android.common.scanner.dialog

import android.content.Context
import android.view.View
import com.android.common.scanner.R
import com.android.common.scanner.data.entity.FavoriteEntity
import com.lxj.xpopup.core.BottomPopupView

class FavoriteItemActionsDialog(context: Context) : BottomPopupView(context) {

    private var entity: FavoriteEntity? = null
    private var onDeleteClick: ((FavoriteEntity) -> Unit)? = null
    private var onShareClick: ((FavoriteEntity) -> Unit)? = null

    override fun getImplLayoutId(): Int {
        return R.layout.dialog_history_item_actions
    }

    override fun onCreate() {
        super.onCreate()

        val layoutAddToFavorites = findViewById<View>(R.id.layoutAddToFavorites)
        val layoutDelete = findViewById<View>(R.id.layoutDelete)
        val layoutShare = findViewById<View>(R.id.layoutShare)
        val ivClose = findViewById<View>(R.id.ivClose)

        // 收藏页面不显示添加到收藏选项
        layoutAddToFavorites.visibility = View.GONE

        ivClose.setOnClickListener {
            dismiss()
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

    fun setEntity(entity: FavoriteEntity): FavoriteItemActionsDialog {
        this.entity = entity
        return this
    }

    fun setOnDeleteClick(listener: (FavoriteEntity) -> Unit): FavoriteItemActionsDialog {
        this.onDeleteClick = listener
        return this
    }

    fun setOnShareClick(listener: (FavoriteEntity) -> Unit): FavoriteItemActionsDialog {
        this.onShareClick = listener
        return this
    }

    companion object {
        fun show(
            context: Context,
            entity: FavoriteEntity,
            onDelete: ((FavoriteEntity) -> Unit)? = null,
            onShare: ((FavoriteEntity) -> Unit)? = null
        ) {
            com.lxj.xpopup.XPopup.Builder(context)
                .hasNavigationBar(false)
                .asCustom(
                    FavoriteItemActionsDialog(context)
                        .setEntity(entity)
                        .apply {
                            onDelete?.let { setOnDeleteClick(it) }
                            onShare?.let { setOnShareClick(it) }
                        }
                )
                .show()
        }
    }
}
