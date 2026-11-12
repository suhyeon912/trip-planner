package com.tripplanner.ui

import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.tripplanner.R
import com.tripplanner.route.TimeUtil
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 시안 TimePicker: 누른 칩·칸 바로 아래에 뜨는 휠 팝오버 (MaterialTimePicker 대신).
 *  · 시·분 휠, 5분 단위, 24시간, 가운데 줄이 값
 *  · '적용'을 누르기 전까지 값이 바뀌지 않음, 바깥을 누르면 닫힘
 *  · 도착 시각은 '시간 자유 / 시각 정하기' — 시간 자유면 휠이 흐려지고 못 움직임
 *  · 교통편은 날짜 · 시 · 분 세 줄
 */
object WheelPopup {

    private const val STEP = 5

    /**
     * 시각 고르기. minutes = 하루 안의 분(0~1439), null 이면 '시간 자유'.
     * allowFree = true 면 '시간 자유 / 시각 정하기' 가 보이고, 결과가 null 일 수 있음.
     */
    fun time(anchor: View, title: String, minutes: Int?, allowFree: Boolean = false,
             defaultMinutes: Int = 10 * 60, onApply: (Int?) -> Unit) {
        val base = minutes ?: defaultMinutes
        val hours = (0..23).toList()
        val mins = stepMinutes(base % 60)
        lateinit var hourWheel: WheelView
        lateinit var minWheel: WheelView
        show(anchor, title, allowFree, free = allowFree && minutes == null, build = { box ->
            hourWheel = wheel(box, R.string.wheel_hour, hours.map { "%02d".format(it) }, hours.indexOf(base / 60 % 24))
            colon(box)
            minWheel = wheel(box, R.string.wheel_minute, mins.map { "%02d".format(it) }, mins.indexOf(base % 60))
            listOf(hourWheel, minWheel)
        }) { free ->
            onApply(if (free) null else hours[hourWheel.selectedIndex] * 60 + mins[minWheel.selectedIndex])
        }
    }

    /** 날짜 · 시각 고르기 (교통편 출발·도착). 날짜는 지금 값 앞뒤 60일 */
    fun dateTime(anchor: View, title: String, current: LocalDateTime, onApply: (LocalDateTime) -> Unit) {
        val days = (-60L..60L).map { current.toLocalDate().plusDays(it) }
        val hours = (0..23).toList()
        val mins = stepMinutes(current.minute)
        lateinit var dayWheel: WheelView
        lateinit var hourWheel: WheelView
        lateinit var minWheel: WheelView
        show(anchor, title, allowFree = false, free = false, build = { box ->
            dayWheel = wheel(box, R.string.wheel_date, days.map { dayLabel(it) }, 60, weight = 1.7f)
            hourWheel = wheel(box, R.string.wheel_hour, hours.map { "%02d".format(it) }, current.hour)
            colon(box)
            minWheel = wheel(box, R.string.wheel_minute, mins.map { "%02d".format(it) }, mins.indexOf(current.minute))
            listOf(dayWheel, hourWheel, minWheel)
        }) {
            onApply(days[dayWheel.selectedIndex].atTime(hours[hourWheel.selectedIndex], mins[minWheel.selectedIndex]))
        }
    }

    /** 5분 단위 (지금 값이 5분 단위가 아니면 그 값도 넣어 그대로 고를 수 있게) */
    private fun stepMinutes(current: Int) = ((0 until 60 step STEP).toList() + current).distinct().sorted()

    private fun dayLabel(d: LocalDate) = "${d.monthValue}.${d.dayOfMonth} (${TimeUtil.koreanDayName(d).first()})"

    private fun wheel(box: LinearLayout, label: Int, items: List<String>, select: Int, weight: Float = 1f): WheelView {
        val w = WheelView(box.context)
        w.label = box.context.getString(label)
        box.addView(w, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight))
        w.setItems(items, select.coerceAtLeast(0))
        return w
    }

    private fun colon(box: LinearLayout) {
        box.addView(TextView(box.context).apply {
            text = ":"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = ResourcesCompat.getFont(context, R.font.pretendard_bold)
            setTextColor(context.color(R.color.on_surface))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun show(anchor: View, title: String, allowFree: Boolean, free: Boolean,
                     build: (LinearLayout) -> List<WheelView>, apply: (Boolean) -> Unit) {
        val ctx = anchor.context
        val d = ctx.resources.displayMetrics.density
        val content = LayoutInflater.from(ctx).inflate(R.layout.view_wheel_popup, null)
        content.findViewById<TextView>(R.id.tvWheelTitle).text = title
        val wheels = build(content.findViewById(R.id.wheels))
        val band = content.findViewById<View>(R.id.wheelBand)

        // 도착: 시간 자유 / 시각 정하기
        val seg = content.findViewById<SegmentedGroup>(R.id.segFree)
        var isFree = free
        fun setFree(f: Boolean) {
            isFree = f
            wheels.forEach { it.isEnabled = !f }
            band.alpha = if (f) 0.35f else 1f
        }
        if (allowFree) {
            seg.visibility = View.VISIBLE
            seg.check(if (free) R.id.btnFree else R.id.btnSetTime)
            seg.addOnButtonCheckedListener { _, id, checked -> if (checked) setFree(id == R.id.btnFree) }
        }
        setFree(isFree)

        // 화면 좌우 16 안쪽 가득, 누른 것 바로 아래 4 (아래가 모자라면 위)
        val frame = Rect().also { anchor.rootView.getWindowVisibleDisplayFrame(it) }
        val margin = (16 * d).toInt()
        val width = minOf(frame.width() - margin * 2, (480 * d).toInt())
        val popup = PopupWindow(content, width, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(ColorDrawable(0))
        popup.elevation = 12 * d
        popup.animationStyle = R.style.Animation_TripPlanner_Popover
        popup.isOutsideTouchable = true
        content.findViewById<View>(R.id.btnWheelCancel).setOnClickListener { popup.dismiss() }
        content.findViewById<View>(R.id.btnWheelApply).setOnClickListener { apply(isFree); popup.dismiss() }

        val loc = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val xOff = (frame.left + (frame.width() - width) / 2) - loc[0]
        popup.showAsDropDown(anchor, xOff, (4 * d).toInt())
    }
}
