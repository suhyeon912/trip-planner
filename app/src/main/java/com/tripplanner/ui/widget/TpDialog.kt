package com.tripplanner.ui.widget

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tripplanner.R

/**
 * 팝업 (MaterialAlertDialog 대신, 시안 Dialog).
 * 가운데 · 좌우 24 · 모서리 24 · 바탕 45% 어둡게 · 버튼은 오른쪽 (왼쪽 끝은 보조 버튼).
 * 나타날 때 0.96 → 1 로 커지며 200ms, 사라질 때 150ms 흐려짐.
 * danger = true 면 확인 버튼이 빨강 채움 (되돌릴 수 없는 일).
 *
 * 쓰는 법은 AlertDialog.Builder 와 같음: TpDialog.Builder(ctx).setTitle(..).setPositiveButton(..).show()
 */
class TpDialog private constructor(context: Context, private val b: Builder) :
    Dialog(context, R.style.Theme_TripPlanner_Popup) {

    private val buttons = HashMap<Int, TpButton>()

    /** AlertDialog.getButton 과 같음 (BUTTON_POSITIVE / NEGATIVE / NEUTRAL) */
    fun getButton(which: Int): TpButton? = buttons[which]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LayoutInflater.from(context).inflate(R.layout.view_tp_dialog, null)
        setContentView(root)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val dm = context.resources.displayMetrics
            val w = minOf(dm.widthPixels - (48 * dm.density).toInt(), (560 * dm.density).toInt())
            setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.45f)
            setWindowAnimations(R.style.Animation_TripPlanner_Dialog)
        }
        setCancelable(b.cancelable)
        setCanceledOnTouchOutside(b.cancelable)
        b.onDismiss?.let { setOnDismissListener(it) }

        val title = root.findViewById<TextView>(R.id.dlgTitle)
        title.text = b.title; title.visibility = if (b.title.isNullOrEmpty()) View.GONE else View.VISIBLE
        val msg = root.findViewById<TextView>(R.id.dlgMessage)
        msg.text = b.message; msg.visibility = if (b.message.isNullOrEmpty()) View.GONE else View.VISIBLE
        if (b.title.isNullOrEmpty() && !b.message.isNullOrEmpty()) msg.setTextColor(ContextCompat.getColor(context, R.color.on_surface))

        // 직접 넣는 내용 (입력칸 등) — 내용 칸은 좌우 여백 없이 받아 각자 24 를 줌
        val custom = root.findViewById<FrameLayout>(R.id.dlgCustom)
        b.view?.let { v -> (v.parent as? ViewGroup)?.removeView(v); custom.addView(v); custom.visibility = View.VISIBLE }

        // 목록 (고르면 바로 닫힘)
        val list = root.findViewById<LinearLayout>(R.id.dlgItems)
        b.items?.let { items ->
            list.visibility = View.VISIBLE
            items.forEachIndexed { i, label ->
                val row = LayoutInflater.from(context).inflate(R.layout.item_select_option, list, false) as TextView
                row.text = label
                row.setOnClickListener { b.onItem?.invoke(this, i); dismiss() }
                list.addView(row)
            }
        }
        root.findViewById<ScrollView>(R.id.dlgScroll).isVerticalScrollBarEnabled = true

        fun bind(which: Int, id: Int, spec: Builder.ButtonSpec?) {
            val btn = root.findViewById<TpButton>(id)
            if (spec == null) { btn.visibility = View.GONE; return }
            btn.text = spec.text
            btn.visibility = View.VISIBLE
            btn.setOnClickListener { spec.onClick?.invoke(this, which); dismiss() }
            buttons[which] = btn
        }
        bind(DialogInterface.BUTTON_POSITIVE, if (b.danger) R.id.dlgPositiveDanger else R.id.dlgPositive, b.positive)
        root.findViewById<View>(if (b.danger) R.id.dlgPositive else R.id.dlgPositiveDanger).visibility = View.GONE
        bind(DialogInterface.BUTTON_NEGATIVE, R.id.dlgNegative, b.negative)
        bind(DialogInterface.BUTTON_NEUTRAL, R.id.dlgNeutral, b.neutral)
        val any = b.positive != null || b.negative != null || b.neutral != null
        root.findViewById<View>(R.id.dlgButtons).visibility = if (any) View.VISIBLE else View.GONE
    }

    class Builder(private val context: Context, internal val danger: Boolean = false) {
        internal class ButtonSpec(val text: CharSequence, val onClick: ((DialogInterface, Int) -> Unit)?)
        internal var title: CharSequence? = null
        internal var message: CharSequence? = null
        internal var view: View? = null
        internal var items: List<CharSequence>? = null
        internal var onItem: ((DialogInterface, Int) -> Unit)? = null
        internal var positive: ButtonSpec? = null
        internal var negative: ButtonSpec? = null
        internal var neutral: ButtonSpec? = null
        internal var cancelable = true
        internal var onDismiss: DialogInterface.OnDismissListener? = null

        fun setTitle(t: CharSequence?) = apply { title = t }
        fun setTitle(res: Int) = apply { title = context.getString(res) }
        fun setMessage(m: CharSequence?) = apply { message = m }
        fun setMessage(res: Int) = apply { message = context.getString(res) }
        fun setView(v: View) = apply { view = v }
        fun setItems(list: Array<out CharSequence>, onClick: (DialogInterface, Int) -> Unit) = apply { items = list.toList(); onItem = onClick }
        fun setPositiveButton(text: CharSequence, onClick: ((DialogInterface, Int) -> Unit)?) = apply { positive = ButtonSpec(text, onClick) }
        fun setPositiveButton(res: Int, onClick: ((DialogInterface, Int) -> Unit)?) = setPositiveButton(context.getString(res), onClick)
        fun setNegativeButton(text: CharSequence, onClick: ((DialogInterface, Int) -> Unit)?) = apply { negative = ButtonSpec(text, onClick) }
        fun setNegativeButton(res: Int, onClick: ((DialogInterface, Int) -> Unit)?) = setNegativeButton(context.getString(res), onClick)
        fun setNeutralButton(text: CharSequence, onClick: ((DialogInterface, Int) -> Unit)?) = apply { neutral = ButtonSpec(text, onClick) }
        fun setNeutralButton(res: Int, onClick: ((DialogInterface, Int) -> Unit)?) = setNeutralButton(context.getString(res), onClick)
        fun setCancelable(c: Boolean) = apply { cancelable = c }
        fun setOnDismissListener(l: DialogInterface.OnDismissListener?) = apply { onDismiss = l }
        fun create() = TpDialog(context, this)
        fun show() = create().also { it.show() }
    }
}
