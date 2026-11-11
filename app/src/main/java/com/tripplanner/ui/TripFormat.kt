package com.tripplanner.ui

import com.tripplanner.data.Trip
import com.tripplanner.route.TimeUtil
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 여행 날짜를 화면용 문자열로 (여행 목록·상세에서 함께 사용) */
object TripFormat {

    enum class Phase { UPCOMING, TODAY, ONGOING, PAST }

    data class Dday(val text: String, val phase: Phase)

    private fun parse(s: String) = runCatching { LocalDate.parse(s) }.getOrNull()

    /** "12/20(일) – 12/23(수) · 4일" (날짜를 못 읽으면 원문 그대로) */
    fun dateRange(trip: Trip): String {
        val s = parse(trip.startDate) ?: return "${trip.startDate} ~ ${trip.endDate}"
        val e = parse(trip.endDate) ?: return "${trip.startDate} ~ ${trip.endDate}"
        fun f(d: LocalDate) = "${d.monthValue}/${d.dayOfMonth}(${TimeUtil.koreanDayName(d).first()})"
        val days = TimeUtil.dayCount(trip.startDate, trip.endDate)
        return "${f(s)} – ${f(e)} · ${days}일"
    }

    /** D-n / D-day / 여행 중 / 지난 여행 (날짜를 못 읽으면 null) */
    fun dday(trip: Trip, today: LocalDate = LocalDate.now()): Dday? {
        val s = parse(trip.startDate) ?: return null
        val e = parse(trip.endDate) ?: return null
        return when {
            today.isBefore(s) -> Dday("D-${ChronoUnit.DAYS.between(today, s)}", Phase.UPCOMING)
            today == s -> Dday("D-day", Phase.TODAY)
            today.isAfter(e) -> Dday("지난 여행", Phase.PAST)
            else -> Dday("여행 중", Phase.ONGOING)
        }
    }
}
