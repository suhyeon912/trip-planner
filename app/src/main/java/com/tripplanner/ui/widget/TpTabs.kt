package com.tripplanner.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.tripplanner.R

/**
 * 세그먼트 탭 (TabLayout 대신, 시안 '전체 / 내 여행 / 초대받은', '일정 / 후보').
 * 바탕·안쪽 4 는 감싸는 칸(bg_segmented)이 그리고, 이 뷰는 칸들과 흰 선택 표시만 그립니다.
 * 고르면 선택 표시가 200ms 에 걸쳐 미끄러져 감.
 */
class TpTabs @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    private val dp = resources.displayMetrics.density
    private val indicator = View(context).apply { background = ContextCompat.getDrawable(context, R.drawable.bg_segmented_selected) }
    private val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val tabs = mutableListOf<TextView>()

    var selectedIndex = -1
        private set
    /** 고를 때 (같은 칸을 다시 누르면 부르지 않음) */
    var onSelect: ((Int) -> Unit)? = null

    init {
        addView(indicator, LayoutParams(0, (40 * dp).toInt()))
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, (40 * dp).toInt()))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        row.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        ViewCompat.setAccessibilityDelegate(row, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.setCollectionInfo(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat.obtain(1, tabs.size, false))
            }
        })
    }

    val tabCount get() = tabs.size

    fun addTab(text: CharSequence = "") {
        val i = tabs.size
        val tv = TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextAppearance(R.style.TextAppearance_TripPlanner_LabelLarge)
            background = ContextCompat.getDrawable(context, R.drawable.press_r10)
            isClickable = true; isFocusable = true
            setOnClickListener { select(i) }
            ViewCompat.setAccessibilityDelegate(this, object : androidx.core.view.AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.roleDescription = "탭"
                    info.isSelected = i == selectedIndex
                    info.setCollectionItemInfo(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(0, 1, i, 1, false, i == selectedIndex))
                }
            })
        }
        tabs += tv
        row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        if (selectedIndex < 0) select(0, animate = false, notify = false) else restyle()
    }

    fun setTabText(i: Int, text: CharSequence) { tabs.getOrNull(i)?.text = text }

    fun select(i: Int, animate: Boolean = true, notify: Boolean = true) {
        if (i !in tabs.indices) return
        val changed = i != selectedIndex
        selectedIndex = i
        restyle()
        moveIndicator(animate && isLaidOut)
        if (changed && notify) onSelect?.invoke(i)
    }

    private fun restyle() = tabs.forEachIndexed { k, tv ->
        tv.setTextColor(ContextCompat.getColor(context, if (k == selectedIndex) R.color.on_surface else R.color.text_sub))
        tv.isSelected = k == selectedIndex
    }

    private fun moveIndicator(animate: Boolean) {
        if (width == 0 || tabs.isEmpty()) return
        val w = width / tabs.size
        if (indicator.layoutParams.width != w) indicator.layoutParams = (indicator.layoutParams as LayoutParams).apply { width = w }
        val x = (w * selectedIndex).toFloat()
        indicator.animate().cancel()
        if (animate) indicator.animate().translationX(x)
            .setDuration(resources.getInteger(R.integer.motion_medium).toLong())
            .setInterpolator(AnimationUtils.loadInterpolator(context, R.interpolator.motion_standard)).start()
        else indicator.translationX = x
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { moveIndicator(false) }
    }
}
