package com.tripplanner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R

/** 일차 칩 하나. key: 0 = 전체, -1 = 일차 미정, 1 이상 = n일차 */
data class DayChip(
    val key: Int,
    val title: String,      // "1일차"
    val sub: String,        // "2/12 금 · 3곳"
    val a11y: String,       // 낭독용 "1일차, 2월 12일 금요일, 장소 3곳"
    val selected: Boolean
)

/** 가로로 늘어선 일차 칩 목록 (하나만 선택) */
class DayChipAdapter(private val onSelect: (Int) -> Unit) : RecyclerView.Adapter<DayChipAdapter.Holder>() {

    /** 지도 보기: 한 줄 칩 + 일차 색 점 (시안 DetailMap). 고른 '전체'는 진한 바탕 */
    var compact = false
        set(v) { if (field != v) { field = v; notifyDataSetChanged() } }

    private var chips: List<DayChip> = emptyList()

    fun submit(list: List<DayChip>) {
        chips = list
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvChipTitle)
        val sub: TextView = view.findViewById(R.id.tvChipSub)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_day_chip, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val chip = chips[position]
        val ctx = holder.itemView.context
        holder.title.text = chip.title
        holder.sub.text = chip.sub
        holder.sub.visibility = if (chip.sub.isEmpty() || compact) View.GONE else View.VISIBLE
        holder.itemView.setBackgroundResource(if (chip.selected) R.drawable.bg_day_chip_selected else R.drawable.bg_day_chip)
        // 지도 보기: '전체'를 고르면 진한 바탕, 일차 칩 앞에 일차 색 점
        val darkAll = compact && chip.selected && chip.key == 0
        holder.itemView.backgroundTintList = if (darkAll) android.content.res.ColorStateList.valueOf(ctx.color(R.color.on_surface)) else null
        val dot = if (compact && chip.key > 0 && !chip.selected) android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(PinIcons.dayColor(chip.key))
            val px = (8 * ctx.resources.displayMetrics.density).toInt(); setSize(px, px)
        } else null
        holder.title.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
        holder.title.compoundDrawablePadding = (6 * ctx.resources.displayMetrics.density).toInt()
        val titleColor = if (darkAll) R.color.card_bg else if (chip.selected) R.color.on_primary else R.color.on_surface
        val subColor = if (chip.selected) R.color.day_chip_sub_selected else R.color.text_sub
        holder.title.setTextColor(ContextCompat.getColor(ctx, titleColor))
        holder.sub.setTextColor(ContextCompat.getColor(ctx, subColor))
        holder.itemView.contentDescription = chip.a11y
        holder.itemView.isSelected = chip.selected
        ViewCompat.setStateDescription(holder.itemView, if (chip.selected) ctx.getString(R.string.state_selected) else null)
        holder.itemView.setOnClickListener { onSelect(chip.key) }
    }

    override fun getItemCount() = chips.size
}
