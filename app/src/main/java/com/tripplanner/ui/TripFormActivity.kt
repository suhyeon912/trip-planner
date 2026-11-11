package com.tripplanner.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tripplanner.R
import com.tripplanner.TripPlannerApp
import com.tripplanner.data.Trip
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 새 여행 · 여행 수정 (시안 NewTrip · TripEdit, 전체 화면).
 * EXTRA_TRIP_ID 가 있으면 수정. 저장 규칙은 기존 팝업과 같음:
 * 이름 필수, 기간은 달력으로 고르고 종료일이 시작일보다 빠르면 안 됨.
 * 오류는 토스트 대신 칸 아래 빨간 글자로 보여 줌.
 */
class TripFormActivity : AppCompatActivity() {

    private val repo by lazy { (application as TripPlannerApp).repository }
    private var trip: Trip? = null
    private var start = ""
    private var end = ""

    private lateinit var layoutName: TextInputLayout
    private lateinit var inputName: TextInputEditText
    private lateinit var layoutPeriod: TextInputLayout
    private lateinit var inputPeriod: TextInputEditText
    private lateinit var tvNights: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_form)
        val tripId = intent.getLongExtra(TripDetailActivity.EXTRA_TRIP_ID, 0L)
        val toolbar = setupToolbar(getString(if (tripId == 0L) R.string.new_trip else R.string.edit_trip), showBack = false)
        // 입력 화면은 닫기(×)
        toolbar.setNavigationIcon(R.drawable.ic_close)
        toolbar.setNavigationContentDescription(R.string.action_close)
        toolbar.setNavigationOnClickListener { finish() }
        limitScrollContentWidth(R.id.scroll)

        layoutName = findViewById(R.id.layoutName)
        inputName = findViewById(R.id.inputName)
        layoutPeriod = findViewById(R.id.layoutPeriod)
        inputPeriod = findViewById(R.id.inputPeriod)
        tvNights = findViewById(R.id.tvNights)

        inputName.doAfterTextChanged { if (!it.isNullOrBlank()) layoutName.error = null }
        inputPeriod.setOnClickListener { pickPeriod() }
        layoutPeriod.setStartIconOnClickListener { pickPeriod() }

        findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnSave).apply {
            setText(if (tripId == 0L) R.string.action_add else R.string.action_save)
            setOnClickListener { save() }
        }

        if (savedInstanceState != null) {
            start = savedInstanceState.getString("start", "")
            end = savedInstanceState.getString("end", "")
            showPeriod()
        }
        if (tripId != 0L) lifecycleScope.launch {
            trip = repo.getTrip(tripId) ?: run { finish(); return@launch }
            if (savedInstanceState == null) {
                trip!!.let { inputName.setText(it.title); start = it.startDate; end = it.endDate }
                showPeriod()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("start", start); outState.putString("end", end)
    }

    private fun pickPeriod() {
        Pickers.dateRange(this, getString(R.string.pick_period_title), start, end) { s, e ->
            start = s; end = e
            layoutPeriod.error = null
            showPeriod()
        }
    }

    private fun showPeriod() {
        val t = Trip(title = "", startDate = start, endDate = end)
        val nights = TripFormat.nightsDays(start, end)
        inputPeriod.setText(if (start.isBlank()) "" else TripFormat.fullRange(t))
        tvNights.text = nights
        tvNights.visibility = if (nights == null) View.GONE else View.VISIBLE
        inputPeriod.contentDescription = listOfNotNull(inputPeriod.text?.toString()?.takeIf { it.isNotBlank() }, nights)
            .joinToString(", ").ifBlank { null }
    }

    private fun save() {
        val title = inputName.text.toString().trim()
        val datesOk = runCatching { !LocalDate.parse(end).isBefore(LocalDate.parse(start)) }.getOrDefault(false)
        layoutName.error = if (title.isBlank()) getString(R.string.error_trip_title) else null
        layoutPeriod.error = if (!datesOk) getString(R.string.error_trip_period) else null
        if (title.isBlank()) { inputName.requestFocus(); return }
        if (!datesOk) return
        lifecycleScope.launch {
            // 저장 직전에 다시 읽어서 그사이 바뀐 메모·공유 정보 등을 덮어쓰지 않게
            val old = trip?.let { repo.getTrip(it.id) ?: it }
            if (old == null) repo.addTrip(Trip(title = title, startDate = start, endDate = end))
            else repo.updateTrip(old.copy(title = title, startDate = start, endDate = end))
            finish()
        }
    }
}
