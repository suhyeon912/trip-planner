package com.tripplanner.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.LifecycleOwner
import com.tripplanner.ui.widget.TpButton
import com.tripplanner.R
import com.tripplanner.data.Lodging
import com.tripplanner.data.Place
import com.tripplanner.data.PlaceStatus

/**
 * 여행 상세의 '지도로 보기' (시안 DetailMap · DetailMapAll).
 *  - n일차: 그날 장소를 숙소 → 1 → 2 → … → 숙소 순서로 선을 이음
 *  - 전체: 일차마다 다른 색 + 후보는 속이 빈 점
 *  - 왼쪽 위 '일차 미정 n', 점을 누르면 아래 카드(지도 앱 · 자세히 · 수정 / 확정하기)
 *  - 맨 아래 '지도에 없는 장소' 판 → 위치 찾기
 */
class TripDetailMap(
    root: View,
    owner: LifecycleOwner,
    private val listener: Listener,
    private val onCardShown: (Boolean) -> Unit
) {

    /** 지도 아래 카드 · 판의 버튼들 */
    interface Listener {
        fun mapLabel(place: Place): String      // "카카오맵" 또는 "Google Maps"
        fun isDomestic(place: Place): Boolean
        fun onOpenMap(place: Place)
        fun onOpen(place: Place)
        fun onEdit(place: Place)
        fun onConfirmPick(place: Place, anchor: View)
        fun onFindLocation(place: Place)
    }

    private val ctx = root.context
    val container: View = root.findViewById(R.id.mapContainer)
    private val note: TextView = root.findViewById(R.id.mapNote)
    private val card: View = root.findViewById(R.id.mapCard)
    private val cardThumb: ImageView = card.findViewById(R.id.ivCardThumb)
    private val cardOrder: TextView = card.findViewById(R.id.tvCardOrder)
    private val cardName: TextView = card.findViewById(R.id.tvCardName)
    private val cardSub: TextView = card.findViewById(R.id.tvCardSub)
    private val cardMap: TpButton = card.findViewById(R.id.btnCardMap)
    private val cardView: Button = card.findViewById(R.id.btnCardView)
    private val cardEdit: Button = card.findViewById(R.id.btnCardEdit)
    private val cardConfirm: Button = card.findViewById(R.id.btnCardConfirm)
    private val missingPanel: View = root.findViewById(R.id.missingPanel)
    private val missingTitle: TextView = root.findViewById(R.id.tvMissingTitle)
    private val missingRows: LinearLayout = root.findViewById(R.id.missingRows)
    private val missingScroll: NestedScrollView = root.findViewById(R.id.missingScroll)

    /** 국내면 카카오맵, 해외면 Google Maps (키가 없으면 OpenStreetMap) */
    private val host = MapHost(root.findViewById<FrameLayout>(R.id.mapView), owner) { key -> select(key) }
    /** 지금 쓰는 지도 */
    val provider get() = host.provider
    private var shown: List<Place> = emptyList()
    private var selectedId: Long? = null
    /** 장소 id → 지도 점에 쓴 번호 (카드에도 같은 번호를 보여 주려고) */
    private val labels = HashMap<Long, String>()
    /** n일차 보기: 장소 id → (도착, 떠남) 분 */
    private val times = HashMap<Long, Pair<Int, Int>>()

    private fun range(a: Int, b: Int) = "${com.tripplanner.route.TimeUtil.format(a)} – ${com.tripplanner.route.TimeUtil.format(b)}"

    init {
        card.findViewById<View>(R.id.btnCardClose).setOnClickListener { select(null) }
    }

    /**
     * @param places 고른 일차(또는 전체)의 확정 장소들 (순서 그대로)
     * @param candidates '전체'일 때 함께 찍을 후보 (속 빈 점)
     * @param selectedDay 0 = 전체, -1 = 일차 미정, 1 이상 = n일차
     * @param lodgings 지도에 함께 찍을 숙소
     */
    fun render(places: List<Place>, candidates: List<Place>, selectedDay: Int, lodgings: List<Lodging>,
               tripCoords: List<Pair<Double, Double>>, startMinutes: Int = com.tripplanner.route.RoutePlanner.DEFAULT_START,
               legTime: com.tripplanner.route.LegTime = com.tripplanner.route.RoutePlanner.DEFAULT_LEG) {
        shown = places + candidates
        labels.clear()
        times.clear()
        // n일차: 지금 순서대로 숙소 출발 → 장소마다 도착·떠남 → 숙소 도착 (경로 제안과 같은 계산)
        var homeCaption: String? = null
        if (selectedDay > 0) {
            val todays = places.filter { it.hasLocation && it.day == selectedDay }
            val home = lodgings.singleOrNull { it.lat != 0.0 || it.lng != 0.0 }
            if (todays.isNotEmpty()) {
                val anchor = home?.let { com.tripplanner.route.Anchor(it.name, it.lat, it.lng) }
                val stops = todays.map { p ->
                    com.tripplanner.route.Stop(p.id, p.name, p.lat, p.lng,
                        open = com.tripplanner.route.TimeUtil.parseRange(p.openingHours),
                        breakTime = com.tripplanner.route.TimeUtil.parseRange(p.breakTime), stayMinutes = p.stayMinutes)
                }
                val sim = com.tripplanner.route.RoutePlanner.simulate(anchor, stops, startMinutes, legTime)
                sim.visits.forEach { v -> times[v.stop.id] = v.arrive to v.leave }
                if (anchor != null) homeCaption = range(startMinutes, sim.endTime)
            }
        }
        val located = places.filter { it.hasLocation }
        val pins = mutableListOf<MapPin>()
        val paths = mutableListOf<MapPath>()
        val gray = ContextCompat.getColor(ctx, R.color.outline)
        val primary = ContextCompat.getColor(ctx, R.color.primary)

        val homes = lodgings.filter { it.lat != 0.0 || it.lng != 0.0 }
        homes.forEach { l ->
            pins += MapPin(-1, l.lat, l.lng, "", ContextCompat.getColor(ctx, R.color.on_surface), MapPin.Style.LODGING,
                ctx.getString(R.string.map_pin_a11y, ctx.getString(R.string.map_lodging), l.name) +
                    (homeCaption?.let { ", $it" } ?: ""), caption = homeCaption)
        }
        // 일차별로 묶어서 순서대로 선 + 번호 점
        located.groupBy { it.day }.forEach { (day, group) ->
            val color = if (day == null) gray else PinIcons.dayColor(day)
            group.forEachIndexed { i, p ->
                val label = if (day == null) "" else (p.orderInDay ?: (i + 1)).toString()
                labels[p.id] = label
                val caption = times[p.id]?.let { (a, b) -> range(a, b) }
                pins += MapPin(p.id, p.lat, p.lng, label, color, MapPin.Style.FILLED,
                    ctx.getString(R.string.map_pin_a11y, day?.let { ctx.dayLabel(it) } ?: ctx.getString(R.string.day_none), p.name) +
                        (caption?.let { ", $it" } ?: ""), caption = caption)
            }
            if (day != null) {
                val line = group.map { it.lat to it.lng }.toMutableList()
                // 고른 일차면 숙소에서 출발해 숙소로 돌아오는 선
                if (selectedDay == day && homes.size == 1) {
                    val h = homes[0]
                    line.add(0, h.lat to h.lng); line.add(h.lat to h.lng)
                }
                paths += MapPath(line, color)
            }
        }
        candidates.filter { it.hasLocation }.forEach { p ->
            pins += MapPin(p.id, p.lat, p.lng, "", primary, MapPin.Style.HOLLOW,
                ctx.getString(R.string.map_pin_a11y, ctx.getString(R.string.status_candidate), p.name))
        }
        // 지도 종류는 여행 전체 기준 (해외가 한 곳이라도 있으면 Google) → 일차를 바꿔도 같은 지도
        host.render(pins, paths, tripPoints = tripCoords)

        // 왼쪽 위: 일차 미정 n (전체 보기)
        val undated = places.count { it.day == null }
        note.visibility = if (undated > 0 && selectedDay == TripDetailViewModel.DAY_ALL) View.VISIBLE else View.GONE
        note.text = ctx.getString(R.string.map_undated, undated)

        renderMissing(places.filter { !it.hasLocation })

        // 고른 장소가 사라졌으면 카드 닫기, 남아 있으면 내용 갱신
        select(selectedId?.takeIf { id -> shown.any { it.id == id && it.hasLocation } })
    }

    /** 지도에 없는 장소 판: 최대 3줄 높이, 넘치면 스크롤 */
    private fun renderMissing(missing: List<Place>) {
        missingPanel.visibility = if (missing.isEmpty()) View.GONE else View.VISIBLE
        missingTitle.text = ctx.getString(R.string.missing_title, missing.size)
        missingRows.removeAllViews()
        val inf = LayoutInflater.from(ctx)
        missing.forEach { p ->
            val row = inf.inflate(R.layout.item_missing_place, missingRows, false)
            Thumbs.bind(row.findViewById(R.id.ivMissingThumb), p)
            row.findViewById<TextView>(R.id.tvMissingName).text = p.name
            row.findViewById<TextView>(R.id.tvMissingSub).apply {
                text = p.day?.let { ctx.dayLabel(it) } ?: ""
                visibility = if (p.day == null) View.GONE else View.VISIBLE
            }
            row.findViewById<View>(R.id.btnFindLocation).apply {
                contentDescription = ctx.getString(R.string.find_location_a11y, p.name)
                setOnClickListener { listener.onFindLocation(p) }
            }
            missingRows.addView(row)
        }
        val maxPx = (64 * 3 * ctx.resources.displayMetrics.density).toInt()
        missingScroll.layoutParams = missingScroll.layoutParams.apply {
            height = if (missing.size > 3) maxPx else LinearLayout.LayoutParams.WRAP_CONTENT
        }
    }

    /** 장소 정보의 '여행 지도에서': 이 장소를 골라 아래 카드 열기 (지도가 아직이면 그린 뒤에) */
    fun focus(id: Long) {
        selectedId = id
        if (shown.any { it.id == id }) select(id)
    }

    private fun select(id: Long?) {
        val place = shown.firstOrNull { it.id == id }
        selectedId = place?.id
        card.visibility = if (place == null) View.GONE else View.VISIBLE
        onCardShown(place != null)
        if (place == null) return

        val confirmed = place.status == PlaceStatus.CONFIRMED
        val day = place.day
        val color = when {
            !confirmed -> ContextCompat.getColor(ctx, R.color.primary)
            day == null -> ContextCompat.getColor(ctx, R.color.outline)
            else -> PinIcons.dayColor(day)
        }
        Thumbs.bind(cardThumb, place)
        val label = labels[place.id].orEmpty()
        cardOrder.visibility = if (confirmed && label.isNotEmpty()) View.VISIBLE else View.GONE
        cardOrder.text = label
        cardOrder.backgroundTintList = ColorStateList.valueOf(color)
        cardName.text = place.name
        val hours = place.openingHours.trim().replace(" ~ ", "–")
        val visit = times[place.id]
        cardSub.text = if (visit != null) ctx.getString(R.string.map_card_times,
            com.tripplanner.route.TimeUtil.format(visit.first), com.tripplanner.route.TimeUtil.format(visit.second))
        else listOfNotNull(
            if (!confirmed) ctx.getString(R.string.status_candidate) else day?.let { ctx.dayLabel(it) } ?: ctx.getString(R.string.day_none),
            hours.takeIf { it.isNotEmpty() }?.let { ctx.getString(R.string.opening_hours_line, it) }
        ).joinToString(ctx.getString(R.string.list_separator))
        // 지도 앱: 국내 = 카카오맵 아이콘 + 글자, 해외 = 글자만
        cardMap.text = listener.mapLabel(place)
        cardMap.icon = if (listener.isDomestic(place)) ContextCompat.getDrawable(ctx, R.drawable.ic_kakaomap) else null
        cardMap.setOnClickListener { listener.onOpenMap(place) }
        cardView.setOnClickListener { listener.onOpen(place) }
        cardEdit.setOnClickListener { listener.onEdit(place) }
        cardConfirm.visibility = if (confirmed) View.GONE else View.VISIBLE
        cardEdit.visibility = if (confirmed) View.VISIBLE else View.GONE
        cardConfirm.contentDescription = ctx.getString(R.string.confirm_place_a11y, place.name)
        cardConfirm.setOnClickListener { listener.onConfirmPick(place, it) }
    }
}
