package com.tripplanner.ui

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R
import kotlin.math.abs
import kotlin.math.min

/**
 * 시안 TimePicker 의 휠 한 줄 (시 · 분 · 날짜).
 * 5칸이 보이고 가운데 칸이 값. 가운데에서 멀수록 작고 흐리게.
 * 돌리면 칸마다 짧은 진동, 화면 읽기(TalkBack)에서는 위·아래로 넘기기로 한 칸씩 바뀜.
 */
class WheelView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RecyclerView(context, attrs) {

    private val itemH = (ITEM_DP * resources.displayMetrics.density).toInt()
    private var items: List<String> = emptyList()
    private val lm = LinearLayoutManager(context)
    private val regular = ResourcesCompat.getFont(context, R.font.pretendard_medium)
    private val bold = ResourcesCompat.getFont(context, R.font.pretendard_bold)

    /** 지금 가운데 칸 */
    var selectedIndex = 0
        private set
    /** 값이 바뀔 때 (돌리는 중에도) */
    var onChange: ((Int) -> Unit)? = null
    /** 화면 읽기용 이름 (예: "시") */
    var label: String = ""

    init {
        layoutManager = lm
        overScrollMode = OVER_SCROLL_NEVER
        clipToPadding = false
        val pad = itemH * (VISIBLE / 2)
        setPadding(0, pad, 0, pad)
        LinearSnapHelper().attachToRecyclerView(this)
        adapter = WheelAdapter()
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) { restyle(); updateSelected(fromUser = true) }
        })
        ViewCompat.replaceAccessibilityAction(this, AccessibilityActionCompat.ACTION_SCROLL_FORWARD, null) { _, _ -> step(1) }
        ViewCompat.replaceAccessibilityAction(this, AccessibilityActionCompat.ACTION_SCROLL_BACKWARD, null) { _, _ -> step(-1) }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) =
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(itemH * VISIBLE, MeasureSpec.EXACTLY))

    fun setItems(list: List<String>, select: Int) {
        items = list
        adapter?.notifyDataSetChanged()
        select(select)
    }

    /** 애니메이션 없이 바로 이 칸으로 */
    fun select(index: Int) {
        selectedIndex = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        lm.scrollToPositionWithOffset(selectedIndex, 0)
        post { restyle(); describe() }
    }

    private fun step(d: Int): Boolean {
        val to = (selectedIndex + d).coerceIn(0, items.size - 1)
        if (to == selectedIndex) return false
        smoothScrollBy(0, (to - selectedIndex) * itemH)
        return true
    }

    private fun updateSelected(fromUser: Boolean) {
        val center = height / 2
        var best = -1; var bestD = Int.MAX_VALUE
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val d = abs((c.top + c.bottom) / 2 - center)
            if (d < bestD) { bestD = d; best = getChildAdapterPosition(c) }
        }
        if (best >= 0 && best != selectedIndex) {
            selectedIndex = best
            if (fromUser) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            describe()
            onChange?.invoke(best)
        }
    }

    /** 가운데에서 멀어질수록 작고 흐리게 */
    private fun restyle() {
        val center = height / 2f
        for (i in 0 until childCount) {
            val c = getChildAt(i) as TextView
            val d = min(abs((c.top + c.bottom) / 2f - center) / itemH, 2.5f)
            val near = 1f - min(d, 1f)
            c.scaleX = 1f + 0.22f * near; c.scaleY = c.scaleX
            c.alpha = (1f - 0.3f * d).coerceAtLeast(0.3f)
            val sel = d < 0.5f
            c.typeface = if (sel) bold else regular
            c.setTextColor(context.color(if (sel) R.color.on_surface else R.color.text_sub))
        }
    }

    private fun describe() {
        contentDescription = label
        ViewCompat.setStateDescription(this, items.getOrNull(selectedIndex))
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.35f
    }

    /** 꺼져 있으면 돌아가지 않음 (도착 '시간 자유') */
    override fun onInterceptTouchEvent(e: MotionEvent) = isEnabled && super.onInterceptTouchEvent(e)
    override fun onTouchEvent(e: MotionEvent) = isEnabled && super.onTouchEvent(e)

    private inner class WheelAdapter : Adapter<ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : ViewHolder(TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemH)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            fontFeatureSettings = "tnum"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            typeface = regular
        }) {}
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            (holder.itemView as TextView).text = items[position]
            // 누른 칸을 가운데로
            holder.itemView.setOnClickListener { if (isEnabled) step(holder.bindingAdapterPosition - selectedIndex) }
        }
        override fun getItemCount() = items.size
    }

    companion object {
        const val ITEM_DP = 40
        const val VISIBLE = 5
    }
}
