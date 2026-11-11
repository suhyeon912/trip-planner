package com.tripplanner.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R
import com.tripplanner.route.Anchor
import com.tripplanner.route.RouteOption
import com.tripplanner.route.RoutePlanner
import com.tripplanner.route.TimeUtil
import com.tripplanner.route.Visit

fun waitMinutes(o: RouteOption) = o.visits.sumOf { it.waitMinutes }

/** 거리 · 이동 · (대기) 한 줄 */
fun statsLine(ctx: Context, o: RouteOption, separator: String = ctx.getString(R.string.list_separator)): String {
    val wait = waitMinutes(o)
    return listOfNotNull(
        "%.1fkm".format(o.totalKm),
        ctx.getString(R.string.stat_move, ctx.minutesLabel(o.moveMinutes)),
        if (wait >= 10) ctx.getString(R.string.stat_wait, ctx.minutesLabel(wait)) else null
    ).joinToString(separator)
}

// =====================================================================
/** A/B/C 요약 카드 (하나만 선택). 카드 수만큼 한 줄에 나란히 */
class RouteOptionAdapter(private val onSelect: (String) -> Unit) : RecyclerView.Adapter<RouteOptionAdapter.Holder>() {

    private var options: List<RouteOption> = emptyList()
    private var selectedKey: String? = null
    /** 0 이면 칸을 나눠 꽉 채움, 0보다 크면 이 폭(px)으로 고정하고 가로 스크롤 */
    private var fixedWidthPx = 0

    fun submit(list: List<RouteOption>, selected: String?, fixedWidth: Int = 0) {
        options = list
        selectedKey = selected
        fixedWidthPx = fixedWidth
        notifyDataSetChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val key: TextView = v.findViewById(R.id.tvKey)
        val name: TextView = v.findViewById(R.id.tvOptionName)
        val stats: TextView = v.findViewById(R.id.tvStats)
        val badge: TextView = v.findViewById(R.id.tvWarnBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_route, parent, false))

    override fun onBindViewHolder(h: Holder, position: Int) {
        val o = options[position]
        val ctx = h.itemView.context
        val selected = o.key == selectedKey
        h.itemView.layoutParams = h.itemView.layoutParams.apply {
            width = if (fixedWidthPx > 0) fixedWidthPx else ViewGroup.LayoutParams.MATCH_PARENT
        }
        h.key.text = o.key
        h.name.text = o.title
        h.stats.text = statsLine(ctx, o, "\n")
        val warnCount = o.warnings.size
        when {
            warnCount > 0 -> {
                h.badge.text = ctx.getString(R.string.warn_n, warnCount)
                h.badge.setBackgroundResource(R.drawable.bg_warn_badge)
                h.badge.setTextColor(ContextCompat.getColor(ctx, R.color.warn_text))
            }
            else -> {
                h.badge.text = ctx.getString(R.string.warn_none)
                h.badge.setBackgroundResource(R.drawable.bg_ok_badge)
                h.badge.setTextColor(ContextCompat.getColor(ctx, R.color.success))
            }
        }
        if (o.added.isNotEmpty()) h.badge.text = h.badge.text.toString() + ctx.getString(R.string.added_n_badge, o.added.size)
        h.itemView.setBackgroundResource(if (selected) R.drawable.bg_option_selected else R.drawable.bg_option)
        h.itemView.isSelected = selected
        h.itemView.contentDescription = ctx.getString(R.string.option_a11y, o.key, o.title, statsLine(ctx, o, ", "),
                if (warnCount > 0) ctx.getString(R.string.warn_n_a11y, warnCount) else ctx.getString(R.string.warn_none),
                if (o.added.isNotEmpty()) ctx.getString(R.string.added_n_a11y, o.added.size) else "")
        ViewCompat.setStateDescription(h.itemView, if (selected) ctx.getString(R.string.state_selected) else null)
        h.itemView.setOnClickListener { onSelect(o.key) }
    }

    override fun getItemCount() = options.size
}

