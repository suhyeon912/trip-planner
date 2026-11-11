package com.tripplanner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R

/** 제목 + 부제목 두 줄짜리 목록에 두루 쓰는 어댑터 */
class SimpleAdapter<T>(
    private val title: (T) -> String,
    private val subtitle: (T) -> String,
    private val onClick: (T) -> Unit,
    private val icon: Int = 0,         // 왼쪽 아이콘 (0 이면 숨김)
    private val iconOf: ((T) -> Int)? = null   // 항목마다 다른 아이콘 (교통 종류 등)
) : RecyclerView.Adapter<SimpleAdapter.Holder>() {

    private var items: List<T> = emptyList()

    fun submit(list: List<T>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvSub: TextView = view.findViewById(R.id.tvSub)
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_simple, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = title(item)
        holder.tvSub.text = subtitle(item)
        val res = iconOf?.invoke(item) ?: icon
        holder.ivIcon.visibility = if (res != 0) View.VISIBLE else View.GONE
        if (res != 0) holder.ivIcon.setImageResource(res)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
