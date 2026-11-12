package com.tripplanner.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R

/**
 * 상단 바 묶음 (AppBarLayout 대신): 상단 바 · 탭 등을 세로로.
 * tpScrollTarget 목록이 맨 위가 아니면 아래 1px 구분선이 150ms 에 걸쳐 나타남 (시안 CompScroll).
 */
class TopBarLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    private val targetId: Int
    private val line = BottomLine(ContextCompat.getColor(context, R.color.outline_variant), resources.displayMetrics.density)
    private var shown = false
    private var anim: ValueAnimator? = null
    private var target: View? = null

    init {
        orientation = VERTICAL
        val a = context.obtainStyledAttributes(attrs, R.styleable.TopBarLayout)
        targetId = a.getResourceId(R.styleable.TopBarLayout_tpScrollTarget, View.NO_ID)
        a.recycle()
        foreground = line
        if (background == null) setBackgroundColor(ContextCompat.getColor(context, R.color.background))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (target != null || targetId == View.NO_ID) return
        val t = rootView.findViewById<View>(targetId) ?: return
        target = t
        when (t) {
            is RecyclerView -> t.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) = update()
            })
            else -> t.viewTreeObserver.addOnScrollChangedListener { update() }
        }
        t.viewTreeObserver.addOnGlobalLayoutListener { update() }
    }

    private fun update() {
        val show = target?.let { it.isShown && it.canScrollVertically(-1) } == true
        if (show == shown) return
        shown = show
        anim?.cancel()
        anim = ValueAnimator.ofInt(line.alpha, if (show) 255 else 0).apply {
            duration = resources.getInteger(R.integer.motion_short).toLong()
            interpolator = AnimationUtils.loadInterpolator(context, R.interpolator.motion_standard)
            addUpdateListener { line.alpha = it.animatedValue as Int }
            start()
        }
    }

    /** 아래쪽 1px 선 (투명도로 나타나고 사라짐) */
    private class BottomLine(color: Int, density: Float) : Drawable() {
        private val paint = Paint().apply { this.color = color }
        private val h = maxOf(1f, density)
        private var a = 0
        override fun draw(canvas: Canvas) {
            if (a == 0) return
            paint.alpha = a
            canvas.drawRect(bounds.left.toFloat(), bounds.bottom - h, bounds.right.toFloat(), bounds.bottom.toFloat(), paint)
        }
        override fun setAlpha(alpha: Int) { a = alpha; invalidateSelf() }
        override fun getAlpha() = a
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
