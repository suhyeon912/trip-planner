package com.tripplanner.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Application
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.tripplanner.R

/**
 * 앱 전체 공통 모션 (1단계)
 *  · 스크롤 끝 효과: Android 11 이하의 파란 빛번짐(글로우)은 끔. 12 이상의 늘어나는 효과는 그대로
 *  · 상단 바: 맨 위면 구분선 없음, 내려가면 1px 구분선이 150ms 에 걸쳐 나타남 (시안 CompScroll)
 * 화면마다 코드를 넣지 않도록 Activity 가 시작될 때 한 번 적용합니다.
 */
object Motion {

    fun register(app: Application) = app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) = apply(activity)
        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivityResumed(a: Activity) {
            // 화면이 뜬 뒤에 만들어진 카드까지 (예: 설정 목록)
            a.findViewById<ViewGroup>(android.R.id.content)?.let { r -> walk(r) { clipRoundedCard(it) } }
        }
        override fun onActivityPaused(a: Activity) {}
        override fun onActivityStopped(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(a: Activity) {}
    })

    private fun apply(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) walk(root) { v ->
            if (v is RecyclerView || v is NestedScrollView || v is ScrollView || v is HorizontalScrollView)
                v.overScrollMode = View.OVER_SCROLL_NEVER
        }
        walk(root) { v -> if (v is AppBarLayout) attachDivider(v, root) }
        walk(root) { v -> clipRoundedCard(v) }
    }

    /**
     * 둥근 카드 안의 누를 수 있는 줄: 눌림 덧칠이 카드 모서리 밖으로 삐져나오지 않게 카드 모양으로 자름.
     * (XML 의 clipToOutline 은 Android 12 이상에서만 적용되어 코드로 켬)
     */
    fun clipRoundedCard(v: View) {
        if (v !is android.widget.LinearLayout || v.clipToOutline) return
        val bg = v.background as? android.graphics.drawable.GradientDrawable ?: return
        // getCornerRadii() 는 Android 11 이하에서 값이 없으면 앱이 멈춤 → 감싸서 확인
        val rounded = bg.cornerRadius > 0f || runCatching { bg.cornerRadii?.any { it > 0f } == true }.getOrDefault(false)
        if (!rounded) return
        v.clipToOutline = true
    }

    private fun walk(v: View, f: (View) -> Unit) {
        f(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), f)
    }

    /** 상단 바 아래 1px 구분선: 목록이 맨 위가 아니면 보임 */
    private fun attachDivider(bar: AppBarLayout, root: ViewGroup) {
        if (bar.getTag(R.id.tag_scroll_divider) != null) return
        val target = bar.liftOnScrollTargetViewId.takeIf { it != View.NO_ID }?.let { root.findViewById<View>(it) }
            ?: run { var found: View? = null; walk(root) { if (found == null && (it is NestedScrollView || it is ScrollView)) found = it }; found }
            ?: return
        val line = BottomLine(ContextCompat.getColor(bar.context, R.color.outline_variant), bar.resources.displayMetrics.density)
        bar.foreground = line
        bar.setTag(R.id.tag_scroll_divider, line)
        var shown = false
        var anim: ValueAnimator? = null
        fun update() {
            val show = target.canScrollVertically(-1)
            if (show == shown) return
            shown = show
            anim?.cancel()
            anim = ValueAnimator.ofInt(line.alpha, if (show) 255 else 0).apply {
                duration = bar.resources.getInteger(R.integer.motion_short).toLong()
                interpolator = AnimationUtils.loadInterpolator(bar.context, R.interpolator.motion_standard)
                addUpdateListener { line.alpha = it.animatedValue as Int }
                start()
            }
        }
        when (target) {
            is RecyclerView -> target.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) = update()
            })
            else -> target.viewTreeObserver.addOnScrollChangedListener { update() }
        }
        // 목록 내용이 바뀌어 맨 위로 돌아간 경우도 반영
        target.viewTreeObserver.addOnGlobalLayoutListener { update() }
    }

    /** 아래쪽 1px 선만 그리는 그림 (투명도로 나타나고 사라짐) */
    private class BottomLine(color: Int, density: Float) : Drawable() {
        private val paint = Paint().apply { this.color = color }
        private val h = maxOf(1f, density)   // 1dp
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
