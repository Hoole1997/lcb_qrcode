package com.android.common.scanner.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.common.scanner.R
import com.android.common.scanner.data.entity.FavoriteEntity
import com.android.common.scanner.databinding.ItemScanHistoryBinding
import com.android.common.scanner.util.BarcodeTypeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FavoritesAdapter(
    private val onItemClick: (FavoriteEntity) -> Unit,
    private val onMoreClick: (FavoriteEntity) -> Unit
) : ListAdapter<FavoriteEntity, FavoritesAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScanHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemScanHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FavoriteEntity) {
            binding.tvContent.text = "${item.typeName}:${item.content}"
            binding.tvTime.text = formatTime(item.addTime)

            if (item.typeName.equals("PDF", ignoreCase = true)) {
                binding.ivTypeIcon.setBackgroundResource(R.drawable.qrcode_ic_pdf)
                binding.ivTypeIcon.setImageResource(0)
            } else{
                binding.ivTypeIcon.setBackgroundResource(R.drawable.qrcode_bg_scan_type_icon)
                binding.ivTypeIcon.setImageResource(
                    BarcodeTypeUtils.getTypeIcon(item.barcodeType, item.content, item.typeName)
                )
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }

            binding.ivMore.setOnClickListener {
                onMoreClick(item)
            }
        }

        private fun formatTime(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("dd MMMM yyyy | hh:mm a", Locale.ENGLISH)
            return dateFormat.format(Date(timestamp))
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FavoriteEntity>() {
        override fun areItemsTheSame(oldItem: FavoriteEntity, newItem: FavoriteEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FavoriteEntity, newItem: FavoriteEntity): Boolean {
            return oldItem == newItem
        }
    }
}
