package com.tripplanner.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R
import com.tripplanner.data.Flight
import com.tripplanner.data.Lodging
import com.tripplanner.data.TransportMode

/** 비행기 · 숙소 추가/수정/삭제 화면. 항목을 누르면 수정(삭제) 다이얼로그 */
class TravelInfoActivity : AppCompatActivity() {

    private val viewModel: TravelInfoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_travel_info)
        setupToolbar()
        limitScrollContentWidth(R.id.scroll)

        val flightAdapter = SimpleAdapter<Flight>(
            title = { getString(R.string.flight_title, it.flightNo, it.fromAirport, it.toAirport) },
            subtitle = { getString(R.string.flight_sub, it.departAt, it.arriveAt) },
            onClick = { showFlightForm(it) },
            iconOf = { it.mode.icon }
        )
        val lodgingAdapter = SimpleAdapter<Lodging>(
            title = { it.name },
            subtitle = { "${it.checkIn} ~ ${it.checkOut}" +
                    getString(if (it.lat == 0.0 && it.lng == 0.0) R.string.lodging_no_location else R.string.lodging_has_location) },
            onClick = { showLodgingForm(it) },
            icon = R.drawable.ic_hotel
        )
        findViewById<RecyclerView>(R.id.rvFlights).apply {
            layoutManager = LinearLayoutManager(this@TravelInfoActivity); adapter = flightAdapter
        }
        findViewById<RecyclerView>(R.id.rvLodgings).apply {
            layoutManager = LinearLayoutManager(this@TravelInfoActivity); adapter = lodgingAdapter
        }
        val tvNoFlight = findViewById<Button>(R.id.tvNoFlight)
        val tvNoLodging = findViewById<Button>(R.id.tvNoLodging)
        val tvFlightTitle = findViewById<TextView>(R.id.tvFlightTitle)
        val tvLodgingTitle = findViewById<TextView>(R.id.tvLodgingTitle)
        tvNoFlight.setOnClickListener { showFlightForm(null) }
        tvNoLodging.setOnClickListener { showLodgingForm(null) }

        // 비어 있으면 '추가하기' 행, 섹션 제목에 개수
        viewModel.flights.observe(this) {
            flightAdapter.submit(it)
            tvFlightTitle.text = if (it.isEmpty()) getString(R.string.flights) else getString(R.string.flights_n, it.size)
            tvNoFlight.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.lodgings.observe(this) {
            lodgingAdapter.submit(it)
            tvLodgingTitle.text = if (it.isEmpty()) getString(R.string.lodgings) else getString(R.string.lodgings_n, it.size)
            tvNoLodging.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
        }

        findViewById<Button>(R.id.btnAddFlight).setOnClickListener { showFlightForm(null) }
        findViewById<Button>(R.id.btnAddLodging).setOnClickListener { showLodgingForm(null) }
    }

    private fun showFlightForm(f: Flight?) {
        showFormDialog(
            this, getString(if (f == null) R.string.add_flight else R.string.edit_flight),
            listOf(
                // 0: 종류 (비행기 · 기차·KTX · 버스 · 배 · 렌터카 · 기타)
                Field(getString(R.string.hint_transport_mode), (f?.mode ?: TransportMode.FLIGHT).label,
                    kind = FieldKind.CHOICE, choices = TransportMode.entries.map { it.label }),
                Field(getString(R.string.hint_flight_no), f?.flightNo ?: ""),
                Field(getString(R.string.hint_from_airport), f?.fromAirport ?: ""),
                Field(getString(R.string.hint_to_airport), f?.toAirport ?: ""),
                Field(getString(R.string.hint_depart_at), f?.departAt ?: "", kind = FieldKind.DATETIME),
                Field(getString(R.string.hint_arrive_at), f?.arriveAt ?: "", kind = FieldKind.DATETIME)
            ),
            onDelete = f?.let { { viewModel.deleteFlight(it) } }
        ) { v ->
            if (v[1].isBlank()) { toast(R.string.error_flight_no); return@showFormDialog false }
            val mode = TransportMode.entries.firstOrNull { it.label == v[0] } ?: TransportMode.FLIGHT
            viewModel.saveFlight(
                Flight(id = f?.id ?: 0, tripId = viewModel.tripId, flightNo = v[1].uppercase(),
                    fromAirport = v[2].uppercase(), toAirport = v[3].uppercase(), departAt = v[4], arriveAt = v[5], mode = mode)
            )
            true
        }
    }

    private fun showLodgingForm(l: Lodging?) {
        showFormDialog(
            this, getString(if (l == null) R.string.add_lodging else R.string.edit_lodging),
            listOf(
                Field(getString(R.string.hint_lodging_name), l?.name ?: ""),
                Field(getString(R.string.hint_check_in), l?.checkIn ?: "", kind = FieldKind.DATE, range = 1 to 2),
                Field(getString(R.string.hint_check_out), l?.checkOut ?: "", kind = FieldKind.DATE, range = 1 to 2),
                Field(getString(R.string.hint_lodging_lat), l?.lat?.takeIf { it != 0.0 }?.toString() ?: "", DECIMAL_INPUT),
                Field(getString(R.string.hint_lodging_lng), l?.lng?.takeIf { it != 0.0 }?.toString() ?: "", DECIMAL_INPUT)
            ),
            onDelete = l?.let { { viewModel.deleteLodging(it) } }
        ) { v ->
            if (v[0].isBlank()) { toast(R.string.error_lodging_name); return@showFormDialog false }
            viewModel.saveLodging(
                Lodging(id = l?.id ?: 0, tripId = viewModel.tripId, name = v[0], checkIn = v[1], checkOut = v[2],
                    lat = v[3].toDoubleOrNull() ?: 0.0, lng = v[4].toDoubleOrNull() ?: 0.0)
            )
            true
        }
    }

    private fun toast(msg: Int) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
