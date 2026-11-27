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

/** '전체' 맨 위 요약 한 줄: 아이콘 · 글자 · (개수) */
data class SummaryLine(val icon: Int, val text: String, val count: Int? = null)

/** 상세 화면 목록의 한 줄 종류 */
sealed class DetailItem {
    /** 일차 제목 "● 1일차 11월 20일 (금)" (+ '전체'에서는 오른쪽 '경로 제안') */
    data class Header(val title: String, val sub: String, val day: Int? = null, val routeLink: Boolean = false) : DetailItem()
    /** 장소 카드. order = 방문 순서(경로 확정 전이면 null), timeline = 왼쪽 타임라인 열 표시 */
    data class PlaceRow(val place: Place, val order: String?, val isFirst: Boolean, val isLast: Boolean, val timeline: Boolean,
                        /** 붙은 예약·티켓 자료 수 ('티켓 n' 뱃지) */
                        val files: Int = 0) : DetailItem()
    /** 그날 출발 · 도착 숙소 (체크인 · 체크아웃 날짜로 자동, 끌어서 옮기지 않음) */
    data class LodgingRow(val lodging: com.tripplanner.data.Lodging, val day: Int, val isStart: Boolean,
                          /** 그날이 체크아웃(출발) · 체크인(도착) 날이면 그 시각 "11:00", 아니면 빈 글자 */
                          val time: String = "") : DetailItem()
    /** '전체' 맨 위 교통 · 숙소 요약 */
    data class Summary(val lines: List<SummaryLine>) : DetailItem()
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
        /** 끌기 손잡이만 다시 그릴 때 */
        private const val PAYLOAD_DRAG = "drag"
        private const val TYPE_HEADER = 0
        private const val TYPE_PLACE = 2
        private const val TYPE_SUMMARY = 3
        private const val TYPE_MEMO = 4
        private const val TYPE_EMPTY = 5
        private const val TYPE_LODGING = 6
        /** 카드에 보이는 뱃지는 최대 2개, 넘치면 +n */
        private const val MAX_BADGES = 2
    }

    private var items: List<DetailItem> = emptyList()
    /** 일차 하나를 보고 있을 때만 순서 끌기 가능 (바뀌면 다음 submit 에서 모든 줄의 손잡이를 다시 그림) */
    var dragEnabled = false
        set(v) { if (field != v) { field = v; dragChanged = true } }
    private var dragChanged = false
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
        val old = items
        items = newList
        ListDiff.dispatch(this, old, newList, ::sameItem)
        // '전체' → 일차로 바꿨을 때 내용이 같은 줄도 손잡이를 보이게 (내용이 같으면 다시 그리지 않아서)
        if (dragChanged) { dragChanged = false; if (newList.isNotEmpty()) notifyItemRangeChanged(0, newList.size, PAYLOAD_DRAG) }
    }

    private fun sameItem(a: DetailItem, b: DetailItem) = when (a) {
        is DetailItem.PlaceRow -> b is DetailItem.PlaceRow && a.place.id == b.place.id
        is DetailItem.Header -> b is DetailItem.Header && (if (a.day != null) a.day == b.day else b.day == null && a.title == b.title)
        is DetailItem.Summary -> b is DetailItem.Summary
        is DetailItem.Memo -> b is DetailItem.Memo
        is DetailItem.Empty -> b is DetailItem.Empty && a.state == b.state
        is DetailItem.LodgingRow -> b is DetailItem.LodgingRow && a.lodging.id == b.lodging.id && a.day == b.day && a.isStart == b.isStart
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is DetailItem.Header -> TYPE_HEADER
        is DetailItem.PlaceRow -> TYPE_PLACE
        is DetailItem.Summary -> TYPE_SUMMARY
        is DetailItem.Memo -> TYPE_MEMO
        is DetailItem.Empty -> TYPE_EMPTY
        is DetailItem.LodgingRow -> TYPE_LODGING
    }

    override fun getItemCount() = items.size

    // ---------------- ViewHolders ----------------
    class HeaderHolder(v: View) : RecyclerView.ViewHolder(v) {
        val dot: View = v.findViewById(R.id.vHeaderDot)
        val title: TextView = v.findViewById(R.id.tvHeaderTitle)
        val sub: TextView = v.findViewById(R.id.tvHeaderSub)
        val route: Button = v.findViewById(R.id.btnHeaderRoute)
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
    /** 숙소 줄: 장소 카드와 같은 모양 (끌기 대상이 아니라 PlaceHolder 와 다른 클래스) */
    class LodgingHolder(v: View) : RecyclerView.ViewHolder(v) {
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(inf.inflate(R.layout.item_day_header, parent, false))
            TYPE_SUMMARY -> SummaryHolder(inf.inflate(R.layout.item_detail_summary, parent, false).also { Motion.clipRoundedCard(it) })
            TYPE_MEMO -> MemoHolder(inf.inflate(R.layout.item_detail_memo, parent, false))
            TYPE_EMPTY -> EmptyHolder(inf.inflate(R.layout.view_empty_state, parent, false).apply {
                setPadding(paddingLeft, (40 * resources.displayMetrics.density).toInt(), paddingRight,
                    (24 * resources.displayMetrics.density).toInt())
            })
            TYPE_LODGING -> LodgingHolder(inf.inflate(R.layout.item_place, parent, false))
            else -> PlaceHolder(inf.inflate(R.layout.item_place, parent, false))
        }
    }

    /** 끌기 중 순서 점만 다시 그림 (카드는 그대로) */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        val row = items.getOrNull(position)
        if (PAYLOAD_TIMELINE in payloads && holder is PlaceHolder && row is DetailItem.PlaceRow) bindTimeline(holder, row)
        else if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_DRAG }) {
            if (holder is PlaceHolder && row is DetailItem.PlaceRow) holder.drag.visibility =
                if (dragEnabled && row.timeline && row.place.status == PlaceStatus.CONFIRMED) View.VISIBLE else View.GONE
        }
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
            is DetailItem.Summary -> (holder as SummaryHolder).apply {
                rows.removeAllViews()
                val inf = LayoutInflater.from(itemView.context)
                item.lines.forEachIndexed { i, l ->
                    val row = inf.inflate(R.layout.item_summary_line, rows, false)
                    row.findViewById<TextView>(R.id.tvSummaryLine).text = l.text
                    row.findViewById<ImageView>(R.id.ivSummaryIcon).setImageResource(l.icon)
                    row.findViewById<TextView>(R.id.tvSummaryCount).apply {
                        visibility = if (l.count == null) View.GONE else View.VISIBLE
                        text = l.count?.toString()
                    }
                    row.contentDescription = listOfNotNull(l.text, l.count?.let { itemView.context.getString(R.string.count_items, it) }).joinToString(", ")
                    row.findViewById<View>(R.id.divider).visibility = if (i == 0) View.GONE else View.VISIBLE
                    row.setOnClickListener { listener.onOpenTravel() }
                    rows.addView(row)
                }
            }
            is DetailItem.Memo -> (holder as MemoHolder).apply {
                note.text = item.note.trim()
                note.visibility = if (item.note.isBlank()) View.GONE else View.VISIBLE
                // 메모가 없으면 제목 줄만 (아래 여백 없이 '교통 · 숙소' 카드와 같은 높이)
                itemView.setPadding(itemView.paddingLeft, itemView.paddingTop, itemView.paddingRight,
                    if (item.note.isBlank()) 0 else itemView.resources.getDimensionPixelSize(R.dimen.space_m))
                itemView.setOnClickListener { v -> v.jumpDrawablesToCurrentState(); listener.onOpenNote() }  // 팝업 뒤에 눌림 색이 남지 않게
            }
            is DetailItem.Empty -> bindEmptyState(holder.itemView, item.state) { listener.onEmptyAction(it) }
            is DetailItem.PlaceRow -> bindPlace(holder as PlaceHolder, item)
            is DetailItem.LodgingRow -> bindLodging(holder as LodgingHolder, item)
        }
    }

    /** 숙소 줄 (시안 Detail: 숙소 · 출발 / 숙소 · 도착), 누르면 교통 · 숙소 화면 */
    private fun bindLodging(h: LodgingHolder, row: DetailItem.LodgingRow) {
        val ctx = h.itemView.context
        val l = row.lodging
        // 타임라인: 출발 숙소는 맨 위(위 선 없음), 도착 숙소는 맨 아래(아래 선 없음)
        h.lineAbove.visibility = if (row.isStart) View.INVISIBLE else View.VISIBLE
        h.lineBelow.visibility = if (row.isStart) View.VISIBLE else View.INVISIBLE
        h.dot.text = ""
        h.dot.backgroundTintList = null
        h.dot.setBackgroundResource(R.drawable.bg_timeline_lodging)
        h.name.text = l.name
        // 체크아웃 날 출발 · 체크인 날 도착이면 그 시각도 (예: 숙소 · 출발 · 체크아웃 11:00)
        val time = row.time.takeIf { it.isNotBlank() }?.let { ctx.getString(if (row.isStart) R.string.lodging_checkout_at else R.string.lodging_checkin_at, it) }
        h.summary.text = listOfNotNull(ctx.getString(if (row.isStart) R.string.lodging_depart else R.string.lodging_arrive), time).joinToString(" · ")
        h.summary.visibility = View.VISIBLE
        h.summary.textSize = 14f
        Thumbs.bind(h.thumb, null, R.drawable.ic_hotel, R.color.surface_container)
        h.badges.removeAllViews()
        if (l.lat == 0.0 && l.lng == 0.0) {
            val tv = LayoutInflater.from(ctx).inflate(R.layout.view_badge, h.badges, false) as TextView
            tv.text = ctx.getString(R.string.badge_no_location)
            tv.backgroundTintList = ColorStateList.valueOf(ctx.color(R.color.warn_bg))
            tv.setTextColor(ctx.color(R.color.warn_text))
            h.badges.addView(tv)
            h.badges.visibility = View.VISIBLE
        } else h.badges.visibility = View.GONE
        h.drag.visibility = View.GONE
        h.confirm.visibility = View.GONE
        h.card.contentDescription = ctx.getString(R.string.lodging_row_a11y, l.name, h.summary.text)
        h.card.setOnClickListener { listener.onOpenTravel() }
        h.card.setOnLongClickListener(null)
        h.card.isLongClickable = false
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
        val hours = com.tripplanner.route.TimeUtil.displayRange(place.openingHours.trim())
        // 후보 카드(시안 DetailCandidates): 예약필수는 뱃지 대신 '영업 … · 예약필수' 한 줄, 글자 13
        val inlineReservation = !confirmed && place.isReservationRequired
        h.summary.text = listOfNotNull(hours.takeIf { it.isNotEmpty() }?.let { ctx.getString(R.string.opening_hours_line, it) },
            if (inlineReservation) ctx.getString(R.string.reservation_required) else null).joinToString(" · ")
        h.summary.visibility = if (h.summary.text.isEmpty()) View.GONE else View.VISIBLE
        h.summary.textSize = if (confirmed) 14f else 13f
        Thumbs.bind(h.thumb, place)
        bindBadges(h.badges, place, skipReservation = inlineReservation, files = row.files)
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
            badges(ctx, place, row.files).filterNot { inlineReservation && it.text == ctx.getString(R.string.reservation_required) }.joinToString(", ") { it.text }.takeIf { it.isNotEmpty() }).joinToString(" ")
        h.card.setOnClickListener { listener.onOpen(place) }
        h.card.setOnLongClickListener { listener.onEdit(place); true }
    }

    private data class B(val text: String, val bg: Int, val fg: Int)

    /** 예약필수(빨강) · 브레이크(회색) · 위치 없음(노랑) */
    private fun badges(ctx: android.content.Context, place: Place, files: Int = 0) = listOfNotNull(
        if (place.isReservationRequired) B(ctx.getString(R.string.reservation_required), R.color.error_container, R.color.on_error_container) else null,
        if (files > 0) B(ctx.getString(R.string.badge_tickets, files), R.color.primary_light, R.color.on_primary_container) else null,
        place.breakTime?.let { B(ctx.getString(R.string.break_badge, com.tripplanner.route.TimeUtil.displayRange(it)), R.color.surface_container, R.color.text_sub) },
        if (!place.hasLocation) B(ctx.getString(R.string.badge_no_location), R.color.warn_bg, R.color.warn_text) else null
    )

    /** 카드 뱃지: 최대 2개, 넘치면 +n */
    private fun bindBadges(box: LinearLayout, place: Place, skipReservation: Boolean = false, files: Int = 0) {
        val ctx = box.context
        box.removeAllViews()
        val all = badges(ctx, place, files).let { if (skipReservation) it.drop(if (place.isReservationRequired) 1 else 0) else it }
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
