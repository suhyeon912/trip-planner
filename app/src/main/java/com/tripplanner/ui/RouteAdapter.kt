package com.tripplanner.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R
import com.tripplanner.data.Place
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

/** 추천 = 확인할 것이 가장 적은 경로 (같으면 앞 순서 = 이동 거리 우선) */
fun recommendedKey(options: List<RouteOption>): String? =
    if (options.size < 2) null else options.minByOrNull { it.warnings.size }?.key

// =====================================================================
/** 경로 선택지 카드 (세로 목록, 하나만 선택) */
class RouteOptionAdapter(private val onSelect: (String) -> Unit) : RecyclerView.Adapter<RouteOptionAdapter.Holder>() {

    private var options: List<RouteOption> = emptyList()
    private var selectedKey: String? = null
    private var hasHome = true

    fun submit(list: List<RouteOption>, selected: String?, hasHome: Boolean) {
        options = list
        selectedKey = selected
        this.hasHome = hasHome
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
        val recommended = recommendedKey(options) == o.key
        h.key.text = o.key
        h.key.backgroundTintList = ColorStateList.valueOf(ctx.color(if (selected) R.color.primary else R.color.surface_container))
        h.key.setTextColor(ctx.color(if (selected) R.color.on_primary else R.color.on_surface))
        h.name.text = ctx.getString(R.string.option_name, o.title, o.totalKm)
        val seq = o.visits.joinToString(" → ") { it.stop.name }
        val stats = ctx.getString(if (hasHome) R.string.option_seq else R.string.option_seq_no_home, seq, TimeUtil.format(o.endTime))
        val same = h.itemView.getTag(R.id.tag_bound_key) == o.key
        h.itemView.setTag(R.id.tag_bound_key, o.key)
        if (same) h.stats.setTextFading(stats) else h.stats.setTextNow(stats)
        val warnCount = o.warnings.size
        val small = (14 * ctx.resources.displayMetrics.density).toInt()
        fun icon(res: Int, tint: Int) = ContextCompat.getDrawable(ctx, res)?.mutate()?.apply {
            setBounds(0, 0, small, small); setTint(ctx.color(tint))
        }
        when {
            warnCount > 0 -> {
                h.badge.visibility = View.VISIBLE
                h.badge.text = ctx.getString(R.string.warn_n, warnCount)
                h.badge.backgroundTintList = ColorStateList.valueOf(ctx.color(R.color.warn_bg))
                h.badge.setTextColor(ctx.color(R.color.warn_text))
                h.badge.setCompoundDrawablesRelative(icon(R.drawable.ic_warning, R.color.warn_text), null, null, null)
            }
            recommended -> {
                h.badge.visibility = View.VISIBLE
                h.badge.text = ctx.getString(R.string.recommended)
                h.badge.backgroundTintList = ColorStateList.valueOf(ctx.color(R.color.primary))
                h.badge.setTextColor(ctx.color(R.color.on_primary))
                h.badge.setCompoundDrawablesRelative(icon(R.drawable.ic_check, R.color.on_primary), null, null, null)
            }
            else -> h.badge.visibility = View.GONE
        }
        h.badge.setBackgroundResource(R.drawable.bg_chip)
        h.itemView.setBackgroundResource(if (selected) R.drawable.bg_option_selected else R.drawable.bg_option)
        h.itemView.isSelected = selected
        h.itemView.contentDescription = listOfNotNull(ctx.getString(R.string.option_a11y, o.key, o.title, statsLine(ctx, o, ", "),
                if (warnCount > 0) ctx.getString(R.string.warn_n_a11y, warnCount) else "",
                if (o.added.isNotEmpty()) ctx.getString(R.string.added_n_a11y, o.added.size) else ""),
            if (recommended) ctx.getString(R.string.recommended) else null, stats).joinToString(", ")
        ViewCompat.setStateDescription(h.itemView, if (selected) ctx.getString(R.string.state_selected) else null)
        h.itemView.setOnClickListener { onSelect(o.key) }
    }

    override fun getItemCount() = options.size
}

