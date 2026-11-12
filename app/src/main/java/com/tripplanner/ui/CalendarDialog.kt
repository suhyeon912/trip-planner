package com.tripplanner.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.Toolbar
import com.tripplanner.R
import com.tripplanner.data.Trip
import com.tripplanner.route.TimeUtil
import java.time.LocalDate
import java.time.YearMonth

/**
 * 시안 기간 고르기: 전체 화면 달력 (MaterialDatePicker 대신).
 *  · 달을 세로로 이어서 보여 줌, 고른 날이 있는 달부터
 *  · 칸 44 높이 · 시작·끝은 채운 동그라미 · 사이는 띠 · 오늘은 테두리
 *  · 지난 날은 흐리게, 못 고름 (이미 고른 날이 지난 날이면 그날부터는 고를 수 있음)
 *  · 처음 누른 날 = 시작, 다음 누른 날 = 끝 (시작보다 앞이면 다시 시작)
 * range = false 면 하루만 고름.
 */
class CalendarDialog private constructor(
    activity: Activity,
    private val title: String,
    private val range: Boolean,
    private var start: LocalDate?,
    private var end: LocalDate?,
    private val onPick: (LocalDate, LocalDate) -> Unit
) : Dialog(activity, R.style.Theme_TripPlanner_FullScreenDialog) {

    companion object {
        fun range(activity: Activity, title: String, start: LocalDate?, end: LocalDate?, onPick: (LocalDate, LocalDate) -> Unit) =
            CalendarDialog(activity, title, true, start, end?.takeIf { start != null && !it.isBefore(start) }, onPick).show()

        fun single(activity: Activity, title: String, date: LocalDate?, onPick: (LocalDate) -> Unit) =
            CalendarDialog(activity, title, false, date, null) { d, _ -> onPick(d) }.show()
    }

    private val today = LocalDate.now()
    private val minDate = listOfNotNull(today, start).minOrNull()!!
    private sealed class Cell {
        data class Month(val ym: YearMonth) : Cell()
        object Blank : Cell()
        data class Day(val date: LocalDate) : Cell()
    }
    private val cells = mutableListOf<Cell>()
    private lateinit var grid: RecyclerView
    private lateinit var value: TextView
    private lateinit var nights: TextView
    private lateinit var ok: View

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_range_calendar)
        window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        findViewById<Toolbar>(R.id.calToolbar).apply {
            this.title = this@CalendarDialog.title
            setNavigationOnClickListener { dismiss() }
        }
        findViewById<TextView>(R.id.calLabel).setText(if (range) R.string.range_label else R.string.date_label)
        value = findViewById(R.id.calValue)
        nights = findViewById(R.id.calNights)
        ok = findViewById(R.id.calOk)
        findViewById<View>(R.id.calCancel).setOnClickListener { dismiss() }
        ok.setOnClickListener {
            val s = start ?: return@setOnClickListener
            onPick(s, end ?: s); dismiss()
        }
        buildWeekdays()

        // 달: 고를 수 있는 첫 달부터 2년
        val first = YearMonth.from(minDate)
        for (m in 0 until 24) {
            val ym = first.plusMonths(m.toLong())
            cells += Cell.Month(ym)
            repeat(ym.atDay(1).dayOfWeek.value % 7) { cells += Cell.Blank }   // 일요일 시작
            for (d in 1..ym.lengthOfMonth()) cells += Cell.Day(ym.atDay(d))
        }
        grid = findViewById(R.id.calGrid)
        grid.layoutManager = GridLayoutManager(context, 7).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = if (cells[position] is Cell.Month) 7 else 1
            }
        }
        grid.adapter = Adapter()
        grid.itemAnimator = null
        // 고른 날이 있는 달로
        val focus = YearMonth.from(start ?: today)
        val idx = cells.indexOfFirst { it is Cell.Month && it.ym == focus }.coerceAtLeast(0)
        (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(idx, 0)
        renderHeader()
    }

    private fun buildWeekdays() {
        val row = findViewById<LinearLayout>(R.id.calWeekdays)
        listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, s ->
            row.addView(TextView(context).apply {
                text = s; gravity = Gravity.CENTER
                setTextAppearance(R.style.TextAppearance_TripPlanner_LabelMedium)
                setTextColor(context.color(if (i == 0) R.color.badge_red else R.color.text_sub))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun fmt(d: LocalDate) = "${d.year}.${d.monthValue}.${d.dayOfMonth} (${TimeUtil.koreanDayName(d).first()})"

    private fun renderHeader() {
        val s = start; val e = end
        value.text = when {
            s == null -> context.getString(if (range) R.string.range_label else R.string.date_label)
            !range -> fmt(s)
            e == null -> "${fmt(s)} – "
            else -> TripFormat.fullRange(Trip(title = "", startDate = s.toString(), endDate = e.toString()))
        }
        value.setTextColor(context.color(if (s == null) R.color.text_sub else R.color.on_surface))
        nights.text = if (range && s != null) TripFormat.nightsDays(s.toString(), (e ?: s).toString()) else ""
        ok.isEnabled = s != null
    }

    private fun tap(d: LocalDate) {
        val s = start
        if (!range) { start = d }
        else if (s == null || end != null || d.isBefore(s)) { start = d; end = null }
        else end = d
        renderHeader()
        grid.adapter?.notifyDataSetChanged()
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int) = when (cells[position]) { is Cell.Month -> 0; Cell.Blank -> 1; is Cell.Day -> 2 }
        override fun getItemCount() = cells.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val d = parent.resources.displayMetrics.density
            val v: View = when (viewType) {
                0 -> TextView(parent.context).apply {
                    setTextAppearance(R.style.TextAppearance_TripPlanner_TitleSmall)
                    setPadding((8 * d).toInt(), (20 * d).toInt(), 0, (8 * d).toInt())
                    ViewCompat.setAccessibilityHeading(this, true)
                }
                1 -> View(parent.context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
                else -> DayCellView(parent.context)
            }
            v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                if (viewType == 0) ViewGroup.LayoutParams.WRAP_CONTENT else (44 * d).toInt())
            return object : RecyclerView.ViewHolder(v) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val c = cells[position]) {
                is Cell.Month -> (holder.itemView as TextView).text = context.getString(R.string.cal_month, c.ym.year, c.ym.monthValue)
                Cell.Blank -> {}
                is Cell.Day -> (holder.itemView as DayCellView).apply {
                    val s = start; val e = end
                    val enabledDay = !c.date.isBefore(minDate)
                    bind(c.date.dayOfMonth,
                        isStart = s != null && c.date == s,
                        isEnd = e != null && c.date == e && e != s,
                        inBand = s != null && e != null && c.date.isAfter(s) && c.date.isBefore(e),
                        hasRange = s != null && e != null && e != s,
                        isToday = c.date == today, enabledDay = enabledDay)
                    val state = when {
                        s != null && c.date == s -> context.getString(if (range) R.string.cal_start else R.string.cal_selected)
                        e != null && c.date == e -> context.getString(R.string.cal_end)
                        else -> null
                    }
                    contentDescription = listOfNotNull("${c.date.year}년 ${c.date.monthValue}월 ${c.date.dayOfMonth}일 ${TimeUtil.koreanDayName(c.date)}요일",
                        if (c.date == today) context.getString(R.string.cal_today) else null).joinToString(", ")
                    ViewCompat.setStateDescription(this, state)
                    isEnabled = enabledDay
                    setOnClickListener(if (enabledDay) View.OnClickListener { tap(c.date) } else null)
                }
            }
        }
    }

    /** 날짜 한 칸: 띠 · 동그라미 · 오늘 테두리 · 숫자 */
    private class DayCellView(context: Context) : View(context) {
        private val dp = resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * dp }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics)
            fontFeatureSettings = "tnum"
        }
        private val medium = ResourcesCompat.getFont(context, R.font.pretendard_medium)
        private val bold = ResourcesCompat.getFont(context, R.font.pretendard_bold)
        private var day = 0; private var isStart = false; private var isEnd = false
        private var inBand = false; private var hasRange = false; private var isToday = false; private var on = true

        init { isClickable = true; background = context.getDrawable(R.drawable.press_overlay_borderless)?.mutate() }

        fun bind(day: Int, isStart: Boolean, isEnd: Boolean, inBand: Boolean, hasRange: Boolean, isToday: Boolean, enabledDay: Boolean) {
            this.day = day; this.isStart = isStart; this.isEnd = isEnd; this.inBand = inBand
            this.hasRange = hasRange; this.isToday = isToday; this.on = enabledDay
            invalidate()
        }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val cx = w / 2; val cy = h / 2
            val r = minOf(20 * dp, h / 2 - 2 * dp)
            val bandTop = cy - r; val bandBottom = cy + r
            fill.color = context.color(R.color.primary_light)
            when {
                inBand -> c.drawRect(0f, bandTop, w, bandBottom, fill)
                isStart && hasRange -> c.drawRect(cx, bandTop, w, bandBottom, fill)
                isEnd -> c.drawRect(0f, bandTop, cx, bandBottom, fill)
            }
            val selected = isStart || isEnd
            if (selected) { fill.color = context.color(R.color.primary); c.drawCircle(cx, cy, r, fill) }
            else if (isToday) { ring.color = context.color(R.color.primary); c.drawCircle(cx, cy, r - ring.strokeWidth / 2, ring) }
            text.typeface = if (selected) bold else medium
            text.color = context.color(when {
                selected -> R.color.on_primary
                !on -> R.color.outline
                else -> R.color.on_surface
            })
            text.alpha = if (!on) 110 else 255
            c.drawText(day.toString(), cx, cy - (text.descent() + text.ascent()) / 2, text)
        }
    }
}

