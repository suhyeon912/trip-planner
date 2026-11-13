package com.tripplanner.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import com.tripplanner.R

/**
 * 짧은 진동 (시스템 '터치 진동' 설정을 따름 — 꺼 두면 울리지 않음).
 *  · confirm: 확정 · 적용 · 팝업 확인
 *  · delete: 삭제 확인 (조금 더 묵직하게)
 *  · reject: 입력 오류가 처음 보일 때
 *  · dragEnd: 끌던 카드를 놓을 때
 * 길게 누르기는 안드로이드가 이미 진동을 주므로 따로 하지 않음.
 */
object Haptics {
    fun confirm(v: View?) = v?.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)

    fun delete(v: View?) = v?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

    fun reject(v: View?) = v?.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)

    fun dragEnd(v: View?) = v?.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.CLOCK_TICK)
}

/**
 * 글자(주로 시각 · 숫자)가 바뀔 때 툭 바뀌지 않고 흐려졌다 새 값으로 나타남 (100ms 사라짐 + 150ms 나타남).
 * 화면에 아직 안 보이거나 같은 글자면 바로 넣음. 연달아 바뀌면 마지막 값으로.
 */
fun TextView.setTextFading(value: CharSequence?) {
    val target = value ?: ""
    val pending = getTag(R.id.tag_fade_target) as CharSequence?
    if (pending == null && text.toString() == target.toString()) return
    if (pending != null && pending.toString() == target.toString()) return
    if (!isLaidOut || !isShown) {
        animate().cancel(); alpha = 1f; setTag(R.id.tag_fade_target, null); text = target; return
    }
    setTag(R.id.tag_fade_target, target)
    animate().cancel()
    val ctx = context
    animate().alpha(0f)
        .setDuration(ctx.resources.getInteger(R.integer.motion_press_exit).toLong())
        .setInterpolator(AnimationUtils.loadInterpolator(ctx, R.interpolator.motion_accelerate))
        .withEndAction {
            text = getTag(R.id.tag_fade_target) as CharSequence? ?: text
            setTag(R.id.tag_fade_target, null)
            animate().alpha(1f)
                .setDuration(ctx.resources.getInteger(R.integer.motion_short).toLong())
                .setInterpolator(AnimationUtils.loadInterpolator(ctx, R.interpolator.motion_decelerate))
                .start()
        }.start()
}

/** 흐려지는 중이던 효과를 멈추고 바로 넣기 (목록 칸이 다른 항목으로 재사용될 때) */
fun TextView.setTextNow(value: CharSequence?) {
    animate().cancel(); alpha = 1f; setTag(R.id.tag_fade_target, null); text = value
}
