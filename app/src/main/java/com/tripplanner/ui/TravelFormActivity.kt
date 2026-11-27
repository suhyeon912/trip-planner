package com.tripplanner.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.tripplanner.ui.widget.TpButton
import android.widget.EditText
import android.widget.TextView
import com.tripplanner.ui.widget.TpTextField
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
    /** 체크인 · 체크아웃 시각 "HH:mm" (빈 글자 = 안 정함) */
    private var checkInTime = ""
    private var checkOutTime = ""
    private var lat = 0.0
    private var lng = 0.0
    /** 위치 칸에 보일 주소 (검색 결과 또는 저장된 주소) */
    private var address = ""

    private lateinit var fieldMode: SelectField
    private lateinit var fieldDepartDate: SelectField
    private lateinit var fieldDepartTime: SelectField
    private lateinit var fieldArriveDate: SelectField
    private lateinit var fieldArriveTime: SelectField
    /** 여행 첫날 (날짜 없이 시각부터 고를 때 기본 날짜) */
    private var tripStart: String = ""
    private lateinit var fieldLocation: SelectField
    private lateinit var fieldCheckIn: SelectField
    private lateinit var fieldCheckOut: SelectField
    private lateinit var fieldCheckInTime: SelectField
    private lateinit var fieldCheckOutTime: SelectField

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
        fieldDepartDate = findViewById(R.id.fieldDepartDate)
        fieldDepartTime = findViewById(R.id.fieldDepartTime)
        fieldArriveDate = findViewById(R.id.fieldArriveDate)
        fieldArriveTime = findViewById(R.id.fieldArriveTime)
        fieldLocation = findViewById(R.id.fieldLocation)
        fieldCheckIn = findViewById(R.id.fieldCheckIn)
        fieldCheckOut = findViewById(R.id.fieldCheckOut)
        fieldCheckInTime = findViewById(R.id.fieldCheckInTime)
        fieldCheckOutTime = findViewById(R.id.fieldCheckOutTime)
        setupFields()
        lifecycleScope.launch { tripStart = repo.getTrip(tripId)?.startDate.orEmpty() }

        findViewById<TpButton>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<TpButton>(R.id.btnSave).setOnClickListener { save() }

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
                checkIn = l.checkIn; checkOut = l.checkOut
                checkInTime = l.checkInTime; checkOutTime = l.checkOutTime; lat = l.lat; lng = l.lng; address = l.address
                input(R.id.inputLodgingName).setText(l.name)
                input(R.id.inputAddressDetail).setText(l.addressDetail)
                input(R.id.inputLocationMemo).setText(l.locationMemo)
            } ?: finish()
            render()
        } else render()
    }

    private fun input(id: Int) = findViewById<EditText>(id)

    private fun setupFields() {
        fieldMode.label = getString(R.string.hint_transport_mode)
        fieldMode.setDropdown(true)
        fieldMode.setOnClickListener { v ->
            showSelect(v, TransportMode.entries.map { m ->
                SelectRow.Option(m.label, selected = m == mode) { mode = m; render() }
            }, widthDp = v.width.coerceAtLeast(1).let { (it / resources.displayMetrics.density).toInt() })
        }
        // 출발 · 도착: 날짜는 달력, 시각은 휠 (따로 고르기). 저장 형식은 그대로 "yyyy-MM-dd HH:mm"
        fun dateTimeFields(date: SelectField, time: SelectField, labelRes: Int, get: () -> String, set: (String) -> Unit, fallbackDate: () -> String) {
            val label = getString(labelRes)
            date.label = getString(R.string.date_of, label); date.setIcon(R.drawable.ic_calendar)
            date.setPlaceholder(getString(R.string.placeholder_date))
            time.label = getString(R.string.time_of, label); time.setIcon(R.drawable.ic_schedule)
            date.hideEndIcon(); time.hideEndIcon()
            time.setPlaceholder(getString(R.string.time_placeholder))
            date.setOnClickListener {
                Pickers.date(this, label, get().ifBlank { fallbackDate() }) { d -> set(Pickers.join(d, Pickers.minutesOf(get()))); render() }
            }
            time.setOnClickListener { v ->
                WheelPopup.time(v, label, Pickers.minutesOf(get()), clearLabel = getString(R.string.time_clear), defaultMinutes = 9 * 60) { m ->
                    val d = Pickers.dateOf(get()) ?: Pickers.dateOf(fallbackDate()) ?: java.time.LocalDate.now().toString()
                    set(Pickers.join(d, m)); render()
                }
            }
        }
        dateTimeFields(fieldDepartDate, fieldDepartTime, R.string.label_depart, { departAt }, { departAt = it }, { tripStart })
        dateTimeFields(fieldArriveDate, fieldArriveTime, R.string.label_arrive, { arriveAt }, { arriveAt = it }, { departAt.ifBlank { tripStart } })

        fieldLocation.label = getString(R.string.label_location)
        fieldLocation.setIcon(R.drawable.ic_place)
        fieldLocation.setEndIcon(R.drawable.ic_search)   // 시안: 주소 + 돋보기
        fieldLocation.setPlaceholder(getString(R.string.placeholder_location))
        fieldLocation.setOnClickListener { pickLocation(it) }
        for ((f, label) in listOf(fieldCheckIn to R.string.hint_check_in, fieldCheckOut to R.string.hint_check_out)) {
            f.label = getString(R.string.date_of, getString(label))
            f.setIcon(R.drawable.ic_calendar)
            f.hideEndIcon()
            f.setPlaceholder(getString(R.string.placeholder_date))
            // 체크인 · 체크아웃은 각각 하루씩 고름. 같은 날(당일 이용)도 되고,
            // 체크아웃이 체크인보다 앞이 되면 반대쪽을 같은 날로 맞춤
            val isIn = f === fieldCheckIn
            f.setOnClickListener {
                val current = if (isIn) checkIn.ifBlank { tripStart } else checkOut.ifBlank { Pickers.plusDays(checkIn, 1) ?: tripStart }
                Pickers.date(this, getString(label), current) { d ->
                    if (isIn) {
                        checkIn = d
                        if (checkOut.isBlank() || checkOut < d) checkOut = d
                    } else {
                        checkOut = d
                        if (checkIn.isBlank() || checkIn > d) checkIn = d
                    }
                    render()
                }
            }
        }
        // 체크인 · 체크아웃 시각: 휠 (기본 15:00 · 11:00, '선택 안 함'으로 지우기)
        for ((f, labelRes, def) in listOf(Triple(fieldCheckInTime, R.string.hint_check_in, 15 * 60), Triple(fieldCheckOutTime, R.string.hint_check_out, 11 * 60))) {
            val label = getString(labelRes)
            f.label = getString(R.string.time_of, label); f.setIcon(R.drawable.ic_schedule)
            f.hideEndIcon()
            f.setPlaceholder(getString(R.string.time_placeholder))
            val isIn = f === fieldCheckInTime
            f.setOnClickListener { v ->
                val cur = com.tripplanner.route.TimeUtil.parseTime(if (isIn) checkInTime else checkOutTime)
                WheelPopup.time(v, label, cur, clearLabel = getString(R.string.time_clear), defaultMinutes = def) { m ->
                    val t = m?.let { com.tripplanner.route.TimeUtil.format(it) } ?: ""
                    if (isIn) checkInTime = t else checkOutTime = t
                    render()
                }
            }
        }
        input(R.id.inputNumber).doAfterTextChanged { if (!it.isNullOrBlank()) findViewById<TpTextField>(R.id.layoutNumber).error = null }
        input(R.id.inputLodgingName).doAfterTextChanged { if (!it.isNullOrBlank()) findViewById<TpTextField>(R.id.layoutLodgingName).error = null }
    }

    private fun render() {
        fieldMode.setIcon(mode.icon)
        fieldMode.setValue(mode.label)
        findViewById<View>(R.id.groupNumber).visibility = if (needsNumber(mode)) View.VISIBLE else View.GONE
        fieldDepartDate.setValue(Pickers.dateOf(departAt)?.let { TripFormat.dateWithDay(it) })
        fieldDepartTime.setValue(Pickers.minutesOf(departAt)?.let { com.tripplanner.route.TimeUtil.format(it) })
        fieldArriveDate.setValue(Pickers.dateOf(arriveAt)?.let { TripFormat.dateWithDay(it) })
        fieldArriveTime.setValue(Pickers.minutesOf(arriveAt)?.let { com.tripplanner.route.TimeUtil.format(it) })
        // 교통 종류에 맞는 안내 글자 (출발지 · 도착지 · 번호)
        val hints = ModeHints.of(mode)
        findViewById<TextView>(R.id.labelNumber).setText(hints.numberLabel)
        findViewById<TpTextField>(R.id.layoutNumber).placeholderText = getString(hints.number)
        (input(R.id.inputFrom).parent.parent as? TpTextField ?: input(R.id.inputFrom).parent as? TpTextField)?.placeholderText = getString(hints.from)
        (input(R.id.inputTo).parent.parent as? TpTextField ?: input(R.id.inputTo).parent as? TpTextField)?.placeholderText = getString(hints.to)
        fieldCheckIn.setValue(checkIn.takeIf { it.isNotBlank() }?.let { TripFormat.dateWithDay(it) })
        fieldCheckOut.setValue(checkOut.takeIf { it.isNotBlank() }?.let { TripFormat.dateWithDay(it) })
        fieldCheckInTime.setValue(checkInTime.takeIf { it.isNotBlank() })
        fieldCheckOutTime.setValue(checkOutTime.takeIf { it.isNotBlank() })
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
                    findViewById<TpTextField>(R.id.layoutNumber).error = getString(R.string.error_flight_no)
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
                    findViewById<TpTextField>(R.id.layoutLodgingName).error = getString(R.string.error_lodging_name)
                    input(R.id.inputLodgingName).requestFocus(); return@launch
                }
                repo.saveLodging(Lodging(id = itemId, tripId = tripId, name = name,
                    checkIn = checkIn, checkOut = checkOut, checkInTime = checkInTime, checkOutTime = checkOutTime, lat = lat, lng = lng, address = address,
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
        outState.putString("inTime", checkInTime); outState.putString("outTime", checkOutTime)
        outState.putDouble("lat", lat); outState.putDouble("lng", lng)
        outState.putString("address", address)
    }

    private fun restore(b: Bundle) {
        mode = runCatching { TransportMode.valueOf(b.getString("mode") ?: "") }.getOrDefault(TransportMode.FLIGHT)
        departAt = b.getString("depart", ""); arriveAt = b.getString("arrive", "")
        checkIn = b.getString("in", ""); checkOut = b.getString("out", "")
        checkInTime = b.getString("inTime", ""); checkOutTime = b.getString("outTime", "")
        lat = b.getDouble("lat"); lng = b.getDouble("lng"); address = b.getString("address", "")
    }
}

/** 교통 종류별 안내 글자 */
private data class ModeHints(val from: Int, val to: Int, val numberLabel: Int, val number: Int) {
    companion object {
        fun of(mode: TransportMode) = when (mode) {
            TransportMode.FLIGHT -> ModeHints(R.string.ph_from_flight, R.string.ph_to_flight, R.string.label_no_flight, R.string.ph_no_flight)
            TransportMode.TRAIN -> ModeHints(R.string.ph_from_train, R.string.ph_to_train, R.string.label_no_train, R.string.ph_no_train)
            TransportMode.BUS -> ModeHints(R.string.ph_from_bus, R.string.ph_to_bus, R.string.label_no_bus, R.string.ph_no_bus)
            TransportMode.SHIP -> ModeHints(R.string.ph_from_ship, R.string.ph_to_ship, R.string.label_no_ship, R.string.ph_no_ship)
            TransportMode.CAR -> ModeHints(R.string.ph_from_car, R.string.ph_to_car, R.string.label_no_flight, R.string.ph_no_flight)
            TransportMode.ETC -> ModeHints(R.string.ph_from_etc, R.string.ph_to_etc, R.string.label_no_flight, R.string.ph_no_flight)
        }
    }
}
