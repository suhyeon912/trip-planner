package com.tripplanner.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.tripplanner.R

/**
 * TripPlanner 버튼 (MaterialButton · FAB 대신).
 * 모양(바탕·테두리·글자색·눌림 덧칠)은 스타일(Widget.TripPlanner.Button.*)이 정하고,
 * 이 클래스는 아이콘만 그립니다: 왼쪽 끝(start) 또는 글자 바로 앞(textStart), 글자가 없으면 가운데.
 * 체크 상태(세그먼트 칸)도 지원 — 바탕·글자색 selector 의 state_checked 로 표시.
 */
open class TpButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.tpButtonStyle
) : AppCompatButton(context, attrs, defStyleAttr), android.widget.Checkable {

    var icon: Drawable? = null
        set(v) { field = v?.mutate(); applyTint(); requestLayout(); invalidate() }
    var iconSize: Int = 0
        set(v) { field = v; requestLayout(); invalidate() }
    var iconPadding: Int = 0
        set(v) { field = v; invalidate() }
    var iconTint: ColorStateList? = null
        set(v) { field = v; applyTint(); invalidate() }
    var iconGravity: Int = ICON_GRAVITY_TEXT_START
        set(v) { field = v; invalidate() }

    var isCheckable = false
    private var checked = false

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.TpButton, defStyleAttr, 0)
        val d = resources.displayMetrics.density
        iconSize = a.getDimensionPixelSize(R.styleable.TpButton_tpIconSize, (20 * d).toInt())
        iconPadding = a.getDimensionPixelSize(R.styleable.TpButton_tpIconPadding, (8 * d).toInt())
        iconGravity = a.getInt(R.styleable.TpButton_tpIconGravity, ICON_GRAVITY_TEXT_START)
        iconTint = if (a.hasValue(R.styleable.TpButton_tpIconTint)) a.getColorStateList(R.styleable.TpButton_tpIconTint) else null
        a.getResourceId(R.styleable.TpButton_tpIcon, 0).takeIf { it != 0 }?.let { icon = ContextCompat.getDrawable(context, it) }
        a.recycle()
        isAllCaps = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    fun setIconResource(res: Int) { icon = if (res == 0) null else ContextCompat.getDrawable(context, res) }

    /** 아이콘 색: 지정이 없으면 글자색을 따름 (null 을 지정한 원본 색 아이콘은 tint 하지 않음) */
    private var keepIconColor = false
    fun keepOriginalIconColor() { keepIconColor = true; applyTint() }

    private fun applyTint() {
        val ic = icon ?: return
        if (keepIconColor) { DrawableCompat.setTintList(ic, null); return }
        DrawableCompat.setTintList(ic, iconTint ?: textColors)
    }

    override fun setTextColor(colors: ColorStateList?) { super.setTextColor(colors); applyTint() }
    override fun setTextColor(color: Int) { super.setTextColor(color); applyTint() }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        icon?.let { if (it.isStateful) it.state = drawableState }
    }

    /** 아이콘 자리를 글자 앞 여백으로 잡아 둠 (내용 폭 버튼도 아이콘이 들어갈 만큼 넓어짐) */
    private fun iconSpace() = if (icon != null && !text.isNullOrEmpty()) iconSize + iconPadding else 0
    override fun getCompoundPaddingLeft() = super.getCompoundPaddingLeft() + iconSpace()

    override fun onDraw(canvas: Canvas) {
        val ic = icon
        if (ic != null) {
            val top = (height - iconSize) / 2
            val left = if (text.isNullOrEmpty()) (width - iconSize) / 2
            else if (iconGravity == ICON_GRAVITY_START) paddingLeft
            else {
                // 글자 바로 앞: 글자가 실제로 그려지는 왼쪽 끝에서 아이콘 몫만큼 앞
                val avail = width - compoundPaddingLeft - compoundPaddingRight
                val lineW = layout?.getLineWidth(0) ?: paint.measureText(text.toString())
                val textLeft = compoundPaddingLeft + when (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
                    Gravity.CENTER_HORIZONTAL -> ((avail - lineW) / 2f).coerceAtLeast(0f)
                    Gravity.RIGHT, Gravity.END -> (avail - lineW).coerceAtLeast(0f)
                    else -> 0f
                }
                (textLeft - iconPadding - iconSize).toInt()
            }
            ic.setBounds(left, top, left + iconSize, top + iconSize)
            ic.draw(canvas)
        }
        super.onDraw(canvas)
    }

    // ---- 체크 (세그먼트 칸) ----
    override fun isChecked() = checked
    override fun setChecked(c: Boolean) {
        if (!isCheckable || checked == c) return
        checked = c
        refreshDrawableState()
    }
    override fun toggle() { isChecked = !checked }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val s = super.onCreateDrawableState(extraSpace + 1)
        if (checked) mergeDrawableStates(s, intArrayOf(android.R.attr.state_checked))
        return s
    }

    override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (isCheckable) { info.isCheckable = true; info.isChecked = checked }
        info.className = android.widget.Button::class.java.name
    }

    companion object {
        const val ICON_GRAVITY_START = 0
        const val ICON_GRAVITY_TEXT_START = 1
    }
}
