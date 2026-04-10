package com.android.common.bill.ads.bidding

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.android.common.bill.R

/**
 * 广告聚合源选择底部弹窗
 */
class AdSourceSelectionBottomSheet(
    private val context: Context,
    private val currentSource: AdSourceController.AdSource,
    private val onSourceSelected: (AdSourceController.AdSource) -> Unit
) : BottomSheetDialog(context) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SourceAdapter

    init {
        setupDialog()
    }

    private fun setupDialog() {
        val layout = LayoutInflater.from(context).inflate(R.layout.dialog_ad_source_selection, null)
        setContentView(layout)
        
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        initViews(layout)
        setupRecyclerView()
    }

    private fun initViews(layout: View) {
        recyclerView = layout.findViewById(R.id.rv_sources)
    }

    private fun setupRecyclerView() {
        val sources = AdSourceController.getAllSources()
        adapter = SourceAdapter(sources, currentSource) { source ->
            onSourceSelected(source)
            dismiss()
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    /**
     * 源选择适配器
     */
    private class SourceAdapter(
        private val sources: List<AdSourceController.AdSource>,
        private val currentSource: AdSourceController.AdSource,
        private val onItemClick: (AdSourceController.AdSource) -> Unit
    ) : RecyclerView.Adapter<SourceAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tv_source_name)
            val tvCheck: TextView = itemView.findViewById(R.id.tv_check)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ad_source, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val source = sources[position]
            val displayName = AdSourceController.getSourceDisplayName(source)
            holder.tvName.text = displayName
            
            // 显示选中状态
            val isSelected = source == currentSource
            holder.tvCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            holder.itemView.setOnClickListener {
                onItemClick(source)
            }
        }

        override fun getItemCount() = sources.size
    }

    companion object {
        /**
         * 显示聚合源选择弹窗
         */
        fun show(
            context: Context,
            currentSource: AdSourceController.AdSource,
            onSourceSelected: (AdSourceController.AdSource) -> Unit
        ) {
            val dialog = AdSourceSelectionBottomSheet(context, currentSource, onSourceSelected)
            dialog.show()
        }
    }
}

