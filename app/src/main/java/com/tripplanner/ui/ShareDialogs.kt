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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.share_not_configured_title)
        .setMessage(R.string.share_not_configured)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}

private fun AppCompatActivity.progressDialog(): AlertDialog =
    MaterialAlertDialogBuilder(this).setMessage(R.string.working).setCancelable(false).show()

fun AppCompatActivity.openShareDialog(tripId: Long) {
    if (!share.isConfigured) { notConfigured(); return }
    lifecycleScope.launch {
        val trip = repo.getTrip(tripId) ?: return@launch
        val code = trip.shareCode
        if (code == null) {
            MaterialAlertDialogBuilder(this@openShareDialog)
                .setTitle(R.string.share_new_title)
                .setMessage(R.string.share_intro)
                .setPositiveButton(R.string.share_start) { _, _ ->
                    val wait = progressDialog()
                    lifecycleScope.launch {
                        runCatching { share.share(tripId) }
                            .onSuccess { wait.dismiss(); showCode(trip.title, it, tripId) }
                            .onFailure { wait.dismiss(); toast(getString(R.string.share_failed, it.message ?: it.javaClass.simpleName)) }
                    }
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        } else showCode(trip.title, code, tripId)
    }
}

private fun AppCompatActivity.showCode(title: String, code: String, tripId: Long) {
    val view = LayoutInflater.from(this).inflate(R.layout.view_share_code, null)
    view.findViewById<TextView>(R.id.tvCode).apply {
        text = code
        contentDescription = code.toCharArray().joinToString(" ")   // 낭독 시 한 글자씩
    }
    val members = view.findViewById<TextView>(R.id.tvMembers)
    members.text = getString(R.string.share_members, 1)
    lifecycleScope.launch { members.text = getString(R.string.share_members, share.memberCount(code)) }
    view.findViewById<View>(R.id.btnCopy).setOnClickListener {
        (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", code))
        toast(getString(R.string.share_copied))
    }

    // 버튼: 왼쪽 '공유 중지'(빨간 글자) · 오른쪽 '코드 보내기'(채움)
    MaterialAlertDialogBuilder(this)
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
    val row = LayoutInflater.from(this).inflate(R.layout.view_form_field, null)
    val layout = row.findViewById<TextInputLayout>(R.id.layout)
    val input = layout.findViewById<TextInputEditText>(R.id.input).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(8))
    }
    row.findViewById<TextView>(R.id.label).apply { text = getString(R.string.join_label); labelFor = R.id.input }
    val box = android.widget.FrameLayout(this).apply {
        val side = resources.getDimensionPixelSize(R.dimen.space_xl)
        setPadding(side, 0, side, 0); addView(row)
    }
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(R.string.join_title)
        .setView(box)
        .setPositiveButton(R.string.join_button, null)
        .setNegativeButton(R.string.action_cancel, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val code = ShareCodec.normalize(input.text?.toString().orEmpty())
            if (!ShareCodec.isValidCode(code)) { layout.error = getString(R.string.join_invalid); return@setOnClickListener }
            layout.error = null
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
