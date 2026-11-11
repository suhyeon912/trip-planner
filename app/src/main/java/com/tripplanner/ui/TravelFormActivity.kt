package com.tripplanner.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tripplanner.R
import com.tripplanner.TripPlannerApp
import com.tripplanner.data.Flight
import com.tripplanner.data.Lodging
import com.tripplanner.data.TransportMode
import com.tripplanner.route.Region
import kotlinx.coroutines.launch

/**
 * 교통편 · 숙소 추가/수정 (시안 TravelFlightAdd · TravelLodgingAdd, 전체 화면).
 * 저장 형식은 그대로: 날짜 "yyyy-MM-dd", 날짜+시각 "yyyy-MM-dd HH:mm", 위치는 위도·경도.
 * 번호 칸: 비행기 · 기차 · 버스 · 배는 필수, 렌터카·차 · 기타는 칸을 숨김.
 */
class TravelFormActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_ITEM_ID = "item_id"
        const val KIND_FLIGHT = "flight"
        const val KIND_LODGING = "lodging"
        /** 번호(편명·열차 번호)가 있는 교통 종류 */
        fun needsNumber(mode: TransportMode) = mode in setOf(TransportMode.FLIGHT, TransportMode.TRAIN, TransportMode.BUS, TransportMode.SHIP)
    }

    private val repo by lazy { (application as TripPlannerApp).repository }
    private val tripId by lazy { intent.getLongExtra(TripDetailActivity.EXTRA_TRIP_ID, -1L) }
    private val itemId by lazy { intent.getLongExtra(EXTRA_ITEM_ID, 0L) }
    private val isFlight by lazy { intent.getStringExtra(EXTRA_KIND) != KIND_LODGING }

    // 화면 상태 (회전해도 유지)
    private var mode = TransportMode.FLIGHT
    private var departAt = ""
    private var arriveAt = ""
    private var checkIn = ""
    private var checkOut = ""
    private var lat = 0.0
    private var lng = 0.0
    /** 위치 칸에 보일 주소 (검색 결과 또는 저장된 주소) */
    private var address = ""

    private lateinit var fieldMode: SelectField
    private lateinit var fieldDepart: SelectField
    private lateinit var fieldArrive: SelectField
    private lateinit var fieldLocation: SelectField
    private lateinit var fieldCheckIn: SelectField
    private lateinit var fieldCheckOut: SelectField

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_travel_form)
        val title = when {
            isFlight && itemId == 0L -> R.string.add_flight
            isFlight -> R.string.edit_flight
            itemId == 0L -> R.string.add_lodging
            else -> R.string.edit_lodging
        }
        val toolbar = setupToolbar(getString(title), showBack = false)
        toolbar.setNavigationIcon(R.drawable.ic_close)
        toolbar.setNavigationContentDescription(R.string.action_close)
        toolbar.setNavigationOnClickListener { finish() }
        limitScrollContentWidth(R.id.scroll)

        findViewById<View>(R.id.flightGroup).visibility = if (isFlight) View.VISIBLE else View.GONE
        findViewById<View>(R.id.lodgingGroup).visibility = if (isFlight) View.GONE else View.VISIBLE

        fieldMode = findViewById(R.id.fieldMode)
        fieldDepart = findViewById(R.id.fieldDepart)
        fieldArrive = findViewById(R.id.fieldArrive)
        fieldLocation = findViewById(R.id.fieldLocation)
        fieldCheckIn = findViewById(R.id.fieldCheckIn)
        fieldCheckOut = findViewById(R.id.fieldCheckOut)
        setupFields()

        findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }

        if (savedInstanceState != null) {
            restore(savedInstanceState); render()
        } else if (itemId != 0L) lifecycleScope.launch {
            if (isFlight) repo.getFlights(tripId).firstOrNull { it.id == itemId }?.let { f ->
                mode = f.mode; departAt = f.departAt; arriveAt = f.arriveAt
                input(R.id.inputNumber).setText(f.flightNo)
                input(R.id.inputFrom).setText(f.fromAirport)
                input(R.id.inputTo).setText(f.toAirport)
            } ?: finish()
            else repo.getLodgings(tripId).firstOrNull { it.id == itemId }?.let { l ->
                checkIn = l.checkIn; checkOut = l.checkOut; lat = l.lat; lng = l.lng; address = l.address
                input(R.id.inputLodgingName).setText(l.name)
                input(R.id.inputAddressDetail).setText(l.addressDetail)
                input(R.id.inputLocationMemo).setText(l.locationMemo)
            } ?: finish()
            render()
        } else render()
    }

    private fun input(id: Int) = findViewById<TextInputEditText>(id)

    private fun setupFields() {
        fieldMode.label = getString(R.string.hint_transport_mode)
        fieldMode.setDropdown(true)
        fieldMode.setOnClickListener { v ->
            showSelect(v, TransportMode.entries.map { m ->
                SelectRow.Option(m.label, selected = m == mode) { mode = m; render() }
            }, widthDp = v.width.coerceAtLeast(1).let { (it / resources.displayMetrics.density).toInt() })
        }
        fieldDepart.label = getString(R.string.hint_depart_at)
        fieldDepart.setIcon(R.drawable.ic_calendar)
        fieldDepart.setPlaceholder(getString(R.string.placeholder_pick_datetime))
        fieldDepart.setOnClickListener { Pickers.dateTime(this, getString(R.string.hint_depart_at), departAt) { departAt = it; render() } }
        fieldArrive.label = getString(R.string.hint_arrive_at)
        fieldArrive.setIcon(R.drawable.ic_calendar)
        fieldArrive.setPlaceholder(getString(R.string.placeholder_pick_datetime))
        fieldArrive.setOnClickListener { Pickers.dateTime(this, getString(R.string.hint_arrive_at), arriveAt.ifBlank { departAt }) { arriveAt = it; render() } }

        fieldLocation.label = getString(R.string.label_location)
        fieldLocation.setIcon(R.drawable.ic_place)
        fieldLocation.setDropdown(true)
        fieldLocation.setPlaceholder(getString(R.string.placeholder_location))
        fieldLocation.setOnClickListener { pickLocation(it) }
        for ((f, label) in listOf(fieldCheckIn to R.string.hint_check_in, fieldCheckOut to R.string.hint_check_out)) {
            f.label = getString(label)
            f.setIcon(R.drawable.ic_calendar)
            f.setPlaceholder(getString(R.string.placeholder_pick_date))
            f.setOnClickListener {
                Pickers.dateRange(this, getString(R.string.add_lodging), checkIn, checkOut) { s, e -> checkIn = s; checkOut = e; render() }
            }
        }
        input(R.id.inputNumber).doAfterTextChanged { if (!it.isNullOrBlank()) findViewById<TextInputLayout>(R.id.layoutNumber).error = null }
        input(R.id.inputLodgingName).doAfterTextChanged { if (!it.isNullOrBlank()) findViewById<TextInputLayout>(R.id.layoutLodgingName).error = null }
    }

    private fun render() {
        fieldMode.setIcon(mode.icon)
        fieldMode.setValue(mode.label)
        findViewById<View>(R.id.groupNumber).visibility = if (needsNumber(mode)) View.VISIBLE else View.GONE
        fieldDepart.setValue(departAt.takeIf { it.isNotBlank() }?.let { TripFormat.dateTimeWithDay(it) })
        fieldArrive.setValue(arriveAt.takeIf { it.isNotBlank() }?.let { TripFormat.dateTimeWithDay(it) })
        fieldCheckIn.setValue(checkIn.takeIf { it.isNotBlank() }?.let { TripFormat.dateWithDay(it) })
        fieldCheckOut.setValue(checkOut.takeIf { it.isNotBlank() }?.let { TripFormat.dateWithDay(it) })
        val hasLoc = lat != 0.0 || lng != 0.0
        fieldLocation.setValue(when {
            address.isNotBlank() -> address
            hasLoc -> getString(R.string.location_set, lat, lng)
            else -> null
        })
    }

    /** 위치: 검색해서 고르기 / 위도·경도 직접 입력(검색 키가 없을 때) / 지우기 */
    private fun pickLocation(anchor: View) {
        val rows = mutableListOf(
            SelectRow.Option(getString(R.string.location_search)) {
                val name = input(R.id.inputLodgingName).text.toString()
                val domestic = (lat == 0.0 && lng == 0.0) || Region.isKorea(lat, lng)
                showPlaceSearch(this, name, domestic) { hit ->
                    lat = hit.lat; lng = hit.lng
                    address = hit.address.ifBlank { hit.name }
                    render()
                }
            },
            SelectRow.Option(getString(R.string.location_manual)) {
                showFormDialog(this, getString(R.string.label_location), listOf(
                    Field(getString(R.string.hint_lat), lat.takeIf { it != 0.0 }?.toString() ?: "", DECIMAL_INPUT),
                    Field(getString(R.string.hint_lng), lng.takeIf { it != 0.0 }?.toString() ?: "", DECIMAL_INPUT)
                )) { (a, b) ->
                    lat = a.toDoubleOrNull() ?: 0.0; lng = b.toDoubleOrNull() ?: 0.0
                    address = ""; render(); true
                }
            }
        )
        if (lat != 0.0 || lng != 0.0) rows += SelectRow.Option(getString(R.string.location_clear), danger = true) {
            lat = 0.0; lng = 0.0; address = ""; render()
        }
        showSelect(anchor, rows, widthDp = 220)
    }

    private fun save() {
        lifecycleScope.launch {
            if (isFlight) {
                val number = input(R.id.inputNumber).text.toString().trim()
                if (needsNumber(mode) && number.isBlank()) {
                    findViewById<TextInputLayout>(R.id.layoutNumber).error = getString(R.string.error_flight_no)
                    input(R.id.inputNumber).requestFocus(); return@launch
                }
                repo.saveFlight(Flight(id = itemId, tripId = tripId,
                    flightNo = if (needsNumber(mode)) number.uppercase() else "",
                    fromAirport = input(R.id.inputFrom).text.toString().trim().uppercase(),
                    toAirport = input(R.id.inputTo).text.toString().trim().uppercase(),
                    departAt = departAt, arriveAt = arriveAt, mode = mode))
            } else {
                val name = input(R.id.inputLodgingName).text.toString().trim()
                if (name.isBlank()) {
                    findViewById<TextInputLayout>(R.id.layoutLodgingName).error = getString(R.string.error_lodging_name)
                    input(R.id.inputLodgingName).requestFocus(); return@launch
                }
                repo.saveLodging(Lodging(id = itemId, tripId = tripId, name = name,
                    checkIn = checkIn, checkOut = checkOut, lat = lat, lng = lng, address = address,
                    addressDetail = input(R.id.inputAddressDetail).text.toString().trim(),
                    locationMemo = input(R.id.inputLocationMemo).text.toString().trim()))
            }
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("mode", mode.name)
        outState.putString("depart", departAt); outState.putString("arrive", arriveAt)
        outState.putString("in", checkIn); outState.putString("out", checkOut)
        outState.putDouble("lat", lat); outState.putDouble("lng", lng)
        outState.putString("address", address)
    }

    private fun restore(b: Bundle) {
        mode = runCatching { TransportMode.valueOf(b.getString("mode") ?: "") }.getOrDefault(TransportMode.FLIGHT)
        departAt = b.getString("depart", ""); arriveAt = b.getString("arrive", "")
        checkIn = b.getString("in", ""); checkOut = b.getString("out", "")
        lat = b.getDouble("lat"); lng = b.getDouble("lng"); address = b.getString("address", "")
    }
}
