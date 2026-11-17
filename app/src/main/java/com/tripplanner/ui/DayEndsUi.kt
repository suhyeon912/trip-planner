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

    private fun anchorOf(r: DayAnchors.Resolved) = (r as? DayAnchors.Resolved.Lodge)?.lodging
        ?.takeIf { it.lat != 0.0 || it.lng != 0.0 }?.let { Anchor(it.name, it.lat, it.lng) }

    /** 경로 계산용: 숙소면 출발점 · 도착점, 장소면 처음 · 마지막에 들르는 장소 */
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

        fun of(trip: Trip?, day: Int, lodgings: List<Lodging>, dayPlaces: List<Place>): DayEndpoints {
            val ends = DayAnchors.parse(trip?.dayAnchors.orEmpty())[day]
            // 그날 숙소에 위치가 없으면 위치 있는 첫 숙소 (경로 계산이 되도록, 예전과 같음)
            fun hasLoc(l: Lodging) = l.lat != 0.0 || l.lng != 0.0
            val def = lodgingFor(trip, day, lodgings)?.takeIf { hasLoc(it) } ?: lodgings.firstOrNull { hasLoc(it) }
                ?: lodgingFor(trip, day, lodgings)
            return DayEndpoints(
                DayAnchors.resolve(ends?.start ?: Endpoint.Default, def, lodgings, dayPlaces),
                DayAnchors.resolve(ends?.end ?: Endpoint.Default, def, lodgings, dayPlaces)
            )
        }
    }
}

/**
 * 시작 · 끝 장소 고르기 메뉴 (누른 줄 아래):
 *  숙소 ● 이름 … / 이 날 장소 ● 이름 … / 정하지 않음
 */
fun showEndpointMenu(anchor: View, start: Boolean, current: DayAnchors.Resolved, lodgings: List<Lodging>,
                     dayPlaces: List<Place>, onPick: (Endpoint) -> Unit) {
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
    rows += SelectRow.Option(ctx.getString(if (start) R.string.endpoint_none_start else R.string.endpoint_none_end),
        selected = current is DayAnchors.Resolved.None) { onPick(Endpoint.None) }
    showSelect(anchor, rows, widthDp = 260)
}
