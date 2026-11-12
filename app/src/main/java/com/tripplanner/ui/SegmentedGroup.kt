package com.tripplanner.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton

/**
 * 시안 세그먼트 (바탕 surface_container · 안쪽 4 · 고른 칸 card_bg · 모서리 10).
 * MaterialButtonToggleGroup 은 가운데 버튼의 모서리를 0 으로 바꿔 시안과 달라서,
 * 같은 사용법(check · checkedButtonId · addOnButtonCheckedListener)의 단순한 묶음으로 대신합니다.
 * 하나만 고를 수 있고, 고른 칸을 다시 눌러도 풀리지 않습니다.
 */
class SegmentedGroup @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    fun interface OnButtonCheckedListener {
        fun onButtonChecked(group: SegmentedGroup, checkedId: Int, isChecked: Boolean)
    }

    private val listeners = mutableListOf<OnButtonCheckedListener>()

    init { orientation = HORIZONTAL }

    val checkedButtonId: Int
        get() = buttons().firstOrNull { it.isChecked }?.id ?: View.NO_ID

    fun addOnButtonCheckedListener(l: OnButtonCheckedListener) { listeners += l }

    fun check(id: Int) {
        val prev = checkedButtonId
        if (prev == id) return
        buttons().forEach { it.isChecked = it.id == id }
        if (prev != View.NO_ID) listeners.forEach { it.onButtonChecked(this, prev, false) }
        listeners.forEach { it.onButtonChecked(this, id, true) }
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        val b = child as? MaterialButton ?: return
        b.isCheckable = true
        // 칸 사이 4 (시안 gap 4)
        if (childCount > 1) (b.layoutParams as? MarginLayoutParams)?.marginStart = (4 * resources.displayMetrics.density).toInt()
        b.setOnClickListener { check(b.id) }
    }

    private fun buttons() = (0 until childCount).mapNotNull { getChildAt(it) as? MaterialButton }
}
