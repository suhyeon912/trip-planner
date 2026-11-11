package com.tripplanner.ui

import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R
import com.tripplanner.data.Place
import com.tripplanner.data.PlaceStatus

/** 상세 화면 목록의 한 줄 종류 */
sealed class DetailItem {
    /** '전체' 보기의 일차 제목 */
    data class Header(val title: String, val sub: String, val day: Int? = null) : DetailItem()
    /** 타임라인 맨 위 "OO 호텔에서 출발" */
    data class Start(val text: String) : DetailItem()
    /** 장소 카드. order = 방문 순서(경로 확정 전이면 null), timeline = 왼쪽 타임라인 열 표시 */
    data class PlaceRow(val place: Place, val order: String?, val isFirst: Boolean, val isLast: Boolean, val timeline: Boolean) : DetailItem()
    /** "n일차 경로 제안 보기" */
    data class RouteCta(val day: Int) : DetailItem()
}

/**
 * 여행 상세 목록 어댑터 (3단계).
 * 장소 카드 머리줄을 누르면 펼쳐졌다 접혔다 합니다(기존 동작).
 * 펼친 카드: [지도] [메모] [수정] … [⋮ 일차 변경 / 후보로·확정으로 / 삭제]
 */
class PlaceAdapter(
    private val listener: Listener,
    private val onRouteCta: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Listener {
        fun mapLabel(place: Place): String      // "카카오맵" 또는 "구글맵"
        fun onOpenMap(place: Place)
        fun onEditMemo(place: Place)
        fun onEdit(place: Place)
        fun onSetDay(place: Place)
        fun onToggleStatus(place: Place)
        fun onDelete(place: Place)
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_START = 1
        private const val TYPE_PLACE = 2
        private const val TYPE_CTA = 3
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
        items = list
        notifyItemMoved(from, to)
        return true
    }
    private val expandedIds = mutableSetOf<Long>()   // 펼쳐진 카드의 id 모음

    fun submit(newList: List<DetailItem>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is DetailItem.Header -> TYPE_HEADER
        is DetailItem.Start -> TYPE_START
        is DetailItem.PlaceRow -> TYPE_PLACE
        is DetailItem.RouteCta -> TYPE_CTA
    }

    override fun getItemCount() = items.size

    // ---------------- ViewHolders ----------------
    class HeaderHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvHeaderTitle)
        val sub: TextView = v.findViewById(R.id.tvHeaderSub)
    }
    class StartHolder(v: View) : RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewById(R.id.tvStart)
    }
    class CtaHolder(v: View) : RecyclerView.ViewHolder(v) {
        val button: Button = v.findViewById(R.id.btnRouteCta)
    }
    class PlaceHolder(v: View) : RecyclerView.ViewHolder(v) {
        val timeline: View = v.findViewById(R.id.timeline)
        val lineAbove: View = v.findViewById(R.id.lineAbove)
        val lineBelow: View = v.findViewById(R.id.lineBelow)
        val dot: TextView = v.findViewById(R.id.tvDot)
        val header: View = v.findViewById(R.id.header)
        val name: TextView = v.findViewById(R.id.tvPlaceName)
        val summary: TextView = v.findViewById(R.id.tvSummary)
        val badges: View = v.findViewById(R.id.badges)
        val reservation: TextView = v.findViewById(R.id.tvReservationBadge)
        val breakBadge: TextView = v.findViewById(R.id.tvBreakBadge)
        val confirm: Button = v.findViewById(R.id.btnConfirm)
        val expand: ImageView = v.findViewById(R.id.ivExpand)
        val thumb: ImageView = v.findViewById(R.id.ivThumb)
        val drag: ImageView = v.findViewById(R.id.ivDrag)
        val detail: View = v.findViewById(R.id.detail)
        val infoRows: LinearLayout = v.findViewById(R.id.infoRows)
        val map: Button = v.findViewById(R.id.btnMap)
        val memo: Button = v.findViewById(R.id.btnMemo)
        val edit: Button = v.findViewById(R.id.btnEdit)
        val more: ImageButton = v.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(inf.inflate(R.layout.item_day_header, parent, false))
            TYPE_START -> StartHolder(inf.inflate(R.layout.item_timeline_start, parent, false))
            TYPE_CTA -> CtaHolder(inf.inflate(R.layout.item_route_cta, parent, false))
            else -> PlaceHolder(inf.inflate(R.layout.item_place, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DetailItem.Header -> (holder as HeaderHolder).apply {
                title.text = item.title
                sub.text = item.sub
                // 일차 제목은 지도와 같은 일차 색
                val c = item.day?.let { PinIcons.dayColor(it) } ?: title.context.color(R.color.on_surface)
                title.setTextColor(c)
            }
            is DetailItem.Start -> (holder as StartHolder).text.text = item.text
            is DetailItem.RouteCta -> (holder as CtaHolder).button.apply {
                text = context.getString(R.string.route_cta, item.day)
                setOnClickListener { onRouteCta(item.day) }
            }
            is DetailItem.PlaceRow -> bindPlace(holder as PlaceHolder, item)
        }
    }

    private fun bindPlace(h: PlaceHolder, row: DetailItem.PlaceRow) {
        val place = row.place
        val ctx = h.itemView.context
        val expanded = place.id in expandedIds
        val confirmed = place.status == PlaceStatus.CONFIRMED

        // 타임라인 열
        h.timeline.visibility = if (row.timeline) View.VISIBLE else View.GONE
        h.lineAbove.visibility = if (row.isFirst) View.INVISIBLE else View.VISIBLE
        h.lineBelow.visibility = if (row.isLast) View.INVISIBLE else View.VISIBLE
        if (row.order != null) {
            h.dot.text = row.order
            h.dot.setBackgroundResource(R.drawable.bg_timeline_dot)
            // 지도·일차 제목과 같은 일차 색
            h.dot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                place.day?.let { PinIcons.dayColor(it) } ?: ctx.color(R.color.primary))
        } else {
            h.dot.backgroundTintList = null
            h.dot.text = ""
            h.dot.setBackgroundResource(R.drawable.bg_timeline_dot_hollow)
        }

        // 머리줄
        h.name.text = place.name
        h.name.maxLines = if (expanded) 3 else 1
        h.summary.text = listOf(place.category.label, place.openingHours.replace(" ~ ", "–"),
            ctx.getString(R.string.minutes, place.stayMinutes))
            .joinToString(ctx.getString(R.string.list_separator))
        bindThumb(h.thumb, place)
        // 순서 바꾸기: 손잡이를 누르면 바로 끌기 시작
        h.drag.visibility = if (dragEnabled && row.timeline && confirmed) View.VISIBLE else View.GONE
        h.drag.setOnTouchListener { _, e ->
            if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) onStartDrag?.invoke(h)
            false
        }
        h.reservation.visibility = if (place.isReservationRequired) View.VISIBLE else View.GONE
        h.breakBadge.visibility = if (place.breakTime != null) View.VISIBLE else View.GONE
        h.breakBadge.text = ctx.getString(R.string.break_badge, place.breakTime?.replace(" ~ ", "–").orEmpty())
        h.badges.visibility = if (place.isReservationRequired || place.breakTime != null) View.VISIBLE else View.GONE
        h.confirm.visibility = if (confirmed) View.GONE else View.VISIBLE
        h.confirm.contentDescription = ctx.getString(R.string.confirm_place_a11y, place.name)
        h.confirm.setOnClickListener { listener.onToggleStatus(place) }
        h.expand.setImageResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
        ViewCompat.setStateDescription(h.header, ctx.getString(if (expanded) R.string.state_expanded else R.string.state_collapsed))
        h.header.setOnClickListener {
            if (expanded) expandedIds.remove(place.id) else expandedIds.add(place.id)
            notifyItemChanged(h.bindingAdapterPosition)
        }

        // 펼친 영역
        h.detail.visibility = if (expanded) View.VISIBLE else View.GONE
        if (expanded) {
            h.infoRows.removeAllViews()
            fun info(label: String, value: String) {
                val v = LayoutInflater.from(ctx).inflate(R.layout.item_info_row, h.infoRows, false)
                v.findViewById<TextView>(R.id.tvLabel).text = label
                v.findViewById<TextView>(R.id.tvValue).text = value
                h.infoRows.addView(v)
            }
            fun s(id: Int) = ctx.getString(id)
            info(s(R.string.info_hours), place.openingHours)
            place.breakTime?.let { info(s(R.string.info_break), it) }
            place.closedDays?.let { info(s(R.string.info_closed), it) }
            info(s(R.string.info_reservation), s(if (place.isReservationRequired) R.string.reservation_yes else R.string.reservation_no))
            info(s(R.string.info_stay), ctx.getString(R.string.stay_about, place.stayMinutes))
            if (!place.hasLocation) info(s(R.string.info_location), s(R.string.no_location_info))
            if (place.memo.isNotBlank()) info(s(R.string.action_memo), place.memo)

            h.map.text = listener.mapLabel(place)
            h.map.setOnClickListener { listener.onOpenMap(place) }
            h.memo.setOnClickListener { listener.onEditMemo(place) }
            h.edit.setOnClickListener { listener.onEdit(place) }
            h.more.contentDescription = ctx.getString(R.string.more_a11y, place.name)
            h.more.setOnClickListener { showMoreMenu(it, place) }
        }
    }

    /** ⋮ 메뉴: 일차 변경(확정만) / 후보로 옮기기·확정으로 / 삭제(빨간 글자) */
    private fun showMoreMenu(anchor: View, place: Place) {
        val popup = PopupMenu(anchor.context, anchor)
        popup.inflate(R.menu.place_actions)
        popup.markDanger(anchor.context, R.id.action_delete)
        val confirmed = place.status == PlaceStatus.CONFIRMED
        popup.menu.findItem(R.id.action_day).isVisible = confirmed
        popup.menu.findItem(R.id.action_status).setTitle(if (confirmed) R.string.move_to_candidate else R.string.move_to_confirmed)
        popup.menu.findItem(R.id.action_delete).title = SpannableString(anchor.context.getString(R.string.action_delete)).apply {
            setSpan(ForegroundColorSpan(ContextCompat.getColor(anchor.context, R.color.badge_red)), 0, length, 0)
        }
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_day -> listener.onSetDay(place)
                R.id.action_status -> listener.onToggleStatus(place)
                R.id.action_delete -> listener.onDelete(place)
            }
            true
        }
        popup.show()
    }

    /** 사진이 있으면 사진, 없으면 장소 종류 아이콘(색 배경). 모서리 12dp 로 둥글게 자름 */
    private fun bindThumb(v: ImageView, place: com.tripplanner.data.Place) = Thumbs.bind(v, place.photoUri, place.category)
}
