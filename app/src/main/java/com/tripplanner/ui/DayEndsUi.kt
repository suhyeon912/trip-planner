package com.tripplanner.ui

import android.view.View
import com.tripplanner.R
import com.tripplanner.data.DayAnchors
import com.tripplanner.data.Endpoint
import com.tripplanner.data.Lodging
import com.tripplanner.data.Place
import com.tripplanner.data.Trip
import com.tripplanner.route.Anchor
import com.tripplanner.route.Ends
import com.tripplanner.route.TimeUtil

/**
 * 하루의 시작 · 끝 (여행 상세 타임라인 · 지도 · 경로 제안이 함께 씀).
 * 고른 값이 없으면 그날 묵는 숙소 (예전과 같음).
 */
data class DayEndpoints(val start: DayAnchors.Resolved, val end: DayAnchors.Resolved) {

    private fun anchorOf(r: DayAnchors.Resolved): Anchor? = when (r) {
        is DayAnchors.Resolved.Lodge -> r.lodging.takeIf { it.lat != 0.0 || it.lng != 0.0 }?.let { Anchor(it.name, it.lat, it.lng) }
        is DayAnchors.Resolved.Spot -> Anchor(r.place.name, r.place.lat, r.place.lng, lodging = false)
        else -> null
    }

    /** 그날 일정 밖 장소를 시작 · 끝으로 정했으면 그 장소들 (지도에 점으로) */
    val spots: List<Place> get() = listOfNotNull((start as? DayAnchors.Resolved.Spot)?.place,
        (end as? DayAnchors.Resolved.Spot)?.place).distinctBy { it.id }

    /** 경로 계산용: 숙소 · 일정 밖 장소면 출발점 · 도착점, 그날 장소면 처음 · 마지막에 들르는 장소 */
    val ends: Ends get() = Ends(
        start = anchorOf(start), end = anchorOf(end),
        firstId = (start as? DayAnchors.Resolved.AtPlace)?.place?.id,
        lastId = (end as? DayAnchors.Resolved.AtPlace)?.place?.id
    )

    /** 장소를 시작 · 끝으로 정했으면 그 장소가 처음 · 마지막에 오게 (나머지 순서는 그대로) */
    fun pin(list: List<Place>): List<Place> {
        val first = (start as? DayAnchors.Resolved.AtPlace)?.place?.id
        val last = (end as? DayAnchors.Resolved.AtPlace)?.place?.id?.takeIf { it != first }
        val f = list.firstOrNull { it.id == first }
        val l = list.firstOrNull { it.id == last }
        return listOfNotNull(f) + list.filter { it.id != first && it.id != last } + listOfNotNull(l)
    }

    companion object {
        /** 그날 묵는 숙소 (체크인 ≤ 그날 < 체크아웃, 없으면 첫 숙소) */
        fun lodgingFor(trip: Trip?, day: Int, lodgings: List<Lodging>): Lodging? {
            val date = trip?.startDate?.let { TimeUtil.dateOfDay(it, day) }?.toString()
            return lodgings.firstOrNull { date != null && it.checkIn <= date && date < it.checkOut } ?: lodgings.firstOrNull()
        }

        /** @param allPlaces 여행의 모든 장소 (다른 날 · 후보를 시작 · 끝으로 정했을 때) */
        fun of(trip: Trip?, day: Int, lodgings: List<Lodging>, dayPlaces: List<Place>, allPlaces: List<Place> = dayPlaces): DayEndpoints {
            val ends = DayAnchors.parse(trip?.dayAnchors.orEmpty())[day]
            // 그날 숙소에 위치가 없으면 위치 있는 첫 숙소 (경로 계산이 되도록, 예전과 같음)
            fun hasLoc(l: Lodging) = l.lat != 0.0 || l.lng != 0.0
            val def = lodgingFor(trip, day, lodgings)?.takeIf { hasLoc(it) } ?: lodgings.firstOrNull { hasLoc(it) }
                ?: lodgingFor(trip, day, lodgings)
            return DayEndpoints(
                DayAnchors.resolve(ends?.start ?: Endpoint.Default, def, lodgings, dayPlaces, allPlaces),
                DayAnchors.resolve(ends?.end ?: Endpoint.Default, def, lodgings, dayPlaces, allPlaces)
            )
        }
    }
}

/**
 * 시작 · 끝 장소 고르기 메뉴 (누른 줄 아래):
 *  숙소 … / 이 날 장소 … / 다른 장소 (다른 날 · 후보, 위치 있는 것) … / 정하지 않음
 * 고를 수 있는 건 여행에 넣은 장소뿐 (새 장소는 장소 추가로 먼저 넣기)
 */
fun showEndpointMenu(anchor: View, start: Boolean, current: DayAnchors.Resolved, lodgings: List<Lodging>,
                     dayPlaces: List<Place>, onPick: (Endpoint) -> Unit) =
    showEndpointMenu(anchor, start, current, lodgings, dayPlaces, emptyList(), onPick)

fun showEndpointMenu(anchor: View, start: Boolean, current: DayAnchors.Resolved, lodgings: List<Lodging>,
                     dayPlaces: List<Place>, otherPlaces: List<Place>, onPick: (Endpoint) -> Unit) {
    val ctx = anchor.context
    val rows = mutableListOf<SelectRow>()
    if (lodgings.isNotEmpty()) {
        rows += SelectRow.Header(ctx.getString(R.string.endpoint_lodgings))
        lodgings.forEach { l ->
            rows += SelectRow.Option(l.name, selected = (current as? DayAnchors.Resolved.Lodge)?.lodging?.id == l.id) {
                onPick(Endpoint.AtLodging(l.name))
            }
        }
    }
    if (dayPlaces.isNotEmpty()) {
        rows += SelectRow.Header(ctx.getString(R.string.endpoint_places))
        dayPlaces.forEach { p ->
            rows += SelectRow.Option(p.name, selected = (current as? DayAnchors.Resolved.AtPlace)?.place?.id == p.id) {
                onPick(Endpoint.AtPlace(p.name))
            }
        }
    }
    // 다른 날 · 후보: 이름이 겹치면 하나만 (이름으로 저장하니까), 그날 장소와 같은 이름도 빼기
    val others = otherPlaces.filter { (it.lat != 0.0 || it.lng != 0.0) && dayPlaces.none { d -> d.name == it.name } }
        .distinctBy { it.name }
    if (others.isNotEmpty()) {
        rows += SelectRow.Header(ctx.getString(R.string.endpoint_other_places))
        others.forEach { p ->
            val candidate = p.status == com.tripplanner.data.PlaceStatus.CANDIDATE
            val where = when {
                candidate -> ctx.getString(R.string.status_candidate)
                p.day != null -> ctx.dayLabel(p.day)
                else -> ctx.getString(R.string.day_none)
            }
            rows += SelectRow.Option(ctx.getString(R.string.endpoint_other_option, p.name, where),
                selected = (current as? DayAnchors.Resolved.Spot)?.place?.id == p.id,
                dotColor = if (candidate) ctx.color(R.color.primary) else p.day?.let { PinIcons.dayColor(it) } ?: ctx.color(R.color.outline),
                hollowDot = candidate || p.day == null) {
                onPick(Endpoint.AtPlace(p.name))
            }
        }
    }
    rows += SelectRow.Option(ctx.getString(if (start) R.string.endpoint_none_start else R.string.endpoint_none_end),
        selected = current is DayAnchors.Resolved.None) { onPick(Endpoint.None) }
    showSelect(anchor, rows, widthDp = 260)
}
