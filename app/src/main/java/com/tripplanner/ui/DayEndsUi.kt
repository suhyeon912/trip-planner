package com.tripplanner.ui

import com.tripplanner.data.Lodging
import com.tripplanner.data.Place
import com.tripplanner.data.Trip
import com.tripplanner.route.Anchor
import com.tripplanner.route.Ends
import com.tripplanner.route.TimeUtil

/**
 * 하루의 출발 · 도착.
 *  · 숙소: 체크인 · 체크아웃 날짜로 자동 (시각과 상관없이)
 *    - 그날 밤 묵는 숙소(체크인 ≤ 그날 < 체크아웃) = 그날 도착
 *    - 전날 밤 묵은 숙소(체크인 < 그날 ≤ 체크아웃) = 그날 출발
 *  · 숙소가 없는 쪽(여행 첫날 출발 · 마지막 날 도착 등)은 그날 순서의 첫 · 마지막 장소 (집 · 공항 등)
 *  · 예전처럼 숙소를 장소로 맨 앞 · 맨 뒤에 넣어 둔 날은 그 장소를 그대로 씀 (숙소가 두 번 나오지 않게)
 *  · 숙소 위치가 없으면 경로에는 못 넣으니 그쪽은 첫 · 마지막 장소로
 *  · 경로 제안은 출발 · 도착을 그 자리에 두고 가운데 장소들의 순서만 바꿔 봄
 * (여행 상세 타임라인 · 지도 · 경로 제안이 함께 씀)
 */
data class DayEndpoints(
    val first: Place?, val last: Place?,
    /** 그날 출발하는 숙소 · 도착하는 숙소 (자동, 없으면 null) */
    val startLodging: Lodging? = null, val endLodging: Lodging? = null
) {

    /** 경로 계산용: 숙소(위치 있음)는 출발점 · 도착점, 없는 쪽은 첫 · 마지막 장소를 고정 */
    val ends: Ends get() {
        val s = startLodging?.anchor()
        val e = endLodging?.anchor()
        return Ends(start = s, end = e, firstId = if (s == null) first?.id else null, lastId = if (e == null) last?.id else null)
    }

    companion object {
        /** 그날 묵는 숙소 (체크인 ≤ 그날 < 체크아웃, 없으면 첫 숙소) */
        fun lodgingFor(trip: Trip?, day: Int, lodgings: List<Lodging>): Lodging? {
            val date = trip?.startDate?.let { TimeUtil.dateOfDay(it, day) }?.toString()
            return lodgings.firstOrNull { date != null && it.checkIn <= date && date < it.checkOut } ?: lodgings.firstOrNull()
        }

        /** @param dayPlaces 그날 장소 (위치 없는 장소는 경로에 못 넣으니 빼고 셈) */
        fun of(dayPlaces: List<Place>): DayEndpoints {
            val located = dayPlaces.filter { it.lat != 0.0 || it.lng != 0.0 }
                .sortedWith(compareBy<Place>({ it.orderInDay == null }, { it.orderInDay }, { it.id }))
            return DayEndpoints(located.firstOrNull(), located.lastOrNull()?.takeIf { located.size >= 2 })
        }

        /** 숙소까지 넣은 하루의 출발 · 도착 (체크인 · 체크아웃 날짜 기준) */
        fun of(dayPlaces: List<Place>, trip: Trip?, day: Int, lodgings: List<Lodging>): DayEndpoints {
            val base = of(dayPlaces)
            val date = trip?.startDate?.let { TimeUtil.dateOfDay(it, day) }?.toString() ?: return base
            val dated = lodgings.filter { it.checkIn.isNotBlank() && it.checkOut.isNotBlank() }
            var start = dated.firstOrNull { it.checkIn < date && date <= it.checkOut }
            var end = dated.firstOrNull { it.checkIn <= date && date < it.checkOut }
            // 예전 방식: 그 숙소를 장소로 맨 앞 · 맨 뒤에 넣어 둔 날은 장소 쪽을 씀
            if (start != null && base.first != null && isLodging(base.first, listOf(start))) start = null
            if (end != null && base.last != null && isLodging(base.last, listOf(end))) end = null
            return base.copy(startLodging = start, endLodging = end)
        }

        /** 숙소 → 경로의 출발 · 도착점 (위치가 없으면 null) */
        fun Lodging.anchor(): Anchor? = if (lat != 0.0 || lng != 0.0) Anchor(name, lat, lng) else null

        /** 숙소로 넣은 장소인지: 등록한 숙소와 이름이 같거나 100m 안 */
        fun isLodging(p: Place, lodgings: List<Lodging>): Boolean = lodgings.any { l ->
            l.name.isNotBlank() && l.name.trim() == p.name.trim() ||
                (l.lat != 0.0 || l.lng != 0.0) && (p.lat != 0.0 || p.lng != 0.0) &&
                com.tripplanner.route.RoutePlanner.legKm(l.lat, l.lng, p.lat, p.lng) < 0.1
        }

        /**
         * 머무는 시간을 0으로 볼 장소: 첫 장소 · 마지막 장소가 숙소면
         * (숙소에서 출발 시각에 바로 떠나고, 숙소에 도착하면 하루 끝)
         */
        fun zeroStayIds(dayPlaces: List<Place>, lodgings: List<Lodging>): Set<Long> {
            val e = of(dayPlaces)
            return listOfNotNull(e.first, e.last).filter { isLodging(it, lodgings) }.map { it.id }.toSet()
        }
    }
}
