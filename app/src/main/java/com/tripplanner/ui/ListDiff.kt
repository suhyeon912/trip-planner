package com.tripplanner.ui

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView


/**
 * 목록 바꾸기 (notifyDataSetChanged 대신).
 * 같은 항목(id)은 제자리에서 내용만 고쳐 그리고, 새 항목은 나타나고, 지운 항목은 사라지고, 순서가 바뀐 항목은 미끄러짐.
 * 내용만 바뀐 줄은 겹쳐 사라지는(crossfade) 효과 없이 그 자리에서 바로 다시 그림 (PAYLOAD_CONTENT).
 */
@android.annotation.SuppressLint("NotifyDataSetChanged")
object ListDiff {
    /** 내용만 바뀜 → 같은 ViewHolder 에 다시 그리기 */
    const val PAYLOAD_CONTENT = "content"

    fun <T> dispatch(
        adapter: RecyclerView.Adapter<*>,
        old: List<T>, new: List<T>,
        sameItem: (T, T) -> Boolean,
        /** 맨 위 줄만 다르게 그리는 목록(구분선 숨김 등)이면 true → 첫 자리가 바뀐 줄도 다시 그림 */
        firstMatters: Boolean = false
    ) {
        // 처음 채울 때(화면이 열릴 때)는 화면 전환 효과가 이미 있으므로 하나씩 나타나지 않고 바로 보임
        if (old.isEmpty()) { adapter.notifyDataSetChanged(); return }
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = new.size
            override fun areItemsTheSame(o: Int, n: Int) = sameItem(old[o], new[n])
            override fun areContentsTheSame(o: Int, n: Int) =
                old[o] == new[n] && (!firstMatters || (o == 0) == (n == 0))
            override fun getChangePayload(o: Int, n: Int): Any = PAYLOAD_CONTENT
        }).dispatchUpdatesTo(adapter)
    }
}
