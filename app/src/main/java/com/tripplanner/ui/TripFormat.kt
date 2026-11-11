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

    /**
     * 시안 형식: "2026.11.20 (금) – 11.23 (월)". 끝 날짜는 해가 같으면 연도를 뺌.
     * 날짜를 못 읽으면 원문 그대로.
     */
    fun fullRange(trip: Trip): String {
        val s = parse(trip.startDate) ?: return "${trip.startDate} ~ ${trip.endDate}"
        val e = parse(trip.endDate) ?: return "${trip.startDate} ~ ${trip.endDate}"
        fun day(d: LocalDate) = TimeUtil.koreanDayName(d).first()
        val end = if (e.year == s.year) "${e.monthValue}.${e.dayOfMonth}" else "${e.year}.${e.monthValue}.${e.dayOfMonth}"
        return "${s.year}.${s.monthValue}.${s.dayOfMonth} (${day(s)}) – $end (${day(e)})"
    }

    /** "2026.11.20 (금) – 11.23 (월) · 4일" (여행 카드) */
    fun fullRangeWithDays(trip: Trip): String {
        val ok = nightsDays(trip.startDate, trip.endDate) != null
        return fullRange(trip) + if (ok) " · ${TimeUtil.dayCount(trip.startDate, trip.endDate)}일" else ""
    }

    /** "3박 4일" (하루짜리는 "1일", 날짜를 못 읽으면 null) */
    fun nightsDays(start: String, end: String): String? {
        val s = parse(start) ?: return null
        val e = parse(end) ?: return null
        if (e.isBefore(s)) return null
        val nights = ChronoUnit.DAYS.between(s, e).toInt()
        return if (nights == 0) "1일" else "${nights}박 ${nights + 1}일"
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
