package com.tripplanner.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tripplanner.R
import com.tripplanner.data.TripRepository
import com.tripplanner.ui.widget.TpSnackbar
import kotlinx.coroutines.launch

/** 지운 뒤 아래 알림: "○○ 삭제했어요 · 실행취소" (알림이 사라지면 되돌릴 수 없음) */
fun AppCompatActivity.showUndo(message: String, undo: TripRepository.Undo) {
    TpSnackbar.show(this, message, action = getString(R.string.undo)) {
        lifecycleScope.launch { undo.undo() }
    }
}

/** 다른 화면에서 지운 뒤 돌아온 화면에 '실행취소'를 넘겨주는 곳 (장소 정보 → 여행 상세) */
object PendingUndo {
    private var last: Pair<String, TripRepository.Undo>? = null
    fun put(message: String, undo: TripRepository.Undo) { last = message to undo }
    fun showIn(activity: AppCompatActivity) { last?.let { (m, u) -> last = null; activity.showUndo(m, u) } }
}
