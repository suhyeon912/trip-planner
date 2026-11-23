package com.tripplanner.route

import java.time.DayOfWeek
import java.time.LocalDate

/** 시간은 모두 '자정부터 몇 분'(Int)으로 계산합니다. 예) 15:30 → 930 */
object TimeUtil {

    private val rangeRegex = Regex("""(\d{1,2}):(\d{2})\s*[~\-]\s*(\d{1,2}):(\d{2})""")

    /** "11:00 ~ 21:00" → 660..1260. 못 읽으면(상시, 정보 없음 등) null = 언제나 열려 있다고 봄 */
    fun parseRange(text: String?): IntRange? {
        if (text.isNullOrBlank()) return null
        val m = rangeRegex.find(text) ?: rangeRegex.find(loose(text) ?: return null) ?: return null
        val (h1, m1, h2, m2) = m.destructured
        val start = h1.toInt() * 60 + m1.toInt()
        var end = h2.toInt() * 60 + m2.toInt()
        if (end <= start) end += 24 * 60          // 16:00 ~ 02:00 처럼 자정을 넘기는 경우
        return start..end
    }

    /** 화면에 보일 때 시간 범위 표기를 하나로: "10:00~21:00" · "10:00–21:00" → "10:00 ~ 21:00" */
    fun displayRange(text: String): String =
        loose(text) ?: text.replace(Regex("""(\d{1,2}:\d{2})\s*[~–\-]\s*(\d{1,2}:\d{2})"""), "$1 ~ $2")

    /** 콜론 · 물결 없이 적힌 범위 ("11 13", "11-13", "11시~13시", "1100 1300") → "11:00 ~ 13:00", 아니면 null */
    private val looseRegex = Regex("""^\s*(\d{1,2})(?::?(\d{2}))?\s*시?\s*(?:[~–\-]|\s)\s*(\d{1,2})(?::?(\d{2}))?\s*시?\s*$""")
    fun loose(text: String): String? {
        if (rangeRegex.containsMatchIn(text)) return null
        val m = looseRegex.find(text) ?: return null
        val (h1, m1, h2, m2) = m.destructured
        val a = h1.toInt(); val b = h2.toInt(); val am = m1.ifEmpty { "0" }.toInt(); val bm = m2.ifEmpty { "0" }.toInt()
        if (a > 24 || b > 24 || am > 59 || bm > 59) return null
        return "%02d:%02d ~ %02d:%02d".format(a, am, b, bm)
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

    /** 여행 기간이 며칠인지 (날짜를 못 읽으면 7일로 가정, 길어도 1년까지) */
    fun dayCount(startDate: String, endDate: String): Int = runCatching {
        val s = LocalDate.parse(startDate)
        val e = LocalDate.parse(endDate)
        (java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1).toInt().coerceIn(1, 366)
    }.getOrDefault(7)
}
