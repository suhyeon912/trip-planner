package com.tripplanner.ui

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.FragmentActivity
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.widget.Toast
import com.tripplanner.R
import com.tripplanner.data.Trip
import java.time.LocalDate
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
 * 각 칸은 TextInputLayout(테두리형) — 값이 채워져도 라벨이 사라지지 않습니다.
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
            val layout = inflater.inflate(R.layout.view_form_choice, box, false) as TextInputLayout
            layout.hint = f.hint
            val input = layout.findViewById<MaterialAutoCompleteTextView>(R.id.input)
            input.setSimpleItems(f.choices.toTypedArray())
            input.setText(f.value, false)
            box.addView(layout); inputs += input
            return@forEach
        }
        val layout = inflater.inflate(R.layout.view_form_field, box, false) as TextInputLayout
        layout.hint = f.hint
        val input = layout.findViewById<TextInputEditText>(R.id.input).apply {
            inputType = if (f.minLines > 1) f.inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE else f.inputType
            if (f.minLines > 1) minLines = f.minLines
            setText(f.value)
        }
        if (f.kind == FieldKind.DATE || f.kind == FieldKind.DATETIME) {
            // 직접 입력 대신 달력·시계로 고르기
            input.inputType = InputType.TYPE_NULL
            input.isFocusable = false
            input.isCursorVisible = false
            layout.endIconMode = TextInputLayout.END_ICON_CUSTOM
            layout.setEndIconDrawable(if (f.kind == FieldKind.DATE) R.drawable.ic_event else R.drawable.ic_schedule)
            layout.endIconContentDescription = f.hint
        }
        box.addView(layout); inputs += input
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
                else -> Pickers.dateTime(activity, f.hint, inputs[i].text.toString()) { inputs[i].setText(it) }
            }
        }
        inputs[i].setOnClickListener { open() }
        (inputs[i].parent.parent as? TextInputLayout)?.setEndIconOnClickListener { open() }
    }
    val builder = MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setView(box)
        .setPositiveButton(R.string.action_save, null)
        .setNegativeButton(R.string.action_cancel, null)
    if (onDelete != null) builder.setNeutralButton(R.string.action_delete) { _, _ -> onDelete() }
    val dialog = builder.create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
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
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.pick_day_title)
        .setItems(labels.toTypedArray()) { _, which -> onPick(if (which < dayCount) which + 1 else null) }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}

fun confirmDialog(context: Context, message: String, onYes: () -> Unit) {
    MaterialAlertDialogBuilder(context)
        .setMessage(message)
        .setPositiveButton(R.string.action_delete) { _, _ -> onYes() }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}

val DECIMAL_INPUT = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED

/**
 * 여행 추가·수정 다이얼로그 (여행 목록과 여행 상세에서 함께 사용).
 * 검사: 이름 필수, 날짜는 yyyy-MM-dd 이고 종료일이 시작일보다 빠르면 안 됨.
 */
fun showTripFormDialog(context: Context, trip: Trip?, onSave: (Trip) -> Unit) {
    showFormDialog(
        context, context.getString(if (trip == null) R.string.new_trip else R.string.edit_trip),
        listOf(
            Field(context.getString(R.string.hint_trip_title), trip?.title ?: ""),
            Field(context.getString(R.string.hint_trip_start), trip?.startDate ?: "", kind = FieldKind.DATE, range = 1 to 2),
            Field(context.getString(R.string.hint_trip_end), trip?.endDate ?: "", kind = FieldKind.DATE, range = 1 to 2)
        )
    ) { (title, start, end) ->
        val datesOk = runCatching { !LocalDate.parse(end).isBefore(LocalDate.parse(start)) }.getOrDefault(false)
        when {
            title.isBlank() -> Toast.makeText(context, R.string.error_trip_title, Toast.LENGTH_SHORT).show()
            !datesOk -> Toast.makeText(context, R.string.error_trip_dates, Toast.LENGTH_SHORT).show()
            else -> {
                onSave(trip?.copy(title = title, startDate = start, endDate = end)
                    ?: Trip(title = title, startDate = start, endDate = end))
                return@showFormDialog true
            }
        }
        false
    }
}
