package com.tripplanner.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.tripplanner.R

/**
 * 진행 중 표시 (LinearProgressIndicator 대신): 높이 4 의 막대 위로 짧은 막대가 왼쪽 → 오른쪽으로 반복해서 지나감.
 * 보일 때만 움직이고, 시스템 '애니메이션 제거' 설정이면 멈춘 막대로 보임.
 */
class TpProgressBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    init { contentDescription = context.getString(R.string.loading) }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(getDefaultSize(0, widthMeasureSpec), resolveSize((4 * dp).toInt(), heightMeasureSpec))

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) { if (!anim.isStarted) anim.start() } else anim.cancel()
    }

    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val primary = ContextCompat.getColor(context, R.color.primary)
        val w = width.toFloat(); val h = height.toFloat()
        paint.color = ColorUtils.setAlphaComponent(primary, 48)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.color = primary
        val seg = w * 0.35f
        val x = -seg + (w + seg) * t
        canvas.drawRect(x.coerceAtLeast(0f), 0f, (x + seg).coerceAtMost(w), h, paint)
    }
}
