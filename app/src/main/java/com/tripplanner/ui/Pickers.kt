package com.tripplanner.ui

import androidx.core.util.Pair as APair
import androidx.fragment.app.FragmentActivity
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 달력·시계 선택 창 (Material DatePicker / TimePicker).
 * 저장 형식은 기존과 같음: 날짜 "yyyy-MM-dd", 날짜+시각 "yyyy-MM-dd HH:mm".
 * 달력은 UTC 자정 기준 밀리초를 쓰므로 여기서만 변환합니다.
 */
object Pickers {
    private val DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private fun parseDate(s: String) = runCatching { LocalDate.parse(s.trim().take(10)) }.getOrNull()
    private fun toMillis(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun fromMillis(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

    /** 기간 고르기 (여행 날짜·숙소 체크인/아웃) */
    fun dateRange(activity: FragmentActivity, title: String, start: String, end: String, onPick: (String, String) -> Unit) {
        val s = parseDate(start); val e = parseDate(end)
        val b = MaterialDatePicker.Builder.dateRangePicker().setTitleText(title)
        if (s != null && e != null && !e.isBefore(s)) b.setSelection(APair(toMillis(s), toMillis(e)))
        val picker = b.build()
        picker.addOnPositiveButtonClickListener { sel ->
            val a = sel.first ?: return@addOnPositiveButtonClickListener
            val z = sel.second ?: a
            onPick(fromMillis(a).toString(), fromMillis(z).toString())
        }
        picker.show(activity.supportFragmentManager, "date_range")
    }

    /** 하루 고르기 */
    fun date(activity: FragmentActivity, title: String, current: String, onPick: (String) -> Unit) {
        val d = parseDate(current) ?: LocalDate.now()
        val picker = MaterialDatePicker.Builder.datePicker().setTitleText(title).setSelection(toMillis(d)).build()
        picker.addOnPositiveButtonClickListener { onPick(fromMillis(it).toString()) }
        picker.show(activity.supportFragmentManager, "date")
    }

    /** 날짜 → 시각 순서로 고르기 (교통편 출발·도착) */
    fun dateTime(activity: FragmentActivity, title: String, current: String, onPick: (String) -> Unit) {
        val cur = runCatching { LocalDateTime.parse(current.trim(), DT) }.getOrNull()
            ?: (parseDate(current) ?: LocalDate.now()).atTime(9, 0)
        val dp = MaterialDatePicker.Builder.datePicker().setTitleText(title).setSelection(toMillis(cur.toLocalDate())).build()
        dp.addOnPositiveButtonClickListener { ms ->
            val day = fromMillis(ms)
            val tp = MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(cur.hour).setMinute(cur.minute).setTitleText(title).build()
            tp.addOnPositiveButtonClickListener { onPick(day.atTime(tp.hour, tp.minute).format(DT)) }
            tp.show(activity.supportFragmentManager, "time")
        }
        dp.show(activity.supportFragmentManager, "date_time")
    }
}
