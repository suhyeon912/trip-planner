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
 *  - 여행 상세 툴바 → openShareMenu: '초대하기'(openShareDialog) / '일정 내보내기'(openExportDialog)
 *  - openShareDialog: 코드 만들기 / 코드 보기·보내기·복사 / 그만두기
 *  - openExportDialog: 일정(일차별 | 장소) · 교통 / 숙소 고르기 → 8자리 일정 코드 (받은 사람 앱에 새 여행으로 복사)
 *  - 여행 목록 툴바 · 설정 → openJoinDialog: 코드 입력 → 6자리면 참여, 8자리면 일정 가져오기 → 그 여행 열기
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

/** 기다리는 중: "잠시만요…" + 움직이는 막대 (버튼 없음) */
private fun AppCompatActivity.progressDialog(): TpDialog {
    val d = resources.displayMetrics.density
    val box = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding((24 * d).toInt(), 0, (24 * d).toInt(), 0)
        addView(TextView(context).apply {
            setText(R.string.working)
            setTextAppearance(R.style.TextAppearance_TripPlanner_BodyLarge)
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.on_surface))
        })
        addView(com.tripplanner.ui.widget.TpProgressBar(context).apply {
            visibility = View.VISIBLE
        }, android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (16 * d).toInt() })
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    return TpDialog.Builder(this).setView(box).setCancelable(false).show()
}

/** 서버 응답이 너무 늦으면 (인터넷 · 설정 문제) 기다리지 않고 알림 */
private const val SHARE_TIMEOUT_MS = 20_000L

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

/** 공유 버튼 → 초대하기 / 일정 내보내기 */
fun AppCompatActivity.openShareMenu(anchor: View, tripId: Long) {
    if (!share.isConfigured) { notConfigured(); return }
    showSelect(anchor, listOf(
        SelectRow.Option(getString(R.string.share_menu_together)) { openShareDialog(tripId) },
        SelectRow.Option(getString(R.string.export_title)) { openExportDialog(tripId) }
    ), widthDp = 200)
}

/** 일정 내보내기: 일정(일차별 | 장소) · 교통 / 숙소 고르기 → 코드 만들기 (메모는 함께, 사진 · 자료 파일은 빠짐) */
fun AppCompatActivity.openExportDialog(tripId: Long) {
    if (!share.isConfigured) { notConfigured(); return }
    lifecycleScope.launch {
        val trip = repo.getTrip(tripId) ?: return@launch
        val hasSchedule = repo.getPlaces(tripId).any { it.status == com.tripplanner.data.PlaceStatus.CONFIRMED }
        val hasTravel = repo.getFlights(tripId).isNotEmpty() || repo.getLodgings(tripId).isNotEmpty()
        val view = LayoutInflater.from(this@openExportDialog).inflate(R.layout.view_export_options, null)
        val side = resources.getDimensionPixelSize(R.dimen.space_xl)
        view.setPadding(side, view.paddingTop, side, 0)
        val toggle = view.findViewById<SegmentedGroup>(R.id.toggleSchedule)
        toggle.check(R.id.btnByDay)
        view.findViewById<View>(R.id.rowSchedule).visibility = if (hasSchedule) View.VISIBLE else View.GONE
        val swTravel = view.findViewById<com.tripplanner.ui.widget.TpSwitch>(R.id.swTravel).apply {
            isChecked = true
            visibility = if (hasTravel) View.VISIBLE else View.GONE
        }
        val dialog = TpDialog.Builder(this@openExportDialog)
            .setTitle(R.string.export_title)
            .setMessage(R.string.export_intro)
            .setView(view)
            .setPositiveButton(R.string.export_make, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)!!.setOnClickListener {
                dialog.dismiss()
                val byDay = hasSchedule && toggle.checkedButtonId == R.id.btnByDay
                val travel = hasTravel && swTravel.isChecked
                val wait = progressDialog()
                lifecycleScope.launch {
                    runCatching { kotlinx.coroutines.withTimeout(SHARE_TIMEOUT_MS) {
                        share.exportCopy(tripId, schedule = byDay, transport = travel, lodging = travel, memos = true)
                    } }
                        .onSuccess { wait.dismiss(); showCopyCode(trip.title, it) }
                        .onFailure { wait.dismiss(); toast(if (it is kotlinx.coroutines.TimeoutCancellationException) getString(R.string.share_timeout)
                            else getString(R.string.share_failed, it.message ?: it.javaClass.simpleName)) }
                }
            }
        }
        dialog.show()
    }
}

