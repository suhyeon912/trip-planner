package com.tripplanner.ui

import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R

/**
 * 여행 상세 목록의 자리 이동 애니메이션.
 * 장소 줄이 자리를 바꿀 때 줄 전체(왼쪽 순서 점 포함)가 미끄러지는 대신,
 * 줄은 바로 새 자리에 두고 카드만 원래 자리에서 미끄러져 옴 → 순서 점은 제자리에 고정되어 보임.
 * 지금 손가락으로 끌고 있는 카드는 ItemTouchHelper 가 움직이므로 건드리지 않음.
 */
class CardOnlyMoveAnimator(private val dragging: () -> RecyclerView.ViewHolder?) : DefaultItemAnimator() {

    private val running = mutableSetOf<RecyclerView.ViewHolder>()

    override fun animateMove(holder: RecyclerView.ViewHolder, fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
        if (holder !is PlaceAdapter.PlaceHolder) return super.animateMove(holder, fromX, fromY, toX, toY)
        val view = holder.itemView
        val startY = fromY + view.translationY.toInt()
        view.translationY = 0f
        if (holder === dragging()) { dispatchMoveFinished(holder); return false }
        val delta = (startY - toY).toFloat()
        if (delta == 0f) { dispatchMoveFinished(holder); return false }
        val card = holder.card
        card.animate().cancel()
        card.translationY = delta + card.translationY
        running += holder
        dispatchMoveStarting(holder)
        card.animate().translationY(0f)
            .setDuration(view.resources.getInteger(R.integer.motion_medium).toLong())
            .setInterpolator(android.view.animation.AnimationUtils.loadInterpolator(view.context, R.interpolator.motion_standard))
            .withEndAction { running -= holder; dispatchMoveFinished(holder); if (!isRunning) dispatchAnimationsFinished() }
            .start()
        return false
    }

    override fun endAnimation(item: RecyclerView.ViewHolder) {
        if (item is PlaceAdapter.PlaceHolder && item in running) {
            item.card.animate().cancel(); item.card.translationY = 0f
            running -= item; dispatchMoveFinished(item)
        }
        super.endAnimation(item)
    }

    override fun endAnimations() {
        running.toList().forEach { endAnimation(it) }
        super.endAnimations()
    }

    override fun isRunning() = running.isNotEmpty() || super.isRunning()
}
