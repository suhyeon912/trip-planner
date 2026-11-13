package com.tripplanner.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ListenerRegistration
import com.tripplanner.R
import com.tripplanner.TripPlannerApp
import com.tripplanner.share.ActivityEvent
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 활동 기록 (시안 TripHistory) — 공유 중인 여행에서 누가 무엇을 바꿨는지.
 * 위: 전체 / 사람별 칩, 아래: 날짜별 묶음 카드 (오늘 · 11.08 (일)), 줄마다 동그란 이름 머리글자 · 문장 · 시각.
 */
class ActivityLogActivity : AppCompatActivity() {

    companion object { const val EXTRA_TRIP_ID = TripDetailActivity.EXTRA_TRIP_ID }

    private lateinit var content: LinearLayout
    private val chipRow by lazy { LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL } }
    private val scroller by lazy {
        HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false; addView(chipRow) }
    }
    private var registration: ListenerRegistration? = null
    private var members: List<Pair<String, String>> = emptyList()
    private var events: List<ActivityEvent> = emptyList()
    private var me = ""
    /** 고른 사람 uid (null = 전체) */
    private var filter: String? = null
    private val d by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        content = findViewById(R.id.content)
        limitScrollContentWidth(R.id.scroll)
        val toolbar = setupToolbar(getString(R.string.activity_log))
        filter = savedInstanceState?.getString("filter")
        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
        val app = application as TripPlannerApp
        lifecycleScope.launch {
            val trip = app.repository.getTrip(tripId) ?: run { finish(); return@launch }
            toolbar.subtitle = trip.title
            val code = trip.shareCode ?: run { finish(); return@launch }
            registration = app.share.observeActivity(code) { m, e, myUid ->
                members = m; events = e; me = myUid
                render()
            }
            render()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putString("filter", filter) }
    override fun onDestroy() { registration?.remove(); super.onDestroy() }

    /** 사람마다 고정 색 (들어온 순서) */
    private fun colorOf(uid: String, name: String): Int {
        val i = members.indexOfFirst { it.first == uid }.takeIf { it >= 0 } ?: (name.hashCode() and 0xff)
        return PinIcons.DAY_COLORS[i % PinIcons.DAY_COLORS.size]
    }

    private fun displayName(uid: String, name: String) = name.ifBlank { members.firstOrNull { it.first == uid }?.second.orEmpty() }
        .ifBlank { getString(R.string.activity_someone) }

    private fun avatar(uid: String, name: String, size: Int) = TextView(this).apply {
        text = displayName(uid, name).take(1)
        gravity = Gravity.CENTER
        setTextColor(0xFFFFFFFF.toInt())
        textSize = if (size >= 32) 14f else 13f
        typeface = ResourcesCompat.getFont(context, R.font.pretendard_bold)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(colorOf(uid, name)) }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    private fun render() {
        content.removeAllViews()
        // 사람 칩: 전체(진한 채움) / 이름 (머리글자 동그라미 + 이름)
        chipRow.removeAllViews()
        (scroller.parent as? ViewGroup)?.removeView(scroller)   // 다시 그릴 때도 같은 칩 줄 (가로 스크롤 위치 유지)
        content.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(4) })
        chip(null, getString(R.string.activity_all))
        members.forEach { (uid, name) -> chip(uid, displayName(uid, name) + if (uid == me) getString(R.string.activity_me) else "") }

        val shown = events.filter { filter == null || it.uid == filter }
        if (shown.isEmpty()) {
            content.addView(TextView(this).apply {
                setText(R.string.activity_empty)
                setTextAppearance(R.style.TextAppearance_TripPlanner_BodyMedium)
                setTextColor(color(R.color.text_sub))
                gravity = Gravity.CENTER
                setPadding(0, dp(48), 0, 0)
            })
            return
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        shown.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }.forEach { (date, list) ->
            content.addView(TextView(this).apply {
                text = when (date) {
                    today -> getString(R.string.activity_today)
                    today.minusDays(1) -> getString(R.string.activity_yesterday)
                    else -> getString(R.string.activity_date, date.monthValue, date.dayOfMonth,
                        com.tripplanner.route.TimeUtil.koreanDayName(date).first().toString())
                }
                setTextAppearance(R.style.TextAppearance_TripPlanner_LabelLarge)
                setTextColor(color(R.color.text_sub))
                setPadding(0, dp(16), 0, dp(8))
                ViewCompat.setAccessibilityHeading(this, true)
            })
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card)
                clipToOutline = true
            }
            list.forEachIndexed { i, e ->
                if (i > 0) card.addView(View(this).apply { setBackgroundColor(color(R.color.outline_variant)) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
                card.addView(row(e))
            }
            content.addView(card)
        }
    }

    private fun chip(uid: String?, label: String) {
        val sel = filter == uid
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(40)
            setPadding(if (uid == null) dp(14) else dp(4), 0, dp(14), 0)
            background = GradientDrawable().apply {
                cornerRadius = 20 * d
                setColor(color(if (sel) R.color.on_surface else R.color.card_bg))
                if (!sel) setStroke(dp(1), color(R.color.outline_variant))
            }
            foreground = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.press_pill_primary)
            isClickable = true; isFocusable = true
            contentDescription = label
            ViewCompat.setStateDescription(this, if (sel) getString(R.string.state_selected) else null)
            setOnClickListener { filter = uid; render() }
        }
        if (uid != null) chip.addView(avatar(uid, label, 32).apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(8)
        })
        chip.addView(TextView(this).apply {
            text = label
            setTextAppearance(R.style.TextAppearance_TripPlanner_LabelLarge)
            setTextColor(color(if (sel) R.color.background else R.color.on_surface))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        chipRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply {
            if (chipRow.childCount > 0) marginStart = dp(8)
        })
    }

    private fun row(e: ActivityEvent): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(72)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        r.addView(avatar(e.uid, e.name, 32))
        val text = TextView(this).apply {
            this.text = sentence(e)
            setTextAppearance(R.style.TextAppearance_TripPlanner_BodyMedium)
            setTextColor(color(R.color.on_surface))
        }
        r.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12); marginEnd = dp(12) })
        val t = Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()).toLocalTime()
        r.addView(TextView(this).apply {
            this.text = "%02d:%02d".format(t.hour, t.minute)
            setTextAppearance(R.style.TextAppearance_TripPlanner_BodySmall)
            setTextColor(color(R.color.text_sub))
            fontFeatureSettings = "tnum"
        })
        r.contentDescription = "${sentence(e)}, ${"%02d:%02d".format(t.hour, t.minute)}"
        return r
    }

    /** "민지님이 시부야 스카이 후보에 추가했어요" — 이름 · 대상은 굵게 */
    private fun sentence(e: ActivityEvent): CharSequence {
        val b = SpannableStringBuilder()
        val bold = ResourcesCompat.getFont(this, R.font.pretendard_bold) ?: Typeface.DEFAULT_BOLD
        fun strong(s: String) { val st = b.length; b.append(s); b.setSpan(FontStyle(bold), st, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        strong(displayName(e.uid, e.name)); b.append(getString(R.string.activity_subject))
        val day = e.day?.let { dayLabel(it) }
        when (e.type) {
            ActivityEvent.Type.JOIN -> b.append(getString(R.string.activity_join))
            ActivityEvent.Type.TRAVEL -> b.append(getString(R.string.activity_travel))
            ActivityEvent.Type.FILE -> { strong(e.target); b.append(Josa.eulReul(e.target)); b.append(getString(R.string.activity_file)) }
            else -> {
                strong(e.target); b.append(" ")
                b.append(when (e.type) {
                    ActivityEvent.Type.PLACE_ADD -> if (day == null) getString(R.string.activity_add_candidate) else getString(R.string.activity_add_day, day)
                    ActivityEvent.Type.PLACE_CONFIRM -> if (day == null) getString(R.string.activity_confirm_undated) else getString(R.string.activity_confirm, day)
                    ActivityEvent.Type.PLACE_MOVE -> if (day == null) getString(R.string.activity_move_undated)
                        else getString(R.string.activity_move, day, e.order ?: 1)
                    ActivityEvent.Type.PLACE_HOURS -> getString(R.string.activity_hours)
                    ActivityEvent.Type.PLACE_DELETE -> getString(R.string.activity_delete)
                    else -> getString(R.string.activity_edit)
                })
            }
        }
        return b
    }

    private class FontStyle(private val font: Typeface) : android.text.style.MetricAffectingSpan() {
        override fun updateDrawState(tp: android.text.TextPaint) { tp.typeface = font }
        override fun updateMeasureState(tp: android.text.TextPaint) { tp.typeface = font }
    }
}

/** 조사: 받침이 있으면 '을', 없으면 '를' (한글이 아니면 '를') */
object Josa {
    fun eulReul(word: String): String {
        val c = word.trimEnd().lastOrNull() ?: return "를"
        if (c !in '가'..'힣') return if (c.isDigit() && c in "0136789") "을" else "를"
        return if ((c - '가') % 28 != 0) "을" else "를"
    }
}
