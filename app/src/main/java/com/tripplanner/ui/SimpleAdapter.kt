package com.tripplanner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R

/**
 * 묶음 카드 안의 두 줄 목록 (시안 InfoRows · FileRow): 종류 타일 + 제목 + 부제목 + ⋮.
 * 줄을 누르면 onClick, ⋮ 를 누르면 onMenu(수정 / 삭제).
 */
class SimpleAdapter<T>(
    private val title: (T) -> String,
    private val subtitle: (T) -> String,
    private val onClick: (T) -> Unit,
    private val onMenu: (T, View) -> Unit,
    private val icon: Int = 0,         // 왼쪽 아이콘 (0 이면 숨김)
    private val iconOf: ((T) -> Int)? = null   // 항목마다 다른 아이콘 (교통 종류 등)
) : RecyclerView.Adapter<SimpleAdapter.Holder>() {

    private var items: List<T> = emptyList()

    fun submit(list: List<T>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val divider: View = view.findViewById(R.id.divider)
        val row: View = view.findViewById(R.id.row)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvSub: TextView = view.findViewById(R.id.tvSub)
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        val btnMore: ImageButton = view.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_simple, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.divider.visibility = if (position == 0) View.GONE else View.VISIBLE
        holder.tvTitle.text = title(item)
        holder.tvSub.text = subtitle(item)
        val res = iconOf?.invoke(item) ?: icon
        holder.ivIcon.visibility = if (res != 0) View.VISIBLE else View.GONE
        if (res != 0) holder.ivIcon.setImageResource(res)
        holder.row.setOnClickListener { onClick(item) }
        holder.btnMore.contentDescription = holder.itemView.context.getString(R.string.item_menu_a11y, holder.tvTitle.text)
        holder.btnMore.setOnClickListener { onMenu(item, it) }
    }

    override fun getItemCount() = items.size
}