// =====================================================================
/** 선택한 경로 시간표: 숙소 출발 → (이동) → 장소 → … → 숙소 도착 */
class RouteStopAdapter(
    private val placeOf: (Long) -> Place?,
    private val onLeave: (Visit, View) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class Home(val name: String, val time: Int) : Row()
        data class Stop(val visit: Visit) : Row()
        data class Leg(val minutes: Int, val km: Double) : Row()
    }

    private var rows: List<Row> = emptyList()

    fun submit(option: RouteOption?, anchor: Anchor?, startMinutes: Int = RoutePlanner.DEFAULT_START) {
        rows = if (option == null) emptyList() else buildList {
            var prev: Pair<Double, Double>? = anchor?.let { it.lat to it.lng }
            fun leg(to: Pair<Double, Double>) {
                val from = prev ?: return
                val km = RoutePlanner.legKm(from.first, from.second, to.first, to.second)
                add(Row.Leg(RoutePlanner.legMinutes(km), km))
            }
            if (anchor != null) add(Row.Home(anchor.name, startMinutes))
            option.visits.forEach { v ->
                leg(v.stop.lat to v.stop.lng)
                add(Row.Stop(v))
                prev = v.stop.lat to v.stop.lng
            }
            if (anchor != null) { leg(anchor.lat to anchor.lng); add(Row.Home(anchor.name, option.endTime)) }
        }
        notifyDataSetChanged()
    }

    private class StopHolder(v: View) : RecyclerView.ViewHolder(v) {
        val arrive: TextView = v.findViewById(R.id.tvArrive)
        val leave: TextView = v.findViewById(R.id.tvLeave)
        val icon: ImageView = v.findViewById(R.id.ivStopIcon)
        val name: TextView = v.findViewById(R.id.tvStopName)
        val sub: TextView = v.findViewById(R.id.tvStopSub)
        val warn: ImageView = v.findViewById(R.id.ivWarn)
    }
    private class LegHolder(v: View) : RecyclerView.ViewHolder(v) { val text: TextView = v.findViewById(R.id.tvLeg) }

    override fun getItemViewType(position: Int) = if (rows[position] is Row.Leg) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 1) LegHolder(inf.inflate(R.layout.item_route_leg, parent, false))
        else StopHolder(inf.inflate(R.layout.item_route_stop, parent, false))
    }

    /** 같은 줄(같은 장소·숙소)이 다시 그려질 때만 시각을 흐려졌다 바꿈 */
    private fun rowKey(position: Int, row: Row): String = when (row) {
        is Row.Home -> if (position == 0) "home-start" else "home-end"
        is Row.Stop -> "stop-${row.visit.stop.id}"
        is Row.Leg -> "leg-$position"
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val ctx = holder.itemView.context
        val key = rowKey(position, rows[position])
        val same = holder.itemView.getTag(R.id.tag_bound_key) == key
        holder.itemView.setTag(R.id.tag_bound_key, key)
        fun TextView.time(t: CharSequence) = if (same) setTextFading(t) else setTextNow(t)
        when (val row = rows[position]) {
            is Row.Leg -> (holder as LegHolder).text.apply {
                text = ctx.getString(R.string.leg_line, ctx.minutesLabel(row.minutes), row.km)
                val s = (14 * ctx.resources.displayMetrics.density).toInt()
                val d = ContextCompat.getDrawable(ctx, R.drawable.ic_route)?.mutate()?.apply { setBounds(0, 0, s, s); setTint(ctx.color(R.color.text_sub)) }
                setCompoundDrawablesRelative(d, null, null, null)
            }
            is Row.Home -> (holder as StopHolder).apply {
                arrive.time(TimeUtil.format(row.time))
                leave.visibility = View.GONE
                leave.setOnClickListener(null)
                icon.setImageResource(R.drawable.ic_hotel)
                icon.setPadding(6.dp(ctx), 6.dp(ctx), 6.dp(ctx), 6.dp(ctx))
                icon.background = ContextCompat.getDrawable(ctx, R.drawable.bg_lodging_pin)
                icon.imageTintList = ColorStateList.valueOf(ctx.color(R.color.card_bg))
                name.text = row.name
                sub.visibility = View.GONE
                warn.visibility = View.GONE
                itemView.background = null
                itemView.contentDescription = "${TimeUtil.format(row.time)} ${row.name}"
            }
            is Row.Stop -> (holder as StopHolder).apply {
                val v = row.visit
                arrive.time(TimeUtil.format(v.arrive))
                leave.visibility = View.VISIBLE
                leave.time(TimeUtil.format(v.leave))
                leave.contentDescription = ctx.getString(R.string.leave_a11y, TimeUtil.format(v.leave))
                leave.setOnClickListener { onLeave(v, it) }
                val place = placeOf(v.stop.id)
                Thumbs.bind(icon, place?.photoUri, place?.category ?: com.tripplanner.data.PlaceCategory.ETC)
                icon.setPadding(6.dp(ctx), 6.dp(ctx), 6.dp(ctx), 6.dp(ctx))
                name.text = v.stop.name
                // 이유 한 줄: 경고 > 대기 > 후보에서 추가
                val subText = v.warning
                    ?: if (v.waitMinutes >= 10) ctx.getString(R.string.stop_wait, ctx.minutesLabel(v.waitMinutes)) else null
                    ?: if (v.stop.isCandidate) ctx.getString(R.string.added_from_candidates) else null
                sub.visibility = if (subText == null) View.GONE else View.VISIBLE
                sub.text = subText
                sub.setTextColor(ctx.color(if (v.warning != null) R.color.warn_text else R.color.text_sub))
                warn.visibility = if (v.warning != null) View.VISIBLE else View.GONE
                arrive.setTextColor(ctx.color(if (v.warning != null) R.color.warn_text else R.color.on_surface))
                itemView.background = if (v.warning != null) ContextCompat.getDrawable(ctx, R.drawable.bg_stop_warn) else null
                itemView.contentDescription = listOfNotNull(
                    ctx.getString(R.string.stop_meta, TimeUtil.format(v.arrive), v.stop.stayMinutes), v.stop.name, subText).joinToString(", ")
            }
        }
    }

    override fun getItemCount() = rows.size

    private fun Int.dp(ctx: Context) = (this * ctx.resources.displayMetrics.density).toInt()
}
