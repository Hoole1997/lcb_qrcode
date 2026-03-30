package com.android.common.scanner.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.common.scanner.databinding.ItemCreateCodeTypeBinding

class CreateCodeTypeAdapter(
    private val onItemClick: (CreateCodeType) -> Unit
) : ListAdapter<CreateCodeType, CreateCodeTypeAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCreateCodeTypeBinding.inflate(
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
        private val binding: ItemCreateCodeTypeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CreateCodeType) {
            binding.ivIcon.setImageResource(item.iconRes)
            binding.tvName.text = item.name

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<CreateCodeType>() {
        override fun areItemsTheSame(oldItem: CreateCodeType, newItem: CreateCodeType): Boolean {
            return oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: CreateCodeType, newItem: CreateCodeType): Boolean {
            return oldItem == newItem
        }
    }
}
