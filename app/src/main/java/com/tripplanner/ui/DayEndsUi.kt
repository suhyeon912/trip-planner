package com.tripplanner.ui

import com.tripplanner.data.Lodging
import com.tripplanner.data.Place
import com.tripplanner.data.Trip
import com.tripplanner.route.Ends
import com.tripplanner.route.TimeUtil

/**
 * 하루의 출발 · 도착 = 그날 순서의 첫 장소 · 마지막 장소 (사용자가 넣은 순서 그대로).
 *  · 숙소에서 출발해 숙소로 돌아오는 날이면 숙소를 장소로 맨 앞(1번) · 맨 뒤에 넣음
 *  · 경로 제안은 첫 · 마지막 장소를 그 자리에 두고 가운데 장소들의 순서만 바꿔 봄
 * (여행 상세 타임라인 · 지도 · 경로 제안이 함께 씀)
 */
data class DayEndpoints(val first: Place?, val last: Place?) {

    /** 경로 계산용: 첫 · 마지막 장소를 고정 (따로 출발점 · 도착점은 없음) */
    val ends: Ends get() = Ends(start = null, end = null, firstId = first?.id, lastId = last?.id)

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
