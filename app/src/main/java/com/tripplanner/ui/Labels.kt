package com.tripplanner.ui

import android.content.Context
import com.tripplanner.R
import com.tripplanner.route.TimeUtil
import java.time.LocalDate

/*
 * 여러 화면에서 같이 쓰는 표시 문구 도우미.
 * 문구 자체는 res/values/strings.xml 에 있고, 여기서는 조립만 합니다.
 */

/** "3일차" */
fun Context.dayLabel(day: Int): String = getString(R.string.day_n, day)

/** "3일차 · 2/12(금)" — 날짜가 없으면 "3일차" */
fun Context.dayWithDate(day: Int, date: LocalDate?): String =
    if (date == null) dayLabel(day)
    else getString(R.string.day_n_with_date, day, date.monthValue, date.dayOfMonth, TimeUtil.koreanDayName(date).first().toString())

/** "1시간 33분" / "2시간" / "43분" */
fun Context.minutesLabel(m: Int): String = when {
    m >= 60 && m % 60 == 0 -> getString(R.string.hours, m / 60)
    m >= 60 -> getString(R.string.hours_minutes, m / 60, m % 60)
    else -> getString(R.string.minutes, m)
}

/** 일차 칩 (상세·경로 화면 공용). date 가 없으면 장소 수만 표시 */
fun Context.dayChip(day: Int, date: LocalDate?, count: Int, selected: Boolean): DayChip {
    val sub = if (date != null)
        getString(R.string.chip_sub_date, date.monthValue, date.dayOfMonth, TimeUtil.koreanDayName(date).first().toString(), count)
    else getString(R.string.chip_sub, count)
    val a11y = if (date != null)
        getString(R.string.chip_a11y_date, day, date.monthValue, date.dayOfMonth, TimeUtil.koreanDayName(date), count)
    else getString(R.string.chip_a11y, dayLabel(day), count)
    return DayChip(day, dayLabel(day), sub, a11y, selected)
}
