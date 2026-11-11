package com.tripplanner.ui

import android.view.View
import android.widget.Button
import android.widget.TextView
import com.tripplanner.R

/** 빈 화면에 보여줄 내용. action 은 버튼 라벨 + 눌렀을 때 할 일의 종류 */
data class EmptyState(
    val title: String,
    val body: String,
    val actionLabel: String? = null,
    val action: Action? = null,
    /** 가운데 동그라미 안 아이콘 */
    val icon: Int = R.drawable.ic_place
) {
    enum class Action { SHOW_CANDIDATES, ADD_PLACE, GO_BACK, NEW_TRIP }
}

/** view_empty_state.xml 을 채우거나(state != null) 숨김(state == null) */
fun bindEmptyState(root: View, state: EmptyState?, onAction: (EmptyState.Action) -> Unit) {
    root.visibility = if (state == null) View.GONE else View.VISIBLE
    if (state == null) return
    root.findViewById<TextView>(R.id.emptyTitle).text = state.title
    root.findViewById<TextView>(R.id.emptyBody).text = state.body
    root.findViewById<android.widget.ImageView>(R.id.emptyIcon).setImageResource(state.icon)
    root.findViewById<Button>(R.id.emptyAction).apply {
        // 버튼이 없는 빈 상태도 있음 (예: 메인 — 새 여행은 FAB)
        visibility = if (state.actionLabel == null) View.GONE else View.VISIBLE
        text = state.actionLabel
        setOnClickListener { state.action?.let(onAction) }
    }
}
