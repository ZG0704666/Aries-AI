package com.ai.phoneagent.updates

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ai.phoneagent.R
import com.google.android.material.button.MaterialButton

class ReleaseHistoryAdapter(
    private val items: MutableList<ReleaseEntry> = mutableListOf(),
    private val onDetails: (ReleaseEntry) -> Unit,
    private val onOpenRelease: (ReleaseEntry) -> Unit,
    private val onDownload: (ReleaseEntry) -> Unit,
) : RecyclerView.Adapter<ReleaseHistoryAdapter.VH>() {

    fun submitList(newItems: List<ReleaseEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun appendList(more: List<ReleaseEntry>) {
        if (more.isEmpty()) return
        val start = items.size
        items.addAll(more)
        notifyItemRangeInserted(start, more.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_release_history, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvVersion: TextView = itemView.findViewById(R.id.tvVersion)
        private val tvPrerelease: TextView = itemView.findViewById(R.id.tvPrerelease)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val btnDetails: MaterialButton = itemView.findViewById(R.id.btnDetails)
        private val btnOpen: MaterialButton = itemView.findViewById(R.id.btnOpen)
        private val btnDownload: MaterialButton = itemView.findViewById(R.id.btnDownload)

        fun bind(entry: ReleaseEntry) {
            tvVersion.text = entry.versionTag
            tvTitle.text = entry.title
            tvDate.text = entry.date

            tvPrerelease.visibility = if (entry.isPrerelease) View.VISIBLE else View.GONE

            btnDetails.setOnClickListener { onDetails(entry) }
            btnOpen.setOnClickListener { onOpenRelease(entry) }

            if (entry.apkUrl.isNullOrBlank()) {
                btnDownload.text = "打开"
            } else {
                btnDownload.text = "下载"
            }

            btnDownload.setOnClickListener { onDownload(entry) }
        }
    }
}
