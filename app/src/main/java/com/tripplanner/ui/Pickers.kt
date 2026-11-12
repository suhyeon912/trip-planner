package com.tripplanner.ui

import android.view.View
import androidx.fragment.app.FragmentActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 날짜·시각 고르기 (시안 컴포넌트 CompTime).
 *  · 기간·하루: 전체 화면 달력 (CalendarDialog)
 *  · 날짜+시각: 누른 칸 아래 휠 팝오버 (WheelPopup)
 * 저장 형식은 기존과 같음: 날짜 "yyyy-MM-dd", 날짜+시각 "yyyy-MM-dd HH:mm".
 */
object Pickers {
    private val DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private fun parseDate(s: String) = runCatching { LocalDate.parse(s.trim().take(10)) }.getOrNull()

    /** 기간 고르기 (여행 날짜·숙소 체크인/아웃) */
    fun dateRange(activity: FragmentActivity, title: String, start: String, end: String, onPick: (String, String) -> Unit) =
        CalendarDialog.range(activity, title, parseDate(start), parseDate(end)) { s, e -> onPick(s.toString(), e.toString()) }

    /** 하루 고르기 */
    fun date(activity: FragmentActivity, title: String, current: String, onPick: (String) -> Unit) =
        CalendarDialog.single(activity, title, parseDate(current)) { onPick(it.toString()) }

    /** 날짜 · 시각 고르기 (교통편 출발·도착) — anchor 바로 아래에 휠 */
    fun dateTime(anchor: View, title: String, current: String, onPick: (String) -> Unit) {
        val cur = runCatching { LocalDateTime.parse(current.trim(), DT) }.getOrNull()
            ?: (parseDate(current) ?: LocalDate.now()).atTime(9, 0)
        WheelPopup.dateTime(anchor, title, cur) { onPick(it.format(DT)) }
    }
}
