package com.tripplanner.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * 넘치면 다음 줄로 이어지는 가로 배치 (ChipGroup 대신 — 뱃지 · 종류 칩).
 * 칸 사이 가로 spacingH · 줄 사이 spacingV.
 */
open class FlowLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {

    var spacingH = (8 * resources.displayMetrics.density).toInt()
    var spacingV = (8 * resources.displayMetrics.density).toInt()

    init {
        val a = context.obtainStyledAttributes(attrs, com.tripplanner.R.styleable.FlowLayout)
        spacingH = a.getDimensionPixelSize(com.tripplanner.R.styleable.FlowLayout_tpSpacingH, spacingH)
        spacingV = a.getDimensionPixelSize(com.tripplanner.R.styleable.FlowLayout_tpSpacingV, spacingV)
        a.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0; var y = 0; var rowH = 0; var usedW = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i); if (c.visibility == View.GONE) continue
            measureChildWithMargins(c, widthMeasureSpec, 0, heightMeasureSpec, 0)
            val lp = c.layoutParams as MarginLayoutParams
            val cw = c.measuredWidth + lp.leftMargin + lp.rightMargin
            val ch = c.measuredHeight + lp.topMargin + lp.bottomMargin
            if (x > 0 && x + cw > maxW) { x = 0; y += rowH + spacingV; rowH = 0 }
            x += cw + spacingH
            usedW = maxOf(usedW, x - spacingH)
            rowH = maxOf(rowH, ch)
        }
        val h = y + rowH + paddingTop + paddingBottom
        val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(widthMeasureSpec)
                else usedW + paddingLeft + paddingRight
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var x = 0; var y = 0; var rowH = 0
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        for (i in 0 until childCount) {
            val c = getChildAt(i); if (c.visibility == View.GONE) continue
            val lp = c.layoutParams as MarginLayoutParams
            val cw = c.measuredWidth + lp.leftMargin + lp.rightMargin
            val ch = c.measuredHeight + lp.topMargin + lp.bottomMargin
            if (x > 0 && x + cw > maxW) { x = 0; y += rowH + spacingV; rowH = 0 }
            val left = if (rtl) r - l - paddingRight - x - cw + lp.leftMargin else paddingLeft + x + lp.leftMargin
            val top = paddingTop + y + lp.topMargin
            c.layout(left, top, left + c.measuredWidth, top + c.measuredHeight)
            x += cw + spacingH
            rowH = maxOf(rowH, ch)
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet?) = MarginLayoutParams(context, attrs)
    override fun generateDefaultLayoutParams() = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(p: LayoutParams?) = MarginLayoutParams(p)
    override fun checkLayoutParams(p: LayoutParams?) = p is MarginLayoutParams
}
