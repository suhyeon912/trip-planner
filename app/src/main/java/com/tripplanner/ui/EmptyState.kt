package com.tripplanner.ui

import android.view.View
import android.widget.Button
import android.widget.TextView
import com.tripplanner.R

/** 빈 화면에 보여줄 내용. action 은 버튼 라벨 + 눌렀을 때 할 일의 종류 */
data class EmptyState(
    val title: String,
    val body: String,
    val actionLabel: String,
    val action: Action
) {
    enum class Action { SHOW_CANDIDATES, ADD_PLACE, GO_BACK, NEW_TRIP }
}

/** view_empty_state.xml 을 채우거나(state != null) 숨김(state == null) */
fun bindEmptyState(root: View, state: EmptyState?, onAction: (EmptyState.Action) -> Unit) {
    root.visibility = if (state == null) View.GONE else View.VISIBLE
    if (state == null) return
    root.findViewById<TextView>(R.id.emptyTitle).text = state.title
    root.findViewById<TextView>(R.id.emptyBody).text = state.body
    root.findViewById<Button>(R.id.emptyAction).apply {
        text = state.actionLabel
        setOnClickListener { onAction(state.action) }
    }
}
