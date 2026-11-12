package com.tripplanner.ui

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import androidx.fragment.app.FragmentActivity
import android.content.DialogInterface
import com.tripplanner.ui.widget.TpDialog
import com.tripplanner.ui.widget.TpTextField
import com.tripplanner.R
import com.tripplanner.route.TimeUtil

/** 입력칸 종류: 글자 / 날짜(달력) / 날짜+시각 / 고르기(목록) */
enum class FieldKind { TEXT, DATE, DATETIME, CHOICE }

/** 입력칸 하나의 설정. hint 는 입력칸 위 라벨로 항상 보입니다. */
data class Field(
    val hint: String,
    val value: String = "",
    val inputType: Int = InputType.TYPE_CLASS_TEXT,
    val minLines: Int = 1,
    val trim: Boolean = true,      // false 면 입력값 앞뒤 공백·줄바꿈을 그대로 둠
    val kind: FieldKind = FieldKind.TEXT,
    /** DATE 칸을 기간(시작~끝)으로 고를 때 (시작 칸 순서, 끝 칸 순서). 두 칸 모두 같은 값을 넣음 */
    val range: Pair<Int, Int>? = null,
    /** CHOICE 칸의 목록 */
    val choices: List<String> = emptyList()
)

/**
 * 입력칸 여러 개짜리 다이얼로그를 코드로 만들어 주는 도우미.
 * 각 칸은 TpTextField(테두리형) — 값이 채워져도 라벨이 사라지지 않습니다.
 * 날짜 칸은 누르면 달력, 날짜+시각 칸은 달력 → 시계가 열립니다 (직접 입력 형식 오류 방지).
 * onSave 가 false 를 돌려주면(검사 실패) 다이얼로그를 닫지 않습니다.
 */
fun showFormDialog(
    context: Context,
    title: String,
    fields: List<Field>,
    onDelete: (() -> Unit)? = null,
    onSave: (List<String>) -> Boolean
) {
    val res = context.resources
    val side = res.getDimensionPixelSize(R.dimen.space_xl)

    val box = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(side, 0, side, 0)
    }
    val inflater = LayoutInflater.from(context)
    val inputs = ArrayList<EditText>()
    fields.forEach { f ->
        if (f.kind == FieldKind.CHOICE) {
            val row = inflater.inflate(R.layout.view_form_choice, box, false)
            val input = row.findViewById<EditText>(R.id.input)
            input.id = View.generateViewId()
            row.findViewById<TextView>(R.id.label).apply { text = f.hint; labelFor = input.id }
            // 직접 입력 대신 누르면 목록 (시안 Select)
            input.inputType = InputType.TYPE_NULL
            input.isFocusable = false
            input.setText(f.value)
            input.setOnClickListener { v ->
                showSelect(v, f.choices.map { c -> SelectRow.Option(c, selected = c == input.text.toString()) { input.setText(c) } },
                    widthDp = (v.width / v.resources.displayMetrics.density).toInt())
            }
            box.addView(row); inputs += input
            return@forEach
        }
        val row = inflater.inflate(R.layout.view_form_field, box, false)
        val layout = row.findViewById<TpTextField>(R.id.layout)
        val input = layout.findViewById<EditText>(R.id.input).apply {
            id = View.generateViewId()
            inputType = if (f.minLines > 1) f.inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE else f.inputType
            if (f.minLines > 1) minLines = f.minLines
            setText(f.value)
        }
        // 라벨을 입력칸에 연결 (TalkBack 이 칸 이름으로 읽음)
        row.findViewById<TextView>(R.id.label).apply { text = f.hint; labelFor = input.id }
        if (f.kind == FieldKind.DATE || f.kind == FieldKind.DATETIME) {
            // 직접 입력 대신 달력·시계로 고르기
            input.inputType = InputType.TYPE_NULL
            input.isFocusable = false
            input.isCursorVisible = false
            layout.setEndIcon(if (f.kind == FieldKind.DATE) R.drawable.ic_event else R.drawable.ic_schedule, f.hint, null)
        }
        box.addView(row); inputs += input
    }
    // 날짜 칸 누르면 달력 열기 (모든 칸을 만든 뒤에 연결 — 기간은 짝 칸도 채우므로)
    val activity = context as? FragmentActivity
    fields.forEachIndexed { i, f ->
        if (activity == null || (f.kind != FieldKind.DATE && f.kind != FieldKind.DATETIME)) return@forEachIndexed
        val open = {
            when {
                f.kind == FieldKind.DATE && f.range != null -> {
                    val (si, ei) = f.range
                    Pickers.dateRange(activity, f.hint, inputs[si].text.toString(), inputs[ei].text.toString()) { s, e ->
                        inputs[si].setText(s); inputs[ei].setText(e)
                    }
                }
                f.kind == FieldKind.DATE -> Pickers.date(activity, f.hint, inputs[i].text.toString()) { inputs[i].setText(it) }
                else -> Pickers.dateTime(inputs[i], f.hint, inputs[i].text.toString()) { inputs[i].setText(it) }
            }
        }
        inputs[i].setOnClickListener { open() }
        (inputs[i].parent.parent as? TpTextField)?.setEndIconOnClickListener { open() }
    }
    val builder = TpDialog.Builder(context)
        .setTitle(title)
        .setView(box)
        .setPositiveButton(R.string.action_save, null)
        .setNegativeButton(R.string.action_cancel, null)
    if (onDelete != null) builder.setNeutralButton(R.string.action_delete) { _, _ -> onDelete() }
    val dialog = builder.create()
    dialog.setOnShowListener {
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)!!.setOnClickListener {
            val values = fields.zip(inputs).map { (f, input) ->
                val text = input.text.toString()
                if (f.trim) text.trim() else text
            }
            if (onSave(values)) dialog.dismiss()
        }
    }
    dialog.show()
}

