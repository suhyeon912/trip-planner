package com.tripplanner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tripplanner.ui.widget.TpDialog
import android.widget.EditText
import com.tripplanner.ui.widget.TpTextField
import com.tripplanner.R
import com.tripplanner.TripPlannerApp
import com.tripplanner.share.ShareCodec
import com.tripplanner.share.ShareManager
import kotlinx.coroutines.launch

/*
 * 공유 · 초대 화면들 (다이얼로그).
 *  - 여행 상세 툴바 → openShareDialog: 코드 만들기 / 코드 보기·보내기·복사 / 그만두기
 *  - 여행 목록 툴바 → openJoinDialog: 코드 입력 → 참여 → 그 여행 열기
 */

private val AppCompatActivity.share: ShareManager get() = (application as TripPlannerApp).share
private val AppCompatActivity.repo get() = (application as TripPlannerApp).repository

private fun AppCompatActivity.notConfigured() {
    TpDialog.Builder(this)
        .setTitle(R.string.share_not_configured_title)
        .setMessage(R.string.share_not_configured)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}

private fun AppCompatActivity.progressDialog(): TpDialog =
    TpDialog.Builder(this).setMessage(R.string.working).setCancelable(false).show()

/** 팝업 안 입력 칸 하나 (라벨은 칸 위) → (칸 묶음, 입력칸, 오류 표시용 칸) */
private fun AppCompatActivity.labeledField(label: String, value: String): Triple<View, EditText, TpTextField> {
    val row = LayoutInflater.from(this).inflate(R.layout.view_form_field, null)
    val layout = row.findViewById<TpTextField>(R.id.layout)
    val input = layout.findViewById<EditText>(R.id.input).apply {
        id = View.generateViewId(); setText(value)
    }
    row.findViewById<TextView>(R.id.label).apply { text = label; labelFor = input.id }
    return Triple(row, input, layout)
}

private fun AppCompatActivity.dialogBox(vararg rows: View) = android.widget.LinearLayout(this).apply {
    orientation = android.widget.LinearLayout.VERTICAL
    val side = resources.getDimensionPixelSize(R.dimen.space_xl)
    setPadding(side, 0, side, 0)
    rows.forEach { addView(it) }
}

fun AppCompatActivity.openShareDialog(tripId: Long) {
    if (!share.isConfigured) { notConfigured(); return }
    lifecycleScope.launch {
        val trip = repo.getTrip(tripId) ?: return@launch
        val code = trip.shareCode
        if (code == null) {
            // 여행 공유: 내 닉네임 (함께 보는 사람에게 보이는 이름) → 공유 시작
            val (row, input, layout) = labeledField(getString(R.string.label_nickname), share.nickname)
            val dialog = TpDialog.Builder(this@openShareDialog)
                .setTitle(R.string.share_new_title)
                .setMessage(R.string.share_intro)
                .setView(dialogBox(row))
                .setPositiveButton(R.string.share_start, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE)!!.setOnClickListener {
                    val name = input.text?.toString()?.trim().orEmpty()
                    if (name.isEmpty()) { layout.error = getString(R.string.error_nickname); return@setOnClickListener }
                    share.nickname = name
                    dialog.dismiss()
                    val wait = progressDialog()
                    lifecycleScope.launch {
                        runCatching { share.share(tripId) }
                            .onSuccess { wait.dismiss(); showCode(trip.title, it, tripId) }
                            .onFailure { wait.dismiss(); toast(getString(R.string.share_failed, it.message ?: it.javaClass.simpleName)) }
                    }
                }
            }
            dialog.show()
        } else showCode(trip.title, code, tripId)
    }
}

