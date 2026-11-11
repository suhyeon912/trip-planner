package com.tripplanner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.content.ContextCompat
import com.tripplanner.R

/** 메뉴 · select 한 줄 */
sealed class SelectRow {
    /** 작은 제목 (예: 정렬 기준) — 누를 수 없음 */
    data class Header(val text: String) : SelectRow()
    /** 고를 수 있는 줄. selected = 지금 값(바탕색), danger = 빨간 글자(삭제) */
    data class Option(
        val text: String,
        val selected: Boolean = false,
        val danger: Boolean = false,
        val onClick: () -> Unit
    ) : SelectRow()
    object Divider : SelectRow()
}

/**
 * 시안 Menu · Select: 누른 자리 바로 아래 · 모서리 14 · 그림자 · 항목 48 · 아이콘 없이 글자만.
 * 바텀 시트 대신 씁니다. 고르면 바로 닫힘.
 */
fun showSelect(anchor: View, rows: List<SelectRow>, widthDp: Int = 200) {
    val ctx = anchor.context
    val popup = ListPopupWindow(ctx)
    val inflater = LayoutInflater.from(ctx)
    popup.setAdapter(object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 3
        override fun getItemViewType(position: Int) = when (rows[position]) {
            is SelectRow.Header -> 0; is SelectRow.Option -> 1; SelectRow.Divider -> 2
        }
        override fun areAllItemsEnabled() = false
        override fun isEnabled(position: Int) = rows[position] is SelectRow.Option
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = when (val row = rows[position]) {
            is SelectRow.Header -> (convertView ?: inflater.inflate(R.layout.item_select_header, parent, false)).apply {
                (this as TextView).text = row.text
            }
            is SelectRow.Option -> (convertView ?: inflater.inflate(R.layout.item_select_option, parent, false)).apply {
                (this as TextView).text = row.text
                isSelected = row.selected
                setTextColor(ContextCompat.getColor(ctx, when {
                    row.danger -> R.color.badge_red
                    row.selected -> R.color.on_secondary_container
                    else -> R.color.on_surface
                }))
            }
            SelectRow.Divider -> convertView ?: inflater.inflate(R.layout.item_select_divider, parent, false)
        }
    })
    popup.anchorView = anchor
    popup.width = (widthDp * ctx.resources.displayMetrics.density).toInt()
    popup.isModal = true
    popup.setBackgroundDrawable(ContextCompat.getDrawable(ctx, R.drawable.bg_menu))
    popup.setOnItemClickListener { _, _, position, _ ->
        (rows[position] as? SelectRow.Option)?.onClick?.invoke()
        popup.dismiss()
    }
    popup.show()
}