/**
 * 일차 고르기 다이얼로그: "1일차 · 2/12(금)" … + (선택) 일차 미정.
 * startDate 를 못 읽으면 날짜 없이 "1일차"만 표시합니다.
 */
fun showDayPicker(context: Context, startDate: String?, dayCount: Int, allowNone: Boolean, onPick: (Int?) -> Unit) {
    val labels = (1..dayCount).map { day ->
        context.dayWithDate(day, startDate?.let { TimeUtil.dateOfDay(it, day) })
    } + if (allowNone) listOf(context.getString(R.string.day_none)) else emptyList()
    TpDialog.Builder(context)
        .setTitle(R.string.pick_day_title)
        .setItems(labels.toTypedArray()) { _, which -> onPick(if (which < dayCount) which + 1 else null) }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}

/**
 * 삭제 확인 (시안 규칙): 가운데 팝업, 제목 두 줄만 — 1줄 이름(Bold) / 2줄 '위 …을 삭제하시겠습니까?'(Medium).
 * 설명 문장은 없고(note 는 공유 중지처럼 꼭 알려야 할 때만), 빨간 확인 버튼.
 */
fun confirmDelete(context: Context, name: String, question: String, note: String? = null,
                  positive: Int = R.string.action_delete, onYes: () -> Unit) {
    val bold = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.pretendard_bold)
    val medium = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.pretendard_medium)
    val title = android.text.SpannableStringBuilder().apply {
        append(name, FontSpan(bold), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append("\n")
        append(question, FontSpan(medium), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        // 두 줄 같은 크기 18 (굵기만 다름)
        setSpan(android.text.style.AbsoluteSizeSpan(18, true), 0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    val b = TpDialog.Builder(context, danger = true)
        .setTitle(title)
        .setPositiveButton(positive) { _, _ -> onYes() }
        .setNegativeButton(R.string.action_cancel, null)
    if (note != null) b.setMessage(note)
    b.show()
}

/** 글꼴 하나를 글자 일부에만 (API 26 에서도 동작) */
private class FontSpan(private val font: android.graphics.Typeface?) : android.text.style.MetricAffectingSpan() {
    override fun updateDrawState(tp: android.text.TextPaint) { font?.let { tp.typeface = it } }
    override fun updateMeasureState(tp: android.text.TextPaint) { font?.let { tp.typeface = it } }
}

/** 삭제 확인: 확인 버튼이 빨강 채움 (시안 Dialog · 위험 확인) */
fun confirmDialog(context: Context, message: String, onYes: () -> Unit) {
    TpDialog.Builder(context, danger = true)
        .setMessage(message)
        .setPositiveButton(R.string.action_delete) { _, _ -> onYes() }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}

val DECIMAL_INPUT = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
