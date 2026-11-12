package com.tripplanner.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.AnimationUtils
import android.widget.Checkable
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.tripplanner.R

/**
 * 켜고 끄기 (MaterialSwitch 대신, 시안 Switch).
 * 글자는 왼쪽, 스위치는 오른쪽 끝. 줄 전체를 누르면 바뀜.
 *  · 꺼짐: 회색 바탕 + 테두리 · 작은 손잡이
 *  · 켜짐: primary 바탕 · 흰 손잡이 + ✓
 * 손잡이는 150ms 에 걸쳐 움직임.
 */
class TpSwitch @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatTextView(context, attrs), Checkable {

    private val dp = resources.displayMetrics.density
    private val trackW = 52 * dp; private val trackH = 32 * dp
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * dp }
    private val check = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * dp; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private var checked = false
    private var pos = 0f          // 0 = 꺼짐 · 1 = 켜짐 (움직이는 중엔 사이 값)
    private var anim: ValueAnimator? = null
    private var listener: ((TpSwitch, Boolean) -> Unit)? = null

    init {
        isClickable = true; isFocusable = true
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        minHeight = (48 * dp).toInt()
        if (background == null) background = ContextCompat.getDrawable(context, R.drawable.press_overlay)
        else foreground = ContextCompat.getDrawable(context, R.drawable.press_card)
    }

    override fun getCompoundPaddingEnd() = super.getCompoundPaddingEnd() + (trackW + 12 * dp).toInt()
    override fun getCompoundPaddingRight() = super.getCompoundPaddingRight() + (trackW + 12 * dp).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (text.isNullOrEmpty() && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY)
            setMeasuredDimension((trackW + paddingLeft + paddingRight).toInt(), measuredHeight)
    }

    fun setOnCheckedChangeListener(l: ((TpSwitch, Boolean) -> Unit)?) { listener = l }

    override fun performClick(): Boolean { toggle(); return super.performClick() }
    override fun isChecked() = checked
    override fun toggle() { isChecked = !checked }
    override fun setChecked(c: Boolean) {
        if (c == checked) return
        checked = c
        anim?.cancel()
        if (isLaidOut) anim = ValueAnimator.ofFloat(pos, if (c) 1f else 0f).apply {
            duration = resources.getInteger(R.integer.motion_short).toLong()
            interpolator = AnimationUtils.loadInterpolator(context, R.interpolator.motion_standard)
            addUpdateListener { pos = it.animatedValue as Float; invalidate() }
            start()
        } else { pos = if (c) 1f else 0f; invalidate() }
        listener?.invoke(this, c)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val c = context
        val right = width - paddingRight.toFloat()
        val left = right - trackW
        val top = (height - trackH) / 2f
        val r = RectF(left, top, right, top + trackH)
        val on = ContextCompat.getColor(c, R.color.primary)
        val offBg = ContextCompat.getColor(c, R.color.surface_container_highest)
        val outline = ContextCompat.getColor(c, R.color.outline)
        // 바탕
        paint.color = ColorUtils.blendARGB(offBg, on, pos)
        canvas.drawRoundRect(r, trackH / 2, trackH / 2, paint)
        if (pos < 1f) {
            stroke.color = ColorUtils.setAlphaComponent(outline, ((1f - pos) * 255).toInt())
            val inset = stroke.strokeWidth / 2
            canvas.drawRoundRect(RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset), trackH / 2, trackH / 2, stroke)
        }
        // 손잡이: 꺼짐 16 → 켜짐 24
        val radius = (8 + 4 * pos) * dp
        val cx = left + trackH / 2 + (trackW - trackH) * pos
        val cy = top + trackH / 2
        paint.color = ColorUtils.blendARGB(outline, ContextCompat.getColor(c, R.color.card_bg), pos)
        canvas.drawCircle(cx, cy, radius, paint)
        if (pos > 0.5f) {
            check.color = ColorUtils.setAlphaComponent(on, (((pos - 0.5f) * 2) * 255).toInt())
            val s = 5 * dp
            val path = android.graphics.Path().apply {
                moveTo(cx - s, cy); lineTo(cx - s * 0.3f, cy + s * 0.7f); lineTo(cx + s, cy - s * 0.6f)
            }
            canvas.drawPath(path, check)
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.Switch::class.java.name
        info.isCheckable = true; info.isChecked = checked
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val s = super.onCreateDrawableState(extraSpace + 1)
        if (checked) mergeDrawableStates(s, intArrayOf(android.R.attr.state_checked))
        return s
    }
}
