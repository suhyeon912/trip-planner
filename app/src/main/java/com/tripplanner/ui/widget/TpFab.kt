package com.tripplanner.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import com.tripplanner.R

/**
 * 떠 있는 추가 버튼 (FAB · ExtendedFAB 대신): 모서리 16 · 높이 56 · 그림자.
 * 글자가 있으면 '+ 새 여행' 처럼 넓게, shrink() 하면 200ms 에 걸쳐 아이콘만 남는 정사각형으로.
 */
class TpFab @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    TpButton(context, attrs, R.attr.tpFabStyle) {

    var isExtended = true
        private set
    private var fullText: CharSequence? = null
    private var anim: ValueAnimator? = null

    init {
        // 그림자 모양을 버튼 모양과 똑같이 (동그란 버튼은 원, 넓은 버튼은 모서리 16):
        // 배경 모양에 맡기면 기기에 따라 네모난 그림자가 비쳐 보여서 직접 정함
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                val w = view.width; val h = view.height
                val oval = (view.background as? android.graphics.drawable.GradientDrawable)?.shape ==
                    android.graphics.drawable.GradientDrawable.OVAL
                if (oval) outline.setOval(0, 0, w, h)
                else outline.setRoundRect(0, 0, w, h, minOf(16 * resources.displayMetrics.density, h / 2f))
                outline.alpha = 1f
            }
        }
        // 부드러운 그림자: 검정 대신 진한 남색 기운, 퍼지는 그림자는 옅게
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            outlineSpotShadowColor = 0x660F131A
            outlineAmbientShadowColor = 0x330F131A
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateOutline()
    }

    fun shrink() = animateTo(false)
    fun extend() = animateTo(true)

    private fun animateTo(extended: Boolean) {
        if (extended == isExtended || text.isNullOrEmpty() && fullText == null) return
        isExtended = extended
        val size = (56 * resources.displayMetrics.density).toInt()
        if (!extended) fullText = text
        val from = width
        // 목표 폭: 접으면 56, 펴면 글자를 다시 넣고 잰 폭
        val to = if (extended) {
            text = fullText
            measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY))
            measuredWidth
        } else size
        anim?.cancel()
        anim = ValueAnimator.ofInt(from, to).apply {
            duration = resources.getInteger(R.integer.motion_medium).toLong()
            interpolator = AnimationUtils.loadInterpolator(context, R.interpolator.motion_standard)
            addUpdateListener { layoutParams = layoutParams.apply { width = it.animatedValue as Int }; requestLayout() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    if (!isExtended) text = ""
                    layoutParams = layoutParams.apply { width = ViewGroup.LayoutParams.WRAP_CONTENT }
                    if (!isExtended) { minWidth = size; minimumWidth = size }
                }
            })
            start()
        }
        if (!extended) { text = ""; contentDescription = fullText } else contentDescription = null
    }
}