// =====================================================================
/** 선택한 안의 시간표: (숙소 출발) → 장소들 → (숙소 도착) */
class RouteStopAdapter : RecyclerView.Adapter<RouteStopAdapter.Holder>() {

    private sealed class Row {
        data class Home(val name: String, val time: Int, val start: Boolean) : Row()
        data class Stop(val index: Int, val visit: Visit) : Row()
    }

    private var rows: List<Row> = emptyList()

    fun submit(option: RouteOption?, anchor: Anchor?, startMinutes: Int = RoutePlanner.DEFAULT_START) {
        rows = if (option == null) emptyList() else buildList {
            if (anchor != null) add(Row.Home(anchor.name, startMinutes, true))
            option.visits.forEachIndexed { i, v -> add(Row.Stop(i + 1, v)) }
            if (anchor != null) add(Row.Home(anchor.name, option.endTime, false))
        }
        notifyDataSetChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val lineAbove: View = v.findViewById(R.id.lineAbove)
        val lineBelow: View = v.findViewById(R.id.lineBelow)
        val dot: TextView = v.findViewById(R.id.tvDot)
        val dotIcon: ImageView = v.findViewById(R.id.ivDotIcon)
        val name: TextView = v.findViewById(R.id.tvStopName)
        val meta: TextView = v.findViewById(R.id.tvStopMeta)
        val candidate: TextView = v.findViewById(R.id.tvCandidate)
        val wait: TextView = v.findViewById(R.id.tvWait)
        val warn: TextView = v.findViewById(R.id.tvWarn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_route_stop, parent, false))

    override fun onBindViewHolder(h: Holder, position: Int) {
        h.lineAbove.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        h.lineBelow.visibility = if (position == rows.lastIndex) View.INVISIBLE else View.VISIBLE
        val ctx = h.itemView.context
        when (val row = rows[position]) {
            is Row.Home -> {
                h.dot.text = ""
                h.dot.setBackgroundResource(R.drawable.bg_chip)
                h.dot.backgroundTintList = ContextCompat.getColorStateList(h.itemView.context, R.color.surface_container)
                h.dotIcon.visibility = View.VISIBLE
                h.name.text = row.name
                h.meta.text = ctx.getString(if (row.start) R.string.stop_depart else R.string.stop_arrive_end, TimeUtil.format(row.time))
                h.candidate.visibility = View.GONE
                h.wait.visibility = View.GONE
                h.warn.visibility = View.GONE
            }
            is Row.Stop -> {
                val v = row.visit
                h.dot.text = row.index.toString()
                h.dot.setBackgroundResource(R.drawable.bg_timeline_dot)
                h.dot.backgroundTintList = null
                h.dotIcon.visibility = View.GONE
                h.name.text = v.stop.name
                h.meta.text = ctx.getString(R.string.stop_meta, TimeUtil.format(v.arrive), v.stop.stayMinutes)
                h.candidate.visibility = if (v.stop.isCandidate) View.VISIBLE else View.GONE
                h.wait.visibility = if (v.waitMinutes >= 10) View.VISIBLE else View.GONE
                h.wait.text = ctx.getString(R.string.stop_wait, ctx.minutesLabel(v.waitMinutes))
                h.wait.setStartIcon(R.drawable.ic_schedule)
                h.warn.visibility = if (v.warning != null) View.VISIBLE else View.GONE
                h.warn.text = v.warning
                h.warn.setStartIcon(R.drawable.ic_warning)
            }
        }
    }

    override fun getItemCount() = rows.size

    /** 글자 앞 작은 아이콘(16dp). 화면 밀도와 상관없이 같은 크기로 보이게 dp 로 맞춤 */
    private fun TextView.setStartIcon(res: Int) {
        val d = ContextCompat.getDrawable(context, res)?.mutate() ?: return
        val size = (16 * resources.displayMetrics.density).toInt()
        d.setBounds(0, 0, size, size)
        setCompoundDrawablesRelative(d, null, null, null)
    }
}
