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
 *  · 운영시간 · 브레이크 타임은 '시작 시:분 ~ 끝 시:분' 네 줄 (+ '모름' / '없음')
 *  · 머무는 시간은 '시간 · 분' 두 줄
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

    /**
     * 시간 범위 고르기 (운영시간 · 브레이크 타임): 시작 시:분 ~ 끝 시:분.
     * current = "HH:MM ~ HH:MM" 형식 (읽을 수 없으면 defaultStart ~ defaultEnd 에서 시작).
     * noneLabel 이 있으면 '없음 / 시간 정하기' 가 보이고, '없음'으로 적용하면 null.
     * 끝이 시작보다 이르면 다음 날까지 (예: 18:00 ~ 02:00), 같으면 24시간.
     */
    fun range(anchor: View, title: String, current: String?, noneLabel: String?, defaultStart: Int, defaultEnd: Int,
              onApply: (String?) -> Unit) {
        val r = TimeUtil.parseRange(current)
        val s = r?.first ?: defaultStart
        val e = (r?.last ?: defaultEnd) % (24 * 60)
        val hours = (0..23).toList()
        val sMins = stepMinutes(s % 60); val eMins = stepMinutes(e % 60)
        lateinit var sh: WheelView; lateinit var sm: WheelView; lateinit var eh: WheelView; lateinit var em: WheelView
        show(anchor, title, allowFree = noneLabel != null, free = noneLabel != null && r == null,
            freeLabels = noneLabel?.let { it to anchor.context.getString(R.string.range_set_option) }, build = { box ->
            sh = wheel(box, R.string.wheel_start_hour, hours.map { "%02d".format(it) }, s / 60 % 24)
            colon(box)
            sm = wheel(box, R.string.wheel_start_minute, sMins.map { "%02d".format(it) }, sMins.indexOf(s % 60))
            sep(box, "~")
            eh = wheel(box, R.string.wheel_end_hour, hours.map { "%02d".format(it) }, e / 60)
            colon(box)
            em = wheel(box, R.string.wheel_end_minute, eMins.map { "%02d".format(it) }, eMins.indexOf(e % 60))
            listOf(sh, sm, eh, em)
        }) { none ->
            onApply(if (none) null else "%02d:%02d ~ %02d:%02d".format(hours[sh.selectedIndex], sMins[sm.selectedIndex],
                hours[eh.selectedIndex], eMins[em.selectedIndex]))
        }
    }

    /** 머무는 시간 고르기: 시간(0~12) · 분(5분 단위). 10분보다 짧으면 10분으로 */
    fun duration(anchor: View, title: String, minutes: Int, onApply: (Int) -> Unit) {
        val hours = (0..12).toList()
        val mins = stepMinutes(minutes % 60)
        lateinit var hw: WheelView; lateinit var mw: WheelView
        show(anchor, title, allowFree = false, free = false, build = { box ->
            hw = wheel(box, R.string.wheel_hours, hours.map { box.context.getString(R.string.duration_hours, it) }, (minutes / 60).coerceIn(0, 12))
            mw = wheel(box, R.string.wheel_minute, mins.map { box.context.getString(R.string.duration_minutes, it) }, mins.indexOf(minutes % 60))
            listOf(hw, mw)
        }) {
            onApply((hours[hw.selectedIndex] * 60 + mins[mw.selectedIndex]).coerceIn(10, 12 * 60))
        }
    }

    /** "1시간 30분" / "45분" */
    fun durationLabel(ctx: android.content.Context, minutes: Int): String {
        val h = minutes / 60; val m = minutes % 60
        return when {
            h == 0 -> ctx.getString(R.string.duration_minutes, m)
            m == 0 -> ctx.getString(R.string.duration_hours, h)
            else -> ctx.getString(R.string.duration_hm, h, m)
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

    private fun colon(box: LinearLayout) = sep(box, ":")

    private fun sep(box: LinearLayout, mark: String) {
        box.addView(TextView(box.context).apply {
            text = mark
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = ResourcesCompat.getFont(context, R.font.pretendard_bold)
            setTextColor(context.color(R.color.on_surface))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun show(anchor: View, title: String, allowFree: Boolean, free: Boolean,
                     freeLabels: Pair<String, String>? = null,
                     build: (LinearLayout) -> List<WheelView>, apply: (Boolean) -> Unit) {
        val ctx = anchor.context
        val d = ctx.resources.displayMetrics.density
        val content = LayoutInflater.from(ctx).inflate(R.layout.view_wheel_popup, null)
        // 제목 줄은 보이지 않게 (누른 칸 바로 아래라 무엇을 고르는지 이미 보임) — 화면 읽기에는 제목을 알려 줌
        content.findViewById<TextView>(R.id.tvWheelTitle).apply { text = title; visibility = View.GONE }
        content.contentDescription = title
        if (android.os.Build.VERSION.SDK_INT >= 28) content.accessibilityPaneTitle = title
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
            // 기본은 '시간 자유 / 시각 정하기', 범위는 '모름(없음) / 시간 정하기'
            freeLabels?.let { (a, b) ->
                content.findViewById<TextView>(R.id.btnFree).text = a
                content.findViewById<TextView>(R.id.btnSetTime).text = b
            }
            seg.check(if (free) R.id.btnFree else R.id.btnSetTime)
            seg.addOnButtonCheckedListener { _, id, checked -> if (checked) setFree(id == R.id.btnFree) }
        }
        setFree(isFree)

        // 키보드가 떠 있으면 먼저 내리고 (입력칸 포커스도 풀기), 화면 크기가 자리 잡은 뒤에 띄움.
        // 키보드가 열린 채로 띄우면 팝오버가 키보드를 내리며 화면이 줄었다 늘면서 흔들리고, 닫을 때 다시 키보드가 뜸.
        val imeShown = androidx.core.view.ViewCompat.getRootWindowInsets(anchor)
            ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        anchor.rootView.findFocus()?.let { f -> if (f is android.widget.EditText) f.clearFocus() }
        (ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(anchor.windowToken, 0)
        if (imeShown) anchor.postDelayed({ if (anchor.isAttachedToWindow) place(anchor, content, apply) { isFree } }, 250)
        else place(anchor, content, apply) { isFree }
    }

    /** 화면 좌우 16 안쪽 가득, 누른 것 바로 아래 4 */
    private fun place(anchor: View, content: View, apply: (Boolean) -> Unit, isFree: () -> Boolean) {
        val d = anchor.resources.displayMetrics.density
        val frame = Rect().also { anchor.rootView.getWindowVisibleDisplayFrame(it) }
        val margin = (16 * d).toInt()
        val width = minOf(frame.width() - margin * 2, (480 * d).toInt())
        val popup = PopupWindow(content, width, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(ColorDrawable(0))
        popup.elevation = 12 * d
        popup.animationStyle = R.style.Animation_TripPlanner_Popover
        popup.isOutsideTouchable = true
        content.findViewById<View>(R.id.btnWheelCancel).setOnClickListener { popup.dismiss() }
        content.findViewById<View>(R.id.btnWheelApply).setOnClickListener { apply(isFree()); popup.dismiss() }

        // 화면 좌표에 바로 놓기: 누른 칸에 붙여 두면(드롭다운) 화면이 다시 그려질 때마다 위치를 다시 계산하며 좌우로 흔들림.
        // 아래 자리가 모자라면 칸 위에.
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val h = content.measuredHeight
        val loc = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val gap = (4 * d).toInt()
        val below = loc[1] + anchor.height + gap
        val y = if (below + h <= frame.bottom - margin) below else maxOf(frame.top + margin, loc[1] - gap - h)
        val x = frame.left + (frame.width() - width) / 2
        popup.showAtLocation(anchor.rootView, android.view.Gravity.TOP or android.view.Gravity.START, x, y)
    }
}
