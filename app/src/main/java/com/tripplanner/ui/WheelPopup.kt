package com.tripplanner.ui

import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
 *  · 값을 비울 수 있는 칸은 왼쪽 아래 글자 버튼 ('선택 안 함', 경로 도착은 '시간 자유') — 누르면 바로 비우고 닫힘
 *  · 교통편은 날짜 · 시 · 분 세 줄, 머무는 시간은 '시간 · 분' 두 줄
 */
object WheelPopup {

    private const val STEP = 5

    /**
     * 시각 고르기. minutes = 하루 안의 분(0~1439), null 이면 아직 없음 (defaultMinutes 에서 시작).
     * clearLabel 이 있으면 왼쪽 아래에 그 글자 버튼 → 누르면 onApply(null).
     */
    fun time(anchor: View, title: String, minutes: Int?, clearLabel: String? = null,
             defaultMinutes: Int = 10 * 60, onApply: (Int?) -> Unit) {
        val base = minutes ?: defaultMinutes
        val hours = (0..23).toList()
        val mins = stepMinutes(base % 60)
        lateinit var hourWheel: WheelView
        lateinit var minWheel: WheelView
        show(anchor, title, clearLabel, onClear = { onApply(null) }, build = { box ->
            hourWheel = wheel(box, R.string.wheel_hour, hours.map { "%02d".format(it) }, hours.indexOf(base / 60 % 24))
            colon(box)
            minWheel = wheel(box, R.string.wheel_minute, mins.map { "%02d".format(it) }, mins.indexOf(base % 60))
            listOf(hourWheel, minWheel)
        }) {
            onApply(hours[hourWheel.selectedIndex] * 60 + mins[minWheel.selectedIndex])
        }
    }

    /** 머무는 시간 고르기: 시간(0~12) · 분(5분 단위). 10분보다 짧으면 10분으로 */
    fun duration(anchor: View, title: String, minutes: Int, onApply: (Int) -> Unit) {
        val hours = (0..12).toList()
        val mins = stepMinutes(minutes % 60)
        lateinit var hw: WheelView; lateinit var mw: WheelView
        show(anchor, title, null, null, build = { box ->
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
        show(anchor, title, null, null, build = { box ->
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

    private fun show(anchor: View, title: String, clearLabel: String?, onClear: (() -> Unit)?,
                     build: (LinearLayout) -> List<WheelView>, apply: () -> Unit) {
        val ctx = anchor.context
        val d = ctx.resources.displayMetrics.density
        val content = LayoutInflater.from(ctx).inflate(R.layout.view_wheel_popup, null)
        // 제목 줄은 보이지 않게 (누른 칸 바로 아래라 무엇을 고르는지 이미 보임) — 화면 읽기에는 제목을 알려 줌
        content.findViewById<TextView>(R.id.tvWheelTitle).apply { text = title; visibility = View.GONE }
        content.contentDescription = title
        if (android.os.Build.VERSION.SDK_INT >= 28) content.accessibilityPaneTitle = title
        build(content.findViewById(R.id.wheels))
        // 왼쪽 아래 '선택 안 함' (값을 비울 수 있는 칸만)
        content.findViewById<TextView>(R.id.btnWheelClear).apply {
            visibility = if (clearLabel != null) View.VISIBLE else View.GONE
            text = clearLabel
        }

        // 키보드가 떠 있으면 먼저 내리고 (입력칸 포커스도 풀기), 화면 크기가 자리 잡은 뒤에 띄움.
        // 키보드가 열린 채로 띄우면 팝오버가 키보드를 내리며 화면이 줄었다 늘면서 흔들리고, 닫을 때 다시 키보드가 뜸.
        val imeShown = androidx.core.view.ViewCompat.getRootWindowInsets(anchor)
            ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        anchor.rootView.findFocus()?.let { f -> if (f is android.widget.EditText) f.clearFocus() }
        (ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(anchor.windowToken, 0)
        if (imeShown) anchor.postDelayed({ if (anchor.isAttachedToWindow) place(anchor, content, apply, onClear) }, 250)
        else place(anchor, content, apply, onClear)
    }

    /**
     * 화면 좌우 16 안쪽 가득, 누른 것 바로 아래 4 (아래가 모자라면 위).
     * 따로 창(PopupWindow)을 띄우지 않고 지금 화면 위에 한 겹 얹음: 창을 쓰면 기기에 따라
     * 열고 닫을 때 창 위치를 다시 잡으며 좌우로 튀어 보여서. 바깥 · 뒤로 · 취소 = 닫기.
     */
    private fun place(anchor: View, content: View, apply: () -> Unit, onClear: (() -> Unit)?) {
        val ctx = anchor.context
        val d = anchor.resources.displayMetrics.density
        val root = anchor.rootView.findViewById<android.widget.FrameLayout>(android.R.id.content) ?: return
        val margin = (16 * d).toInt()
        val width = minOf(root.width - margin * 2, (480 * d).toInt())

        // 바깥: 투명한 판 (누르면 닫힘)
        val scrim = android.widget.FrameLayout(ctx).apply {
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        content.elevation = 12 * d
        content.isClickable = true   // 팝오버 안을 눌러도 닫히지 않게
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val h = content.measuredHeight
        val a = IntArray(2).also { anchor.getLocationInWindow(it) }
        val r = IntArray(2).also { root.getLocationInWindow(it) }
        val ax = a[0] - r[0]; val ay = a[1] - r[1]
        val gap = (4 * d).toInt()
        val below = ay + anchor.height + gap
        val y = if (below + h <= root.height - margin) below else maxOf(margin, ay - gap - h)
        scrim.addView(content, android.widget.FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = (root.width - width) / 2; topMargin = y
        })
        root.addView(scrim, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        var back: androidx.activity.OnBackPressedCallback? = null
        var closing = false
        fun close() {
            if (closing) return
            closing = true
            back?.remove()
            content.animate().alpha(0f).setDuration(anchor.resources.getInteger(R.integer.motion_press_exit).toLong())
                .withEndAction { root.removeView(scrim) }.start()
            anchor.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }
        scrim.setOnClickListener { close() }
        content.findViewById<View>(R.id.btnWheelCancel).setOnClickListener { close() }
        content.findViewById<View>(R.id.btnWheelApply).setOnClickListener { apply(); close() }
        content.findViewById<View>(R.id.btnWheelClear).setOnClickListener { onClear?.invoke(); close() }
        (generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<androidx.activity.ComponentActivity>().firstOrNull())?.let { act ->
            back = object : androidx.activity.OnBackPressedCallback(true) { override fun handleOnBackPressed() = close() }
            act.onBackPressedDispatcher.addCallback(back!!)
        }

        // 나타남: 위쪽 가운데에서 0.96 → 1 로 커지며 (시안 팝오버, 150ms)
        content.pivotX = width / 2f; content.pivotY = 0f
        content.alpha = 0f; content.scaleX = 0.96f; content.scaleY = 0.96f
        content.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(anchor.resources.getInteger(R.integer.motion_short).toLong())
            .setInterpolator(android.view.animation.AnimationUtils.loadInterpolator(ctx, R.interpolator.motion_decelerate)).start()
        content.post { content.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED) }
    }
}
