package com.ai.phoneagent.updates

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ai.phoneagent.feature.updates.R

/**
 * 更新下载链接列表适配器，每项展示「名称 + 地址」并提供打开/复制操作。
 *
 * @property items 名称与 URL 的键值对列表。
 * @property onOpen 打开链接的回调。
 * @property onCopy 复制链接的回调。
 */
class UpdateLinkAdapter(
    private val items: List<Pair<String, String>>,
    private val onOpen: (String) -> Unit,
    private val onCopy: (String) -> Unit,
) : RecyclerView.Adapter<UpdateLinkAdapter.VH>() {

    /** 单条链接的 ViewHolder。 */
    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvUrl: TextView = itemView.findViewById(R.id.tvUrl)
        val btnCopy: ImageView = itemView.findViewById(R.id.btnCopy)
        val btnOpen: ImageView = itemView.findViewById(R.id.btnOpen)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_update_link, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (name, url) = items[position]
        holder.tvName.text = name
        holder.tvUrl.text = url

        holder.itemView.setOnClickListener { onOpen(url) }
        holder.btnOpen.setOnClickListener { onOpen(url) }
        holder.btnCopy.setOnClickListener { onCopy(url) }
    }

    override fun getItemCount(): Int = items.size
}
