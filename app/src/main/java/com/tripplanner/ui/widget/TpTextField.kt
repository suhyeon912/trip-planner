package com.tripplanner.ui.widget

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.tripplanner.R

/**
 * TripPlanner 입력칸 (TextInputLayout 대신, 시안 TextField).
 * 라벨은 칸 밖 위(Widget.TripPlanner.FieldLabel)에 따로 두고, 이 칸은 테두리 상자 + 오류 한 줄입니다.
 *  · 기본: 높이 52 · 좌우 14 / 촘촘(tpCompact): 높이 48 · 좌우 12 · 모서리 12
 *  · 테두리: 평소 1 outline · 입력 중 2 primary · 오류 2 빨강
 *  · 끝 아이콘(tpEndIcon): 예) 주소 검색
 * XML 안에 EditText 하나를 넣어 씁니다.
 */
class TpTextField @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    private val dp = resources.displayMetrics.density
    private val box = FrameLayout(context)
    private val endIcon = ImageButton(context)
    private val startIcon = android.widget.ImageView(context)
    private val errorView = TextView(context)
    private val compact: Boolean
    private var focused = false
    private var pendingPlaceholder: String? = null

    /** 안의 입력칸 */
    var editText: EditText? = null
        private set

    init {
        orientation = VERTICAL
        val a = context.obtainStyledAttributes(attrs, R.styleable.TpTextField)
        compact = a.getBoolean(R.styleable.TpTextField_tpCompact, false)
        val placeholder = a.getString(R.styleable.TpTextField_tpPlaceholder)
        val icon = a.getResourceId(R.styleable.TpTextField_tpEndIcon, 0)
        val iconDesc = a.getString(R.styleable.TpTextField_tpEndIconDescription)
        val start = a.getResourceId(R.styleable.TpTextField_tpStartIcon, 0)
        a.recycle()

        super.addView(box, -1, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        endIcon.apply {
            background = ContextCompat.getDrawable(context, R.drawable.press_overlay_borderless)
            setColorFilter(ContextCompat.getColor(context, R.color.text_sub))
            val p = (12 * dp).toInt(); setPadding(p, p, p, p)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            visibility = GONE
        }
        box.addView(endIcon, FrameLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt(), Gravity.END or Gravity.CENTER_VERTICAL))
        // 앞 아이콘 (예: 기간 칸의 달력) — 장식이라 읽지 않음
        startIcon.apply {
            setColorFilter(ContextCompat.getColor(context, R.color.text_sub))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = if (start != 0) VISIBLE else GONE
            if (start != 0) setImageResource(start)
        }
        box.addView(startIcon, FrameLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt(), Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = (14 * dp).toInt() })
        errorView.apply {
            setTextAppearance(R.style.TextAppearance_TripPlanner_BodySmall)
            setTextColor(ContextCompat.getColor(context, R.color.badge_red))
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), 0, 0)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = GONE
        }
        super.addView(errorView, -1, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        if (icon != 0) setEndIcon(icon, iconDesc, null)
        pendingPlaceholder = placeholder
        updateBox()
    }


    /** XML 의 자식(EditText)은 상자 안으로 */
    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (child is EditText && editText == null) {
            editText = child
            child.background = null
            val h = ((if (compact) 12 else 14) * dp).toInt()
            val v = ((if (compact) 12 else 14) * dp).toInt()
            child.setPaddingRelative(h, v, h, v)
            child.minHeight = ((if (compact) 48 else 52) * dp).toInt()
            if (child.textSize == 0f) child.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            pendingPlaceholder?.let { if (child.hint.isNullOrEmpty()) child.hint = it }
            box.addView(child, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            return
        }
        super.addView(child, index, params)
    }

    // 입력 중 테두리 (다른 코드의 포커스 리스너와 겹치지 않게 전역 감시)
    private val focusWatch = ViewTreeObserver.OnGlobalFocusChangeListener { _, _ ->
        val f = editText?.hasFocus() == true
        if (f != focused) { focused = f; updateBox() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); viewTreeObserver.addOnGlobalFocusChangeListener(focusWatch) }
    override fun onDetachedFromWindow() { viewTreeObserver.removeOnGlobalFocusChangeListener(focusWatch); super.onDetachedFromWindow() }

    /** 오류 글자 (null 이면 숨김) */
    var error: CharSequence? = null
        set(v) {
            // 오류가 새로 나타나면 짧게 '아니요' 진동
            if (field.isNullOrEmpty() && !v.isNullOrEmpty()) com.tripplanner.ui.Haptics.reject(this)
            field = v
            errorView.text = v
            errorView.visibility = if (v.isNullOrEmpty()) GONE else VISIBLE
            editText?.let { ViewCompat.setStateDescription(it, v) }
            updateBox()
        }

    /** 칸 안의 흐린 안내 글자 */
    var placeholderText: CharSequence?
        get() = editText?.hint
        set(v) { editText?.hint = v }

    /** 끝 아이콘 (null 이면 숨김) */
    fun setEndIcon(res: Int, description: CharSequence?, onClick: ((View) -> Unit)?) {
        endIcon.setImageResource(res)
        endIcon.contentDescription = description
        endIcon.visibility = VISIBLE
        onClick?.let { c -> endIcon.setOnClickListener { c(it) } }
        val e = editText ?: return
        e.setPaddingRelative(e.paddingStart, e.paddingTop, (48 * dp).toInt(), e.paddingBottom)
    }
    fun setEndIconOnClickListener(onClick: (View) -> Unit) = endIcon.setOnClickListener { onClick(it) }

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (startIcon.visibility == VISIBLE) editText?.let { it.setPaddingRelative((44 * dp).toInt(), it.paddingTop, it.paddingEnd, it.paddingBottom) }
        // 끝 아이콘이 있으면 글자가 아이콘 밑으로 가지 않게
        if (endIcon.visibility == VISIBLE) editText?.let { it.setPaddingRelative(it.paddingStart, it.paddingTop, (48 * dp).toInt(), it.paddingBottom) }
    }

    private fun updateBox() {
        val c = context
        val color = when {
            !error.isNullOrEmpty() -> R.color.badge_red
            focused -> R.color.primary
            else -> R.color.outline
        }
        box.background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(c, R.color.card_bg))
            cornerRadius = 12 * dp
            setStroke(((if (focused || !error.isNullOrEmpty()) 2 else 1) * dp).toInt(), ContextCompat.getColor(c, color))
        }
    }
}
