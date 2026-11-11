package com.tripplanner.route

import java.time.DayOfWeek
import java.time.LocalDate

/** 시간은 모두 '자정부터 몇 분'(Int)으로 계산합니다. 예) 15:30 → 930 */
object TimeUtil {

    private val rangeRegex = Regex("""(\d{1,2}):(\d{2})\s*[~\-]\s*(\d{1,2}):(\d{2})""")

    /** "11:00 ~ 21:00" → 660..1260. 못 읽으면(상시, 정보 없음 등) null = 언제나 열려 있다고 봄 */
    fun parseRange(text: String?): IntRange? {
        if (text.isNullOrBlank()) return null
        val m = rangeRegex.find(text) ?: return null
        val (h1, m1, h2, m2) = m.destructured
        val start = h1.toInt() * 60 + m1.toInt()
        var end = h2.toInt() * 60 + m2.toInt()
        if (end <= start) end += 24 * 60          // 16:00 ~ 02:00 처럼 자정을 넘기는 경우
        return start..end
    }

    fun format(minutes: Int): String {
        val m = ((minutes % 1440) + 1440) % 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }

    private val koreanDays = mapOf(
        DayOfWeek.MONDAY to "월요일", DayOfWeek.TUESDAY to "화요일", DayOfWeek.WEDNESDAY to "수요일",
        DayOfWeek.THURSDAY to "목요일", DayOfWeek.FRIDAY to "금요일", DayOfWeek.SATURDAY to "토요일",
        DayOfWeek.SUNDAY to "일요일"
    )

    fun koreanDayName(date: LocalDate): String = koreanDays.getValue(date.dayOfWeek)

    /** 휴무일 글자에 그날 요일이 들어 있으면 휴무로 봄. "일부 가게 …" 처럼 일부만 쉬면 휴무 아님 */
    fun isClosedOn(closedDays: String?, date: LocalDate?): Boolean {
        if (closedDays.isNullOrBlank() || date == null) return false
        if (closedDays.contains("일부")) return false
        return closedDays.contains(koreanDayName(date))
    }

    /** 여행 시작일 + (day-1) 일 → 그날 날짜 */
    fun dateOfDay(startDate: String, day: Int): LocalDate? =
        runCatching { LocalDate.parse(startDate).plusDays((day - 1).toLong()) }.getOrNull()

    /** 여행 기간이 며칠인지 (날짜를 못 읽으면 7일로 가정) */
    fun dayCount(startDate: String, endDate: String): Int = runCatching {
        val s = LocalDate.parse(startDate)
        val e = LocalDate.parse(endDate)
        (java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1).toInt().coerceIn(1, 30)
    }.getOrDefault(7)
}