/** 만든 일정 코드: 코드 + 복사 · 코드 보내기 */
private fun AppCompatActivity.showCopyCode(title: String, code: String) {
    val view = LayoutInflater.from(this).inflate(R.layout.view_share_code, null)
    view.findViewById<TextView>(R.id.tvCode).apply {
        text = code
        contentDescription = code.toCharArray().joinToString(" ")
    }
    (view.findViewById<View>(R.id.avatars).parent as View).visibility = View.GONE   // 함께 보는 사람 줄은 없음
    view.findViewById<View>(R.id.btnCopy).setOnClickListener {
        (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", code))
        toast(getString(R.string.share_copied_copy))
    }
    TpDialog.Builder(this)
        .setTitle(R.string.export_code_title)
        .setMessage(R.string.export_code_note)
        .setCloseButton()
        .setView(view)
        .setPositiveButton(R.string.share_send) { _, _ ->
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, getString(R.string.export_message, title, code))
            }, getString(R.string.share_send)))
        }
        .show()
}

fun AppCompatActivity.openShareDialog(tripId: Long) {
    if (!share.isConfigured) { notConfigured(); return }
    lifecycleScope.launch {
        val trip = repo.getTrip(tripId) ?: return@launch
        val code = trip.shareCode
        // 모든 여행이 백업으로 이미 코드를 갖고 있을 수 있음 → 닉네임이 없을 때만 먼저 물어봄
        if (code == null || share.nickname.isBlank()) {
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
                    share.updateMyName()
                    dialog.dismiss()
                    val wait = progressDialog()
                    lifecycleScope.launch {
                        runCatching { kotlinx.coroutines.withTimeout(SHARE_TIMEOUT_MS) { share.share(tripId) } }
                            .onSuccess { wait.dismiss(); showCode(trip.title, it, tripId) }
                            .onFailure { wait.dismiss(); toast(if (it is kotlinx.coroutines.TimeoutCancellationException) getString(R.string.share_timeout)
                    else getString(R.string.share_failed, it.message ?: it.javaClass.simpleName)) }
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
    view.findViewById<View>(R.id.btnCopy).setOnClickListener {
        (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", code))
        toast(getString(R.string.share_copied))
    }

    // 버튼: 왼쪽 '공유 중지'(빨간 글자) · 오른쪽 '코드 보내기'(채움) · 오른쪽 위 X
    val dialog = TpDialog.Builder(this)
        .setTitle(R.string.share_code_title)
        .setCloseButton()
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
    // 활동 기록으로 가면 이 팝업은 닫음 (뒤로 오면 여행 화면 그대로)
    view.findViewById<View>(R.id.btnActivity).setOnClickListener {
        dialog.dismiss()
        startActivity(Intent(this, ActivityLogActivity::class.java).putExtra(TripDetailActivity.EXTRA_TRIP_ID, tripId))
    }
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
        .setMessage(R.string.join_intro)
        .setView(box)
        .setPositiveButton(R.string.join_button, null)
        .setNegativeButton(R.string.action_cancel, null)
        .create()
    dialog.setOnShowListener {
        // 8자리(일정 코드)면 닉네임이 필요 없고 버튼은 '가져오기'
        fun isCopy() = ShareCodec.isCopyCode(ShareCodec.normalize(input.text?.toString().orEmpty()))
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val copy = isCopy()
                nameRow.visibility = if (copy) View.GONE else View.VISIBLE
                dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setText(if (copy) R.string.import_button else R.string.join_button)
            }
        })
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)!!.setOnClickListener {
            val code = ShareCodec.normalize(input.text?.toString().orEmpty())
            if (ShareCodec.isCopyCode(code)) {
                layout.error = null
                val wait = progressDialog()
                lifecycleScope.launch {
                    runCatching { kotlinx.coroutines.withTimeout(SHARE_TIMEOUT_MS) { share.importCopy(code) } }
                        .onSuccess { wait.dismiss(); dialog.dismiss(); toast(getString(R.string.import_done)); onJoined(it) }
                        .onFailure {
                            wait.dismiss()
                            layout.error = if (it is ShareManager.NotFoundException) getString(R.string.join_not_found)
                                           else if (it is kotlinx.coroutines.TimeoutCancellationException) getString(R.string.share_timeout)
                                           else getString(R.string.import_failed, it.message ?: it.javaClass.simpleName)
                        }
                }
                return@setOnClickListener
            }
            if (!ShareCodec.isValidCode(code)) { layout.error = getString(R.string.join_invalid); return@setOnClickListener }
            layout.error = null
            val name = nameInput.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) { nameLayout.error = getString(R.string.error_nickname); return@setOnClickListener }
            nameLayout.error = null
            share.nickname = name
            val wait = progressDialog()
            lifecycleScope.launch {
                runCatching { kotlinx.coroutines.withTimeout(SHARE_TIMEOUT_MS) { share.join(code) } }
                    .onSuccess { wait.dismiss(); dialog.dismiss(); onJoined(it) }
                    .onFailure {
                        wait.dismiss()
                        layout.error = if (it is ShareManager.NotFoundException) getString(R.string.join_not_found)
                                       else if (it is kotlinx.coroutines.TimeoutCancellationException) getString(R.string.share_timeout)
                                       else getString(R.string.join_failed, it.message ?: it.javaClass.simpleName)
                    }
            }
        }
    }
    dialog.show()
}

