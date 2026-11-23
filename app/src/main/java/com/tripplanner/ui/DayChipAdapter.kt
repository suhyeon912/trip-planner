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
    val selected: Boolean,
    /** 아이콘 칩 (후보 탭 종류): 글자 대신 아이콘, title 은 낭독 · 말풍선용 */
    val icon: Int? = null,
    /** 고른 상태의 아이콘 (채움) */
    val iconSelected: Int? = null
)

/** 가로로 늘어선 일차 칩 목록 (하나만 선택) */
class DayChipAdapter(private val onSelect: (Int) -> Unit) : RecyclerView.Adapter<DayChipAdapter.Holder>() {

    /** 지도 보기: 한 줄 칩 + 일차 색 점 (시안 DetailMap). 고른 '전체'는 진한 바탕 */
    var compact = false
        set(v) { if (field != v) { field = v; notifyDataSetChanged() } }

    /** 후보 탭 종류 칩: 한 줄 칩, 고른 '전체'도 일반 선택 색 */
    var kindMode = false
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
        val darkAll = compact && !kindMode && chip.selected && chip.key == 0
        holder.itemView.backgroundTintList = if (darkAll) android.content.res.ColorStateList.valueOf(ctx.color(R.color.on_surface)) else null
        val dot = if (compact && !kindMode && chip.key > 0 && !chip.selected) android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(PinIcons.dayColor(chip.key))
            val px = (8 * ctx.resources.displayMetrics.density).toInt(); setSize(px, px)
        } else null
        val iconD = (if (chip.selected) chip.iconSelected ?: chip.icon else chip.icon)?.let { ContextCompat.getDrawable(ctx, it)?.mutate() }?.apply {
            val px = (20 * ctx.resources.displayMetrics.density).toInt(); setBounds(0, 0, px, px)
            setTint(ContextCompat.getColor(ctx, if (chip.selected) R.color.on_primary else R.color.on_surface))
        }
        if (iconD != null) holder.title.text = ""
        holder.title.setCompoundDrawablesRelative(iconD ?: dot?.apply { setBounds(0, 0, intrinsicWidth, intrinsicHeight) }, null, null, null)
        androidx.appcompat.widget.TooltipCompat.setTooltipText(holder.itemView, if (iconD != null) chip.title else null)
        holder.title.compoundDrawablePadding = if (iconD != null) 0 else (6 * ctx.resources.displayMetrics.density).toInt()
        val titleColor = if (darkAll) R.color.card_bg else if (chip.selected) R.color.on_primary else R.color.on_surface
        val subColor = if (chip.selected) R.color.day_chip_sub_selected else R.color.text_sub
        holder.title.setTextColor(ContextCompat.getColor(ctx, titleColor))
        holder.sub.setTextColor(ContextCompat.getColor(ctx, subColor))
        holder.itemView.contentDescription = chip.a11y
        holder.itemView.isSelected = chip.selected
        ViewCompat.setStateDescription(holder.itemView, if (chip.selected) ctx.getString(R.string.state_selected) else null)
        holder.itemView.setOnClickListener { onSelect(chip.key) }
        // 칩: 모서리 16(카드와 같음) · 좌우 16 · 위아래 8 · 일차 칩은 최소 폭 76 (넘치면 옆으로 밀어서 보기) · 칩 사이 8
        val d = ctx.resources.displayMetrics.density
        val wide = compact || chip.key == 0
        val h = (16 * d).toInt()
        val v = if (compact && !kindMode) 6 else 8
        holder.itemView.setPaddingRelative(h, (v * d).toInt(), h, (v * d).toInt())
        holder.itemView.minimumWidth = if (wide) 0 else (76 * d).toInt()
        // 한 줄인 '전체'도 두 줄 일차 칩과 같은 높이
        // 후보 탭 종류 칩도 일정 탭 일차 칩과 같은 높이 54 (지도 보기 한 줄 칩만 48)
        holder.itemView.minimumHeight = if (compact && !kindMode) (48 * d).toInt() else (54 * d).toInt()
        // 글자는 칩 가운데
        (holder.itemView as android.widget.LinearLayout).gravity = android.view.Gravity.CENTER
        (holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd = (8 * d).toInt()
    }

    override fun getItemCount() = chips.size
}
