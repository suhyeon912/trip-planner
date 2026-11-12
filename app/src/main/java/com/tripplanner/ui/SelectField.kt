package com.tripplanner.ui

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.tripplanner.R

/**
 * 시안 SelectField: 누르면 메뉴·달력·다른 화면이 열리는 칸 (직접 입력 없음).
 * 칸 52 · 모서리 12 · 왼쪽 아이콘 · 값 · 오른쪽 ▾ 또는 › · 오류는 빨간 테두리 + 칸 아래 한 줄.
 */
class SelectField @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    private val box: View
    private val icon: ImageView
    private val value: TextView
    private val end: ImageView
    private val errorText: TextView
    private var placeholder: CharSequence = ""
    /** 칸 이름 (TalkBack 이 '이름, 값'으로 읽음) */
    var label: CharSequence = ""
        set(v) { field = v; updateA11y() }

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_select_field, this, true)
        box = findViewById(R.id.selectBox)
        icon = findViewById(R.id.selectIcon)
        value = findViewById(R.id.selectValue)
        end = findViewById(R.id.selectEnd)
        errorText = findViewById(R.id.selectError)
    }

    /** 촘촘한 칸 (높이 48 · 왼쪽 12) — 장소 추가의 일차 칸 */
    fun setCompact() {
        val d = resources.displayMetrics.density
        box.minimumHeight = (48 * d).toInt()
        box.setPaddingRelative((12 * d).toInt(), box.paddingTop, box.paddingEnd, box.paddingBottom)
    }

    fun setIcon(res: Int?) {
        icon.visibility = if (res == null) GONE else VISIBLE
        res?.let { icon.setImageResource(it) }
    }

    /** true = ▾ (같은 자리 아래에 목록), false = › (다른 화면·달력) */
    fun setDropdown(dropdown: Boolean) {
        end.setImageResource(if (dropdown) R.drawable.ic_expand_more else R.drawable.ic_chevron_right)
    }

    fun setPlaceholder(text: CharSequence) { placeholder = text; if (value.text.isNullOrEmpty() || value.tag == "ph") setValue(null) }

    fun setValue(text: CharSequence?) {
        if (text.isNullOrEmpty()) {
            value.text = placeholder; value.tag = "ph"
            value.setTextColor(context.color(R.color.outline))
        } else {
            value.text = text; value.tag = null
            value.setTextColor(context.color(R.color.on_surface))
        }
        updateA11y()
    }

    fun setError(message: CharSequence?) {
        errorText.text = message
        errorText.visibility = if (message == null) GONE else VISIBLE
        box.backgroundTintList = null
        box.isActivated = message != null
        updateA11y()
    }

    override fun setOnClickListener(l: OnClickListener?) { box.setOnClickListener(l) }

    private fun updateA11y() {
        val v = if (value.tag == "ph") "" else value.text
        box.contentDescription = listOf(label, v, errorText.text.takeIf { errorText.visibility == VISIBLE } ?: "")
            .filter { it.isNotEmpty() }.joinToString(", ")
    }

    init { setValue(null); icon.imageTintList = ColorStateList.valueOf(context.color(R.color.text_sub)) }
}
