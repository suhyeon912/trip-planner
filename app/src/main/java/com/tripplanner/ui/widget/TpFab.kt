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
