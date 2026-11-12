package com.tripplanner.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View

/**
 * 하나만 고르는 칩 줄 (ChipGroup 대신 — 장소 종류). 칩은 체크 가능한 TpButton.
 * 고른 칩을 다시 눌러도 풀리지 않음.
 */
class TpChipGroup @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FlowLayout(context, attrs) {

    var checkedId: Int = View.NO_ID
        private set
    /** 고른 칩이 바뀔 때 */
    var onCheckedChange: ((Int) -> Unit)? = null

    fun check(id: Int) {
        checkedId = id
        for (i in 0 until childCount) (getChildAt(i) as? TpButton)?.let { it.isChecked = it.id == id }
        onCheckedChange?.invoke(id)
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        val b = child as? TpButton ?: return
        b.isCheckable = true
        b.isChecked = b.id == checkedId
        b.setOnClickListener { if (b.id != checkedId) check(b.id) else b.isChecked = true }
    }
}
