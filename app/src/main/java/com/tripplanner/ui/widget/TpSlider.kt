package com.tripplanner.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.roundToInt

/**
 * 가로 막대로 값 고르기 (Material Slider 대신 — 표지 사진 확대).
 * 막대 4 · 손잡이 20 · 끌거나 눌러서 바꿈. 화면 읽기에서는 위·아래로 넘기면 10% 씩.
 */
class TpSlider @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val dp = resources.displayMetrics.density
    var valueFrom = 0f
    var valueTo = 1f
    var value = 0f
        set(v) { field = v.coerceIn(valueFrom, valueTo); invalidate() }
    var activeColor = 0xFFFFFFFF.toInt()
    var inactiveColor = 0x66FFFFFF
    var thumbColor = 0xFFFFFFFF.toInt()
    /** (값, 사용자가 바꿨는지) */
    var onChange: ((Float, Boolean) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbR = 10 * dp

    init { isFocusable = true; minimumHeight = (48 * dp).toInt() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), resolveSize((48 * dp).toInt(), heightMeasureSpec))

    private fun frac() = if (valueTo == valueFrom) 0f else (value - valueFrom) / (valueTo - valueFrom)

    override fun onDraw(canvas: Canvas) {
        val l = paddingLeft + thumbR; val r = width - paddingRight - thumbR
        val cy = height / 2f
        val x = l + (r - l) * frac()
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = 4 * dp
        paint.color = inactiveColor; canvas.drawLine(l, cy, r, cy, paint)
        paint.color = activeColor; canvas.drawLine(l, cy, x, cy, paint)
        paint.color = thumbColor; canvas.drawCircle(x, cy, thumbR, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP -> performClick()
        }
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) {
            val l = paddingLeft + thumbR; val r = width - paddingRight - thumbR
            val f = ((e.x - l) / (r - l)).coerceIn(0f, 1f)
            value = valueFrom + (valueTo - valueFrom) * f
            onChange?.invoke(value, true)
        }
        return true
    }

    override fun performClick() = super.performClick()

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.SeekBar::class.java.name
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, valueFrom, valueTo, value)
        if (android.os.Build.VERSION.SDK_INT >= 30) info.stateDescription = "${(frac() * 100).roundToInt()}%"
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val step = (valueTo - valueFrom) / 10
        when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> value += step
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> value -= step
            else -> return super.performAccessibilityAction(action, arguments)
        }
        onChange?.invoke(value, true)
        return true
    }
}
