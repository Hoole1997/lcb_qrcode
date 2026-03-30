package com.lcb.qrcode

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lcb.qrcode.databinding.ItemLanguageOptionBinding

class LanguageAdapter(
    private val items: List<LanguageOption>,
    selectedTag: String
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

    var selectedTag: String = selectedTag
        private set

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val binding = ItemLanguageOptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LanguageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class LanguageViewHolder(
        private val binding: ItemLanguageOptionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LanguageOption) {
            binding.tvLanguageName.text = item.label
            binding.radioLanguage.isChecked = item.tag == selectedTag

            binding.root.setOnClickListener {
                val previousIndex = items.indexOfFirst { option -> option.tag == selectedTag }
                selectedTag = item.tag
                if (previousIndex != -1) {
                    notifyItemChanged(previousIndex)
                }
                notifyItemChanged(bindingAdapterPosition)
            }
        }
    }
}