private fun AppCompatActivity.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

// ---------------- 백업 · 복구 코드 (설정) ----------------

private fun AppCompatActivity.recoveryError(e: Throwable): String = when (e) {
    is ShareManager.RecoveryNotAllowedException -> getString(R.string.recovery_not_allowed)
    is ShareManager.BadRecoveryCodeException -> getString(R.string.recovery_bad_code)
    is kotlinx.coroutines.TimeoutCancellationException -> getString(R.string.share_timeout)
    else -> getString(R.string.share_failed, e.message ?: e.javaClass.simpleName)
}

/** 내 복구 코드 보기 (처음이면 만들어서 계정에 연결) → onShown(코드) */
fun AppCompatActivity.openRecoveryCode(onShown: (String) -> Unit = {}) {
    if (!share.isConfigured) { notConfigured(); return }
    val wait = progressDialog()
    lifecycleScope.launch {
        val result = runCatching { kotlinx.coroutines.withTimeout(SHARE_TIMEOUT_MS) { share.recoveryCode() } }
        wait.dismiss()
        result.onFailure { toast(recoveryError(it)); return@launch }
        val code = result.getOrThrow()
        onShown(code)
        val view = LayoutInflater.from(this@openRecoveryCode).inflate(R.layout.view_share_code, null)
        val shown = com.tripplanner.share.RecoveryCode.display(code)
        view.findViewById<TextView>(R.id.tvCode).apply {
            text = shown; textSize = 20f; letterSpacing = 0.06f
            contentDescription = code.toCharArray().joinToString(" ")
        }
        (view.findViewById<View>(R.id.avatars).parent as View).visibility = View.GONE   // 함께 보는 사람 줄은 없음
        view.findViewById<View>(R.id.btnCopy).setOnClickListener {
            (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", shown))
            toast(getString(R.string.recovery_copied))
        }
        TpDialog.Builder(this@openRecoveryCode)
            .setTitle(R.string.recovery_title)
            .setMessage(R.string.recovery_intro)
            .setCloseButton()
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}

/** 복구 코드로 불러오기: 코드 입력 → 그 계정의 여행을 이 기기로 */
fun AppCompatActivity.openRestore(onDone: (Int) -> Unit = {}) {
    if (!share.isConfigured) { notConfigured(); return }
    val (row, input, layout) = labeledField(getString(R.string.recovery_label), "")
    input.hint = "ABCD-EFGH-JKLM"
    input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
        android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    val dialog = TpDialog.Builder(this)
        .setTitle(R.string.restore_title)
        .setMessage(R.string.restore_intro)
        .setView(dialogBox(row))
        .setPositiveButton(R.string.restore_button, null)
        .setNegativeButton(R.string.action_cancel, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)!!.setOnClickListener {
            if (com.tripplanner.share.RecoveryCode.normalize(input.text?.toString().orEmpty()) == null) {
                layout.error = getString(R.string.recovery_bad_code); return@setOnClickListener
            }
            layout.error = null
            val wait = progressDialog()
            lifecycleScope.launch {
                val result = runCatching { kotlinx.coroutines.withTimeout(SHARE_TIMEOUT_MS * 2) { share.restore(input.text.toString()) } }
                wait.dismiss()
                result.onSuccess { n -> dialog.dismiss(); toast(getString(R.string.restore_done, n)); onDone(n) }
                    .onFailure { layout.error = recoveryError(it) }
            }
        }
    }
    dialog.show()
}
