package com.ai.phoneagent.updates

import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ai.phoneagent.feature.updates.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * 更新历史列表适配器。
 *
 * @property onDetails 查看详细变更的回调。
 * @property onOpenRelease 在浏览器中打开发布页的回调。
 * @property onDownload 触发下载的回调。
 */
class ReleaseHistoryAdapter(
    private val items: MutableList<ReleaseEntry> = mutableListOf(),
    private val onDetails: (ReleaseEntry) -> Unit,
    private val onOpenRelease: (ReleaseEntry) -> Unit,
    private val onDownload: (ReleaseEntry) -> Unit,
) : RecyclerView.Adapter<ReleaseHistoryAdapter.VH>() {

    companion object {
        /** 点击防抖间隔，避免快速连点重复触发。 */
        private const val CLICK_THROTTLE_MS = 600L
    }

    /** 用新列表整体替换当前数据。 */
    fun submitList(newItems: List<ReleaseEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    /** 追加一页数据，仅刷新新增区间。 */
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

    /** 单条发布记录的 ViewHolder。 */
    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView as MaterialCardView
        private val tvVersion: TextView = itemView.findViewById(R.id.tvVersion)
        private val tvPrerelease: TextView = itemView.findViewById(R.id.tvPrerelease)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvSummary: TextView = itemView.findViewById(R.id.tvSummary)
        private val btnOpen: MaterialButton = itemView.findViewById(R.id.btnOpen)
        private val btnDownload: MaterialButton = itemView.findViewById(R.id.btnDownload)
        private var lastClickAt: Long = 0L

        /** 在防抖窗口内执行动作，窗口内的重复调用被忽略。 */
        private fun runThrottled(action: () -> Unit) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastClickAt < CLICK_THROTTLE_MS) return
            lastClickAt = now
            action()
        }

        /** 绑定单条发布记录：填充文本、预发布标记与三种点击行为。 */
        fun bind(entry: ReleaseEntry) {
            tvVersion.text = entry.versionTag
            tvTitle.text = entry.title
            tvDate.text = entry.date
            tvSummary.text = entry.body.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }
                ?: itemView.context.getString(R.string.m3t_updates_no_changelog)

            tvPrerelease.visibility = if (entry.isPrerelease) View.VISIBLE else View.GONE

            card.setOnClickListener { runThrottled { onDetails(entry) } }
            btnOpen.setOnClickListener { runThrottled { onOpenRelease(entry) } }

            btnDownload.text =
                if (entry.apkUrl.isNullOrBlank()) {
                    itemView.context.getString(R.string.m3t_updates_view)
                } else {
                    itemView.context.getString(R.string.m3t_updates_download)
                }
            btnDownload.setOnClickListener { runThrottled { onDownload(entry) } }
        }
    }
}
