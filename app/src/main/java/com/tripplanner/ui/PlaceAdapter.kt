package com.tripplanner.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R
import com.tripplanner.data.Place
import com.tripplanner.data.PlaceStatus

/** 상세 화면 목록의 한 줄 종류 */
sealed class DetailItem {
    /** 일차 제목 "● 1일차 11월 20일 (금)" (+ '전체'에서는 오른쪽 '경로 제안') */
    data class Header(val title: String, val sub: String, val day: Int? = null, val routeLink: Boolean = false) : DetailItem()
    /** 타임라인 맨 위·아래 숙소 (출발 / 도착) */
    data class LodgingRow(val name: String, val start: Boolean) : DetailItem()
    /** 장소 카드. order = 방문 순서(경로 확정 전이면 null), timeline = 왼쪽 타임라인 열 표시 */
    data class PlaceRow(val place: Place, val order: String?, val isFirst: Boolean, val isLast: Boolean, val timeline: Boolean) : DetailItem()
    /** '전체' 맨 위 교통 · 숙소 요약 */
    data class Summary(val lines: List<Pair<Int, String>>) : DetailItem()   // (아이콘, 글자)
    /** 맨 아래 여행 메모 */
    data class Memo(val note: String) : DetailItem()
    /** 빈 상태 (목록 안에 표시) */
    data class Empty(val state: EmptyState) : DetailItem()
}

/**
 * 여행 상세 목록 어댑터 (시안 PlaceCard).
 * 장소 카드: 한 번 누르면 장소 정보, 길게 누르면 장소 수정, 오른쪽 ⠿ 손잡이를 끌면 순서 변경.
 * 후보 카드: '확정하기' → 일차 select.
 */
