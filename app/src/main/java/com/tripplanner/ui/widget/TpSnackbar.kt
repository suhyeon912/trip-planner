package com.tripplanner.ui.widget

import android.app.Activity
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.TextView
import com.tripplanner.R

/**
 * 결과 알림 (Snackbar 대신, 시안 Snackbar).
 * 화면 아래 24 · 좌우 16 · 어두운 바탕 · 글자 (필요하면 오른쪽에 글자 버튼 하나).
 * 8dp 아래에서 올라오며 나타나고, 3초 (버튼이 있으면 5초) 뒤 흐려지며 사라짐.
 * above 를 주면 그 뷰(하단 버튼 바 등) 바로 위에 띄움.
 */
object TpSnackbar {

    private const val TAG = "tp_snackbar"

    fun show(activity: Activity, message: CharSequence, above: View? = null,
             action: CharSequence? = null, onAction: (() -> Unit)? = null) {
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        content.findViewWithTag<View>(TAG)?.let { content.removeView(it) }
        val bar = LayoutInflater.from(activity).inflate(R.layout.view_tp_snackbar, content, false)
        bar.tag = TAG
        bar.findViewById<TextView>(R.id.snackText).text = message
        val btn = bar.findViewById<TextView>(R.id.snackAction)
        if (action != null) {
            btn.text = action; btn.visibility = View.VISIBLE
            btn.setOnClickListener { onAction?.invoke(); hide(content, bar) }
        }
        val d = activity.resources.displayMetrics.density
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        val side = (16 * d).toInt()
        lp.setMargins(side, 0, side, if (above != null && above.isShown) aboveOffset(content, above) + (8 * d).toInt() else (24 * d).toInt())
        content.addView(bar, lp)
        bar.alpha = 0f; bar.translationY = 8 * d
        bar.animate().alpha(1f).translationY(0f)
            .setDuration(activity.resources.getInteger(R.integer.motion_medium).toLong())
            .setInterpolator(AnimationUtils.loadInterpolator(activity, R.interpolator.motion_decelerate)).start()
        bar.announceForAccessibility(message)
        bar.postDelayed({ hide(content, bar) }, if (action != null) 5000L else 3000L)
    }

    /** above 뷰 윗변이 화면 아래에서 얼마나 떨어져 있는지 */
    private fun aboveOffset(content: View, above: View): Int {
        val c = IntArray(2).also { content.getLocationInWindow(it) }
        val a = IntArray(2).also { above.getLocationInWindow(it) }
        return (c[1] + content.height) - a[1]
    }

    private fun hide(parent: ViewGroup, bar: View) {
        if (bar.parent == null) return
        bar.animate().alpha(0f).setDuration(bar.resources.getInteger(R.integer.motion_short).toLong())
            .setInterpolator(AnimationUtils.loadInterpolator(bar.context, R.interpolator.motion_accelerate))
            .withEndAction { parent.removeView(bar) }.start()
    }
}
