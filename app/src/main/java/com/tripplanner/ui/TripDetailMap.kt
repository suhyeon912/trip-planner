package com.tripplanner.ui

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tripplanner.R
import com.tripplanner.data.Lodging
import com.tripplanner.data.Place
import com.tripplanner.data.PlaceStatus
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner

/**
 * 여행 상세의 '지도로 보기'.
 * 목록에 보이는 것과 같은 장소(탭·일차 칩 선택 그대로)를 지도에 찍습니다.
 *  - 일정 · 전체: 일차마다 다른 색, 같은 일차는 방문 순서대로 선으로 이음
 *  - 일정 · n일차: 숙소 → 1 → 2 → … → 숙소
 *  - 후보: 속이 빈 점
 * 점을 누르면 아래 카드에서 지도 앱 열기 · 수정 · 확정을 할 수 있습니다 (기존 목록과 같은 동작).
 */
class TripDetailMap(
    root: View,
    owner: LifecycleOwner,
    private val listener: Listener,
    private val onCardShown: (Boolean) -> Unit
) {

    /** 지도 아래 카드의 버튼들 */
    interface Listener {
        fun mapLabel(place: Place): String      // "카카오맵" 또는 "Google Maps"
        fun onOpenMap(place: Place)
        fun onEdit(place: Place)
        fun onConfirmPick(place: Place, anchor: View)
    }

    private val ctx = root.context
    val container: View = root.findViewById(R.id.mapContainer)
    private val legend: LinearLayout = root.findViewById(R.id.mapLegend)
    private val note: TextView = root.findViewById(R.id.mapNote)
    private val card: View = root.findViewById(R.id.mapCard)
    private val cardOrder: TextView = card.findViewById(R.id.tvCardOrder)
    private val cardName: TextView = card.findViewById(R.id.tvCardName)
    private val cardSub: TextView = card.findViewById(R.id.tvCardSub)
    private val cardMap: Button = card.findViewById(R.id.btnCardMap)
    private val cardEdit: Button = card.findViewById(R.id.btnCardEdit)
    private val cardConfirm: Button = card.findViewById(R.id.btnCardConfirm)

    /** 국내면 카카오맵, 해외면 구글맵 (키가 없으면 OpenStreetMap) */
    private val host = MapHost(root.findViewById<FrameLayout>(R.id.mapView), owner) { key -> select(key) }
    /** 지금 쓰는 지도 (테스트·디버그용) */
    val provider get() = host.provider
    private var shown: List<Place> = emptyList()
    private var selectedId: Long? = null
    /** 장소 id → 지도 점에 쓴 번호 (카드에도 같은 번호를 보여 주려고) */
    private val labels = HashMap<Long, String>()

    init {
        card.findViewById<View>(R.id.btnCardClose).setOnClickListener { select(null) }
    }

    /**
     * @param places 지금 목록에 보이는 장소들 (순서 그대로)
     * @param selectedDay 일정 탭에서 고른 일차 (0 = 전체, -1 = 일차 미정)
     * @param lodgings 지도에 함께 찍을 숙소
     */
    fun render(places: List<Place>, candidatesTab: Boolean, selectedDay: Int, lodgings: List<Lodging>,
               tripCoords: List<Pair<Double, Double>>) {
        shown = places
        labels.clear()
        val located = places.filter { it.hasLocation }
        val pins = mutableListOf<MapPin>()
        val paths = mutableListOf<MapPath>()
        val gray = ContextCompat.getColor(ctx, R.color.outline)
        val primary = ContextCompat.getColor(ctx, R.color.primary)

        val homes = lodgings.filter { it.lat != 0.0 || it.lng != 0.0 }
        homes.forEach { l ->
            pins += MapPin(-1, l.lat, l.lng, "", ContextCompat.getColor(ctx, R.color.on_surface), MapPin.Style.LODGING,
                ctx.getString(R.string.map_pin_a11y, ctx.getString(R.string.map_lodging), l.name))
        }

        if (candidatesTab) {
            located.forEach { p ->
                pins += MapPin(p.id, p.lat, p.lng, "", primary, MapPin.Style.HOLLOW, p.name)
            }
        } else {
            // 일차별로 묶어서 순서대로 선 + 번호 점
            located.groupBy { it.day }.forEach { (day, group) ->
                val color = if (day == null) gray else PinIcons.dayColor(day)
                group.forEachIndexed { i, p ->
                    val label = if (day == null) "" else (p.orderInDay ?: (i + 1)).toString()
                    labels[p.id] = label
                    pins += MapPin(p.id, p.lat, p.lng, label, color, MapPin.Style.FILLED,
                        ctx.getString(R.string.map_pin_a11y, day?.let { ctx.dayLabel(it) } ?: ctx.getString(R.string.day_none), p.name))
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
        }
        // 숙소는 범위 계산에서 빼지 않음 (출발점이니까). 숙소만 있고 장소가 없으면 숙소 중심
        // 지도 종류는 여행 전체 기준 (해외가 한 곳이라도 있으면 구글) → 탭·일차를 바꿔도 같은 지도
        host.render(pins, paths, tripPoints = tripCoords)

        // 범례: 전체 보기에서 일차가 둘 이상일 때
        legend.removeAllViews()
        val days = if (candidatesTab || selectedDay != TripDetailViewModel.DAY_ALL) emptyList()
                   else located.mapNotNull { it.day }.distinct().sorted()
        legend.visibility = if (days.size >= 2) View.VISIBLE else View.GONE
        days.forEach { d -> legend.addView(legendItem(ctx.dayLabel(d), PinIcons.dayColor(d))) }

        // 안내: 위치가 없는 장소
        val missing = places.size - located.size
        note.visibility = if (missing > 0 || located.isEmpty()) View.VISIBLE else View.GONE
        note.text = if (located.isEmpty()) ctx.getString(R.string.map_nothing) else ctx.getString(R.string.map_no_location, missing)

        // 고른 장소가 사라졌으면 카드 닫기, 남아 있으면 내용 갱신
        select(selectedId?.takeIf { id -> located.any { it.id == id } })
    }

    private fun legendItem(text: String, color: Int): View {
        val density = ctx.resources.displayMetrics.density
        return TextView(ctx).apply {
            this.text = text
            setTextAppearance(R.style.TextAppearance_TripPlanner_LabelMedium)
            setTextColor(ContextCompat.getColor(ctx, R.color.on_surface))
            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(color)
                setSize((10 * density).toInt(), (10 * density).toInt())
            }
            setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
            compoundDrawablePadding = (4 * density).toInt()
            setPaddingRelative((4 * density).toInt(), 0, (6 * density).toInt(), 0)
        }
    }

    /** 점 선택 → 아래 카드. null 이면 닫기 */
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
        val label = labels[place.id].orEmpty()
        cardOrder.visibility = if (confirmed && label.isNotEmpty()) View.VISIBLE else View.GONE
        cardOrder.text = label
        cardOrder.backgroundTintList = ColorStateList.valueOf(color)
        cardName.text = place.name
        cardSub.text = listOfNotNull(
            if (!confirmed) ctx.getString(R.string.status_candidate) else day?.let { ctx.dayLabel(it) } ?: ctx.getString(R.string.day_none),
            place.openingHours.replace(" ~ ", "–"),
            ctx.getString(R.string.minutes, place.stayMinutes)
        ).joinToString(ctx.getString(R.string.list_separator))
        cardMap.text = listener.mapLabel(place)
        cardMap.setOnClickListener { listener.onOpenMap(place) }
        cardEdit.setOnClickListener { listener.onEdit(place) }
        cardConfirm.visibility = if (confirmed) View.GONE else View.VISIBLE
        cardConfirm.contentDescription = ctx.getString(R.string.confirm_place_a11y, place.name)
        cardConfirm.setOnClickListener { listener.onConfirmPick(place, it) }
        card.contentDescription = null
    }

}
