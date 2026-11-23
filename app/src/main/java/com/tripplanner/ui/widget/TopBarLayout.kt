package com.tripplanner.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R

/**
 * 상단 바 묶음 (AppBarLayout 대신): 상단 바 · 탭 등을 세로로.
 * tpScrollTarget 목록이 맨 위가 아니면 바로 아래(목록 위)에 바탕색 → 투명 그라데이션(16dp)이 150ms 에 걸쳐 나타남.
 * 목록이 상단 바 밑으로 스르르 사라져 보이게 (예전 1px 구분선 대신).
 */
class TopBarLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    private val targetId: Int
    private val line = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
        ContextCompat.getColor(context, R.color.background).let { intArrayOf(it, it and 0x00FFFFFF) }).apply { alpha = 0 }
    private val fadeH = (16 * resources.displayMetrics.density).toInt()
    private var shown = false
    private var anim: ValueAnimator? = null
    private var target: View? = null

    init {
        orientation = VERTICAL
        val a = context.obtainStyledAttributes(attrs, R.styleable.TopBarLayout)
        targetId = a.getResourceId(R.styleable.TopBarLayout_tpScrollTarget, View.NO_ID)
        a.recycle()
        if (background == null) setBackgroundColor(ContextCompat.getColor(context, R.color.background))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (target != null || targetId == View.NO_ID) return
        val t = rootView.findViewById<View>(targetId) ?: return
        target = t
        when (t) {
            is RecyclerView -> {
                t.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) = update()
                })
                // 목록 맨 위에 덧그림 (카드 위로)
                t.addItemDecoration(object : RecyclerView.ItemDecoration() {
                    override fun onDrawOver(c: android.graphics.Canvas, rv: RecyclerView, state: RecyclerView.State) {
                        if (line.alpha == 0) return
                        line.setBounds(0, 0, rv.width, fadeH); line.draw(c)
                    }
                })
            }
            else -> {
                // 스크롤 칸: 칸의 foreground 로 지금 보이는 맨 위에 그림 (내용과 함께 밀리지 않게 scrollY 만큼 내려 그림)
                t.foreground = object : android.graphics.drawable.Drawable() {
                    override fun draw(c: android.graphics.Canvas) {
                        if (line.alpha == 0) return
                        line.setBounds(0, t.scrollY, bounds.width(), t.scrollY + fadeH); line.draw(c)
                    }
                    override fun setAlpha(alpha: Int) {}
                    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
                    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
                }
                t.viewTreeObserver.addOnScrollChangedListener { update(); t.invalidate() }
            }
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
            addUpdateListener { line.alpha = it.animatedValue as Int; target?.invalidate() }
            start()
        }
    }
}