class PlaceAdapter(
    private val listener: Listener,
    private val onRouteCta: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Listener {
        fun onOpen(place: Place)
        fun onEdit(place: Place)
        fun onConfirmPick(place: Place, anchor: View)
        fun onOpenTravel()
        fun onOpenNote()
        fun onEmptyAction(action: EmptyState.Action)
    }

    companion object {
        /** 끌기 중 순서 점만 다시 그릴 때 */
        const val PAYLOAD_TIMELINE = "timeline"
        private const val TYPE_HEADER = 0
        private const val TYPE_LODGING = 1
        private const val TYPE_PLACE = 2
        private const val TYPE_SUMMARY = 3
        private const val TYPE_MEMO = 4
        private const val TYPE_EMPTY = 5
        /** 카드에 보이는 뱃지는 최대 2개, 넘치면 +n */
        private const val MAX_BADGES = 2
    }

    private var items: List<DetailItem> = emptyList()
    /** 일차 하나를 보고 있을 때만 순서 끌기 가능 */
    var dragEnabled = false
    /** 손잡이를 누르면 Activity 의 ItemTouchHelper 가 끌기를 시작 */
    var onStartDrag: ((RecyclerView.ViewHolder) -> Unit)? = null

    /** 지금 목록 (끌어서 옮길 때 사용) */
    val currentItems: List<DetailItem> get() = items

    /** 끌기 중 화면에서만 자리 바꾸기 (저장은 놓았을 때) */
    fun moveItem(from: Int, to: Int): Boolean {
        if (items.getOrNull(from) !is DetailItem.PlaceRow || items.getOrNull(to) !is DetailItem.PlaceRow) return false
        val list = items.toMutableList()
        list.add(to, list.removeAt(from))
        // 왼쪽 순서 점은 자리에 고정: 자리마다 원래 번호·위아래 선을 그대로 두고 장소만 바꿈
        val lo = minOf(from, to); val hi = maxOf(from, to)
        for (i in lo..hi) {
            val slot = items[i] as? DetailItem.PlaceRow ?: continue
            val row = list[i] as? DetailItem.PlaceRow ?: continue
            list[i] = row.copy(order = slot.order, isFirst = slot.isFirst, isLast = slot.isLast)
        }
        items = list
        notifyItemMoved(from, to)
        notifyItemRangeChanged(lo, hi - lo + 1, PAYLOAD_TIMELINE)
        return true
    }

    fun submit(newList: List<DetailItem>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is DetailItem.Header -> TYPE_HEADER
        is DetailItem.LodgingRow -> TYPE_LODGING
        is DetailItem.PlaceRow -> TYPE_PLACE
        is DetailItem.Summary -> TYPE_SUMMARY
        is DetailItem.Memo -> TYPE_MEMO
        is DetailItem.Empty -> TYPE_EMPTY
    }

    override fun getItemCount() = items.size

    // ---------------- ViewHolders ----------------
    class HeaderHolder(v: View) : RecyclerView.ViewHolder(v) {
        val dot: View = v.findViewById(R.id.vHeaderDot)
        val title: TextView = v.findViewById(R.id.tvHeaderTitle)
        val sub: TextView = v.findViewById(R.id.tvHeaderSub)
        val route: Button = v.findViewById(R.id.btnHeaderRoute)
    }
    class LodgingHolder(v: View) : RecyclerView.ViewHolder(v) {
        val lineAbove: View = v.findViewById(R.id.lineAbove)
        val lineBelow: View = v.findViewById(R.id.lineBelow)
        val name: TextView = v.findViewById(R.id.tvLodgingName)
        val sub: TextView = v.findViewById(R.id.tvLodgingSub)
    }
    class PlaceHolder(v: View) : RecyclerView.ViewHolder(v) {
        val timeline: View = v.findViewById(R.id.timeline)
        val lineAbove: View = v.findViewById(R.id.lineAbove)
        val lineBelow: View = v.findViewById(R.id.lineBelow)
        val dot: TextView = v.findViewById(R.id.tvDot)
        val card: View = v.findViewById(R.id.card)
        val name: TextView = v.findViewById(R.id.tvPlaceName)
        val summary: TextView = v.findViewById(R.id.tvSummary)
        val badges: LinearLayout = v.findViewById(R.id.badges)
        val confirm: Button = v.findViewById(R.id.btnConfirm)
        val thumb: ImageView = v.findViewById(R.id.ivThumb)
        val drag: ImageView = v.findViewById(R.id.ivDrag)
    }
    class SummaryHolder(v: View) : RecyclerView.ViewHolder(v) {
        val rows: LinearLayout = v.findViewById(R.id.summaryRows)
    }
    class MemoHolder(v: View) : RecyclerView.ViewHolder(v) {
        val note: TextView = v.findViewById(R.id.tvMemoText)
    }
    class EmptyHolder(v: View) : RecyclerView.ViewHolder(v)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(inf.inflate(R.layout.item_day_header, parent, false))
            TYPE_LODGING -> LodgingHolder(inf.inflate(R.layout.item_timeline_lodging, parent, false))
            TYPE_SUMMARY -> SummaryHolder(inf.inflate(R.layout.item_detail_summary, parent, false).also { Motion.clipRoundedCard(it) })
            TYPE_MEMO -> MemoHolder(inf.inflate(R.layout.item_detail_memo, parent, false))
            TYPE_EMPTY -> EmptyHolder(inf.inflate(R.layout.view_empty_state, parent, false).apply {
                setPadding(paddingLeft, (40 * resources.displayMetrics.density).toInt(), paddingRight,
                    (24 * resources.displayMetrics.density).toInt())
            })
            else -> PlaceHolder(inf.inflate(R.layout.item_place, parent, false))
        }
    }

    /** 끌기 중 순서 점만 다시 그림 (카드는 그대로) */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        val row = items.getOrNull(position)
        if (PAYLOAD_TIMELINE in payloads && holder is PlaceHolder && row is DetailItem.PlaceRow) bindTimeline(holder, row)
        else super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DetailItem.Header -> (holder as HeaderHolder).apply {
                title.text = item.title
                sub.text = item.sub
                // 일차 점은 지도·타임라인과 같은 일차 색
                dot.visibility = if (item.day != null) View.VISIBLE else View.GONE
                item.day?.let { dot.backgroundTintList = ColorStateList.valueOf(PinIcons.dayColor(it)) }
                route.visibility = if (item.routeLink && item.day != null) View.VISIBLE else View.GONE
                if (item.day != null) {
                    route.contentDescription = route.context.getString(R.string.route_link_a11y, item.day)
                    route.setOnClickListener { onRouteCta(item.day) }
                }
            }
            is DetailItem.LodgingRow -> (holder as LodgingHolder).apply {
                name.text = item.name
                sub.setText(if (item.start) R.string.lodging_start else R.string.lodging_end)
                lineAbove.visibility = if (item.start) View.INVISIBLE else View.VISIBLE
                lineBelow.visibility = if (item.start) View.VISIBLE else View.INVISIBLE
                itemView.setOnClickListener { listener.onOpenTravel() }
            }
            is DetailItem.Summary -> (holder as SummaryHolder).apply {
                rows.removeAllViews()
                val inf = LayoutInflater.from(itemView.context)
                item.lines.forEachIndexed { i, (icon, line) ->
                    val row = inf.inflate(R.layout.item_summary_line, rows, false)
                    row.findViewById<TextView>(R.id.tvSummaryLine).text = line
                    row.findViewById<ImageView>(R.id.ivSummaryIcon).setImageResource(icon)
                    row.findViewById<View>(R.id.divider).visibility = if (i == 0) View.GONE else View.VISIBLE
                    row.setOnClickListener { listener.onOpenTravel() }
                    rows.addView(row)
                }
            }
            is DetailItem.Memo -> (holder as MemoHolder).apply {
                note.text = item.note.trim()
                note.visibility = if (item.note.isBlank()) View.GONE else View.VISIBLE
                itemView.setOnClickListener { v -> v.jumpDrawablesToCurrentState(); listener.onOpenNote() }  // 팝업 뒤에 눌림 색이 남지 않게
            }
            is DetailItem.Empty -> bindEmptyState(holder.itemView, item.state) { listener.onEmptyAction(it) }
            is DetailItem.PlaceRow -> bindPlace(holder as PlaceHolder, item)
        }
    }

    /** 왼쪽 타임라인 열: 위 선 · 순서 점 · 아래 선 */
    private fun bindTimeline(h: PlaceHolder, row: DetailItem.PlaceRow) {
        val place = row.place
        val ctx = h.itemView.context
        h.timeline.visibility = if (row.timeline) View.VISIBLE else View.GONE
        h.lineAbove.visibility = if (row.isFirst) View.INVISIBLE else View.VISIBLE
        h.lineBelow.visibility = if (row.isLast) View.INVISIBLE else View.VISIBLE
        if (row.order != null) {
            h.dot.text = row.order
            h.dot.setBackgroundResource(R.drawable.bg_timeline_dot)
            h.dot.backgroundTintList = ColorStateList.valueOf(place.day?.let { PinIcons.dayColor(it) } ?: ctx.color(R.color.primary))
        } else {
            h.dot.backgroundTintList = null
            h.dot.text = ""
            h.dot.setBackgroundResource(R.drawable.bg_timeline_dot_hollow)
        }

    }

    private fun bindPlace(h: PlaceHolder, row: DetailItem.PlaceRow) {
        val place = row.place
        val ctx = h.itemView.context
        val confirmed = place.status == PlaceStatus.CONFIRMED

        bindTimeline(h, row)

        h.name.text = place.name
        val hours = place.openingHours.trim().replace(" ~ ", "–")
        // 후보 카드(시안 DetailCandidates): 예약필수는 뱃지 대신 '영업 … · 예약필수' 한 줄, 글자 13
        val inlineReservation = !confirmed && place.isReservationRequired
        h.summary.text = listOfNotNull(hours.takeIf { it.isNotEmpty() }?.let { ctx.getString(R.string.opening_hours_line, it) },
            if (inlineReservation) ctx.getString(R.string.reservation_required) else null).joinToString(" · ")
        h.summary.visibility = if (h.summary.text.isEmpty()) View.GONE else View.VISIBLE
        h.summary.textSize = if (confirmed) 14f else 13f
        Thumbs.bind(h.thumb, place.photoUri, place.category)
        bindBadges(h.badges, place, skipReservation = inlineReservation)
        (h.card.layoutParams as ViewGroup.MarginLayoutParams).apply {
            val m = ((if (confirmed) 4 else 5) * ctx.resources.displayMetrics.density).toInt(); topMargin = m; bottomMargin = m
        }

        // 순서 바꾸기: 손잡이를 누르면 바로 끌기 시작
        h.drag.visibility = if (dragEnabled && row.timeline && confirmed) View.VISIBLE else View.GONE
        h.drag.setOnTouchListener { _, e ->
            if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) onStartDrag?.invoke(h)
            false
        }
        h.confirm.visibility = if (confirmed) View.GONE else View.VISIBLE
        h.confirm.contentDescription = ctx.getString(R.string.confirm_place_a11y, place.name)
        h.confirm.setOnClickListener { listener.onConfirmPick(place, it) }

        h.card.contentDescription = listOfNotNull(row.order?.let { "$it." }, place.name, h.summary.text.takeIf { it.isNotEmpty() },
            badges(ctx, place).filterNot { inlineReservation && it.text == ctx.getString(R.string.reservation_required) }.joinToString(", ") { it.text }.takeIf { it.isNotEmpty() }).joinToString(" ")
        h.card.setOnClickListener { listener.onOpen(place) }
        h.card.setOnLongClickListener { listener.onEdit(place); true }
    }

    private data class B(val text: String, val bg: Int, val fg: Int)

    /** 예약필수(빨강) · 브레이크(회색) · 위치 없음(노랑) */
    private fun badges(ctx: android.content.Context, place: Place) = listOfNotNull(
        if (place.isReservationRequired) B(ctx.getString(R.string.reservation_required), R.color.error_container, R.color.on_error_container) else null,
        place.breakTime?.let { B(ctx.getString(R.string.break_badge, it.replace(" ~ ", "–")), R.color.surface_container, R.color.text_sub) },
        if (!place.hasLocation) B(ctx.getString(R.string.badge_no_location), R.color.warn_bg, R.color.warn_text) else null
    )

    /** 카드 뱃지: 최대 2개, 넘치면 +n */
    private fun bindBadges(box: LinearLayout, place: Place, skipReservation: Boolean = false) {
        val ctx = box.context
        box.removeAllViews()
        val all = badges(ctx, place).let { if (skipReservation) it.drop(if (place.isReservationRequired) 1 else 0) else it }
        val shown = all.take(MAX_BADGES)
        val inf = LayoutInflater.from(ctx)
        shown.forEach { b ->
            val tv = inf.inflate(R.layout.view_badge, box, false) as TextView
            tv.text = b.text
            tv.backgroundTintList = ColorStateList.valueOf(ctx.color(b.bg))
            tv.setTextColor(ctx.color(b.fg))
            box.addView(tv)
        }
        if (all.size > MAX_BADGES) {
            val tv = inf.inflate(R.layout.view_badge, box, false) as TextView
            tv.text = ctx.getString(R.string.badge_more, all.size - MAX_BADGES)
            tv.backgroundTintList = ColorStateList.valueOf(ctx.color(R.color.surface_container))
            tv.setTextColor(ctx.color(R.color.text_sub))
            box.addView(tv)
        }
        box.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
    }
}