private fun AppCompatActivity.showCode(title: String, code: String, tripId: Long) {
    val view = LayoutInflater.from(this).inflate(R.layout.view_share_code, null)
    view.findViewById<TextView>(R.id.tvCode).apply {
        text = code
        contentDescription = code.toCharArray().joinToString(" ")   // 낭독 시 한 글자씩
    }
    // 함께 보는 사람: "지우 (나) · 민지 · 하준" (이름을 모르면 사람 수)
    val members = view.findViewById<TextView>(R.id.tvMembers)
    lifecycleScope.launch {
        val t = repo.getTrip(tripId)
        val me = share.nickname.takeIf { it.isNotBlank() }
        val names = listOfNotNull(me) + t?.members.orEmpty()
        members.text = if (t != null && t.members.isNotEmpty()) names.joinToString(" · ")
                       else getString(R.string.share_members, share.memberCount(code))
        // 머리글자 동그라미 (활동 기록과 같은 색 순서), 2px 겹침 테두리
        if (t != null && t.members.isNotEmpty()) {
            val box = view.findViewById<android.widget.LinearLayout>(R.id.avatars)
            val d = resources.displayMetrics.density
            box.visibility = View.VISIBLE
            names.take(4).forEachIndexed { i, n ->
                box.addView(TextView(this@showCode).apply {
                    text = n.take(1); gravity = android.view.Gravity.CENTER
                    setTextColor(0xFFFFFFFF.toInt()); textSize = 13f
                    typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.pretendard_bold)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(PinIcons.DAY_COLORS[i % PinIcons.DAY_COLORS.size])
                        setStroke((2 * d).toInt(), color(R.color.card_bg))
                    }
                }, android.widget.LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt()).apply { if (i > 0) marginStart = (-6 * d).toInt() })
            }
        }
    }
    view.findViewById<View>(R.id.btnActivity).setOnClickListener {
        startActivity(Intent(this, ActivityLogActivity::class.java).putExtra(TripDetailActivity.EXTRA_TRIP_ID, tripId))
    }
    view.findViewById<View>(R.id.btnCopy).setOnClickListener {
        (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", code))
        toast(getString(R.string.share_copied))
    }

    // 버튼: 왼쪽 '공유 중지'(빨간 글자) · 오른쪽 '코드 보내기'(채움)
    TpDialog.Builder(this)
        .setTitle(R.string.share_code_title)
        .setView(view)
        .setPositiveButton(R.string.share_send) { _, _ ->
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, getString(R.string.share_message, title, code))
            }, getString(R.string.share_send)))
        }
        .setNeutralButton(R.string.share_stop) { _, _ ->
            confirmDelete(this, title, getString(R.string.share_stop_question), getString(R.string.share_stop_confirm),
                positive = R.string.share_stop) { lifecycleScope.launch { share.stop(tripId) } }
        }
        .show()
}

fun AppCompatActivity.openJoinDialog(onJoined: (Long) -> Unit) {
    if (!share.isConfigured) { notConfigured(); return }
    val (row, input, layout) = labeledField(getString(R.string.join_label), "")
    input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    input.filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(8))
    val (nameRow, nameInput, nameLayout) = labeledField(getString(R.string.label_nickname), share.nickname)
    val box = dialogBox(row, nameRow)
    val dialog = TpDialog.Builder(this)
        .setTitle(R.string.join_title)
        .setView(box)
        .setPositiveButton(R.string.join_button, null)
        .setNegativeButton(R.string.action_cancel, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)!!.setOnClickListener {
            val code = ShareCodec.normalize(input.text?.toString().orEmpty())
            if (!ShareCodec.isValidCode(code)) { layout.error = getString(R.string.join_invalid); return@setOnClickListener }
            layout.error = null
            val name = nameInput.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) { nameLayout.error = getString(R.string.error_nickname); return@setOnClickListener }
            nameLayout.error = null
            share.nickname = name
            val wait = progressDialog()
            lifecycleScope.launch {
                runCatching { share.join(code) }
                    .onSuccess { wait.dismiss(); dialog.dismiss(); onJoined(it) }
                    .onFailure {
                        wait.dismiss()
                        layout.error = if (it is ShareManager.NotFoundException) getString(R.string.join_not_found)
                                       else getString(R.string.join_failed, it.message ?: it.javaClass.simpleName)
                    }
            }
        }
    }
    dialog.show()
}

private fun AppCompatActivity.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
