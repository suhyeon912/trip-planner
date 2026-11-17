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
import com.tripplanner.route.LegMode
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
    /** 끝이 숙소가 아닌 장소(공항 · 역 등)면 그 이름 — "16:48 나리타 공항 도착" */
    private var endSpot: String? = null

    fun submit(list: List<RouteOption>, selected: String?, hasHome: Boolean, endSpot: String? = null) {
        options = list
        selectedKey = selected
        this.hasHome = hasHome
        this.endSpot = endSpot
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
        // 순서가 같은 기준이 셋 이상 합쳐지면 두 개만 쓰고 '외 n' (화면 읽기에는 전부)
        val shortTitle = if (o.kinds.size > 2) ctx.getString(R.string.option_kinds_more,
            o.kindTitles[0], o.kindTitles[1], o.kinds.size - 2) else o.title
        h.name.text = ctx.getString(R.string.option_name, shortTitle, o.totalKm)
        val seq = o.visits.joinToString(" → ") { it.stop.name }
        val stats = endSpot?.let { ctx.getString(R.string.option_seq_spot, seq, TimeUtil.format(o.endTime), it) }
            ?: ctx.getString(if (hasHome) R.string.option_seq else R.string.option_seq_no_home, seq, TimeUtil.format(o.endTime))
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

/** 시간표 구간 한 줄: 수단 · 분 · km · 노선 이름(길찾기로 받았을 때) */
data class LegInfo(val mode: LegMode, val minutes: Int, val km: Double, val lines: String? = null)

/** 수단 이름 · 아이콘 */
val LegMode.labelRes: Int get() = when (this) {
    LegMode.WALK -> R.string.leg_walk; LegMode.TRANSIT -> R.string.leg_transit; LegMode.TAXI -> R.string.leg_taxi
}
val LegMode.iconRes: Int get() = when (this) {
    LegMode.WALK -> R.drawable.ic_walk; LegMode.TRANSIT -> R.drawable.ic_subway; LegMode.TAXI -> R.drawable.ic_taxi
}

// =====================================================================
/** 선택한 경로 시간표: 숙소 출발 → (이동) → 장소 → … → 숙소 도착 */
class RouteStopAdapter(
    private val placeOf: (Long) -> Place?,
    private val onLeave: (Visit, View) -> Unit = { _, _ -> },
    /** 구간 (출발 id, 도착 id, 어림 km) → 수단 · 시간 · 거리 · 노선 */
    private val legInfo: (Long, Long, Double) -> LegInfo = { _, _, km ->
        RoutePlanner.defaultMode(km).let { LegInfo(it, RoutePlanner.modeMinutes(it, km), km) } },
    /** 구간 알약을 누르면: 이 구간 · 이 구간부터 끝까지 · 누른 알약 */
    private val onLeg: (leg: Pair<Long, Long>, rest: List<Pair<Long, Long>>, km: Double, View) -> Unit = { _, _, _, _ -> },
    /** 시작(start) · 끝 줄을 누르면 바꾸기 */
    private val onEndpoint: (start: Boolean, View) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class Home(val name: String, val time: Int, val start: Boolean, val lodging: Boolean = true) : Row()
        /** 숙소가 아닌 시작 · 끝 (장소에서 시작 / 정하지 않음) — 시각 없이 이름 · 설명 */
        data class Pick(val start: Boolean, val title: String, val sub: String, val hollow: Boolean) : Row()
        data class Stop(val visit: Visit) : Row()
        data class Leg(val from: Long, val to: Long, val km: Double) : Row()
    }

    private var rows: List<Row> = emptyList()

    /**
     * @param ends 하루의 시작 · 끝 (숙소면 시각이 있는 줄)
     * @param startPick · endPick 숙소가 아닐 때 보일 (제목, 설명) — 장소에서 시작 · 정하지 않음
     */
    fun submit(option: RouteOption?, ends: com.tripplanner.route.Ends, startMinutes: Int = RoutePlanner.DEFAULT_START,
               startPick: Pair<String, String>? = null, endPick: Pair<String, String>? = null, hollowStart: Boolean = false, hollowEnd: Boolean = false) {
        val start = ends.start; val end = ends.end
        rows = if (option == null) emptyList() else buildList {
            var prev: Pair<Double, Double>? = start?.let { it.lat to it.lng }
            var prevId = RoutePlanner.ANCHOR_ID
            fun leg(toId: Long, to: Pair<Double, Double>) {
                val from = prev ?: return
                add(Row.Leg(prevId, toId, RoutePlanner.legKm(from.first, from.second, to.first, to.second)))
            }
            if (start != null) add(Row.Home(start.name, startMinutes, true, start.lodging))
            else startPick?.let { add(Row.Pick(true, it.first, it.second, hollowStart)) }
            option.visits.forEach { v ->
                leg(v.stop.id, v.stop.lat to v.stop.lng)
                add(Row.Stop(v))
                prev = v.stop.lat to v.stop.lng; prevId = v.stop.id
            }
            if (end != null) { leg(RoutePlanner.ANCHOR_ID, end.lat to end.lng); add(Row.Home(end.name, option.endTime, false, end.lodging)) }
            else endPick?.let { add(Row.Pick(false, it.first, it.second, hollowEnd)) }
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
        is Row.Home -> if (row.start) "home-start" else "home-end"
        is Row.Pick -> if (row.start) "pick-start" else "pick-end"
        is Row.Stop -> "stop-${row.visit.stop.id}"
        is Row.Leg -> "leg-${row.from}-${row.to}"
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val ctx = holder.itemView.context
        val key = rowKey(position, rows[position])
        val same = holder.itemView.getTag(R.id.tag_bound_key) == key
        holder.itemView.setTag(R.id.tag_bound_key, key)
        fun TextView.time(t: CharSequence) = if (same) setTextFading(t) else setTextNow(t)
        when (val row = rows[position]) {
            is Row.Leg -> (holder as LegHolder).text.apply {
                // "🚶 도보 · 18분 · 1.4km ⌄" — 누르면 수단 고르기
                val info = legInfo(row.from, row.to, row.km)
                val mode = info.mode
                // 노선 이름을 알면 수단 이름 대신 (예: 도부 스카이트리선 · 10분 · 2.6km)
                val label = info.lines ?: ctx.getString(mode.labelRes)
                val line = ctx.getString(R.string.leg_mode_line, label, ctx.minutesLabel(info.minutes), info.km)
                if (same) setTextFading(line) else setTextNow(line)
                val s = (16 * ctx.resources.displayMetrics.density).toInt()
                val d = ContextCompat.getDrawable(ctx, mode.iconRes)?.mutate()?.apply { setBounds(0, 0, s, s); setTint(ctx.color(R.color.on_surface)) }
                val more = ContextCompat.getDrawable(ctx, R.drawable.ic_expand_more)?.mutate()?.apply { setBounds(0, 0, s, s); setTint(ctx.color(R.color.text_sub)) }
                setCompoundDrawablesRelative(d, null, more, null)
                contentDescription = ctx.getString(R.string.leg_mode_a11y, line)
                val legs = rows.filterIsInstance<Row.Leg>()
                val i = legs.indexOf(row)
                setOnClickListener { onLeg(row.from to row.to, legs.drop(i).map { it.from to it.to }, row.km, it) }
            }
            is Row.Home -> (holder as StopHolder).apply {
                arrive.time(TimeUtil.format(row.time))
                leave.visibility = View.GONE
                leave.setOnClickListener(null)
                icon.setImageResource(if (row.lodging) R.drawable.ic_hotel else R.drawable.ic_place)
                icon.setPadding(6.dp(ctx), 6.dp(ctx), 6.dp(ctx), 6.dp(ctx))
                icon.background = ContextCompat.getDrawable(ctx, R.drawable.bg_lodging_pin)
                icon.imageTintList = ColorStateList.valueOf(ctx.color(R.color.card_bg))
                name.text = row.name
                sub.visibility = View.GONE
                warn.visibility = View.GONE
                itemView.background = null
                itemView.contentDescription = "${TimeUtil.format(row.time)} ${row.name}, " + ctx.getString(R.string.endpoint_change_a11y)
                itemView.setOnClickListener { onEndpoint(row.start, it) }
            }
            is Row.Pick -> (holder as StopHolder).apply {
                arrive.setTextNow("")
                leave.visibility = View.GONE
                leave.setOnClickListener(null)
                icon.setImageResource(if (row.hollow) 0 else R.drawable.ic_place)
                icon.setPadding(6.dp(ctx), 6.dp(ctx), 6.dp(ctx), 6.dp(ctx))
                icon.background = if (row.hollow) android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(ctx.color(R.color.card_bg)); setStroke(2.dp(ctx), ctx.color(R.color.outline))
                } else ContextCompat.getDrawable(ctx, R.drawable.bg_lodging_pin)
                icon.imageTintList = ColorStateList.valueOf(ctx.color(R.color.card_bg))
                name.text = row.title
                sub.visibility = View.VISIBLE
                sub.text = row.sub
                sub.setTextColor(ctx.color(R.color.text_sub))
                warn.visibility = View.GONE
                arrive.setTextColor(ctx.color(R.color.on_surface))
                itemView.background = null
                itemView.contentDescription = "${row.title}, ${row.sub}, " + ctx.getString(R.string.endpoint_change_a11y)
                itemView.setOnClickListener { onEndpoint(row.start, it) }
            }
            is Row.Stop -> (holder as StopHolder).apply {
                val v = row.visit
                arrive.time(TimeUtil.format(v.arrive))
                leave.visibility = View.VISIBLE
                leave.time(TimeUtil.format(v.leave))
                leave.contentDescription = ctx.getString(R.string.leave_a11y, TimeUtil.format(v.leave))
                leave.setOnClickListener { onLeave(v, it) }
                val place = placeOf(v.stop.id)
                if (place != null) Thumbs.bind(icon, place) else Thumbs.bind(icon, null, com.tripplanner.data.PlaceCategory.ETC)
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
                itemView.setOnClickListener(null); itemView.isClickable = false
            }
        }
    }

    override fun getItemCount() = rows.size

    private fun Int.dp(ctx: Context) = (this * ctx.resources.displayMetrics.density).toInt()
}
