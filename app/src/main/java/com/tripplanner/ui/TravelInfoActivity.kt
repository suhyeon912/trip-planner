package com.tripplanner.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R
import com.tripplanner.data.Flight
import com.tripplanner.data.Lodging

/**
 * 교통 · 숙소 (시안 TravelInfo).
 * 줄을 누르면 수정(전체 화면), ⋮ → 수정 / 삭제(확인 팝업). 목록 아래 점선 버튼으로 추가.
 */
class TravelInfoActivity : AppCompatActivity() {

    private val viewModel: TravelInfoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_travel_info)
        setupToolbar()
        limitScrollContentWidth(R.id.scroll)

        val flightAdapter = SimpleAdapter<Flight>(
            title = { flightTitle(it) },
            subtitle = { getString(R.string.flight_sub, TripFormat.shortDateTime(it.departAt), TripFormat.arriveShort(it.departAt, it.arriveAt)) },
            onClick = { openForm(TravelFormActivity.KIND_FLIGHT, it.id) },
            onMenu = { f, anchor -> rowMenu(anchor, { openForm(TravelFormActivity.KIND_FLIGHT, f.id) }) {
                confirmDelete(this, flightTitle(f), getString(R.string.flight_delete_confirm)) { viewModel.deleteFlight(f) }
            } },
            iconOf = { it.mode.icon },
            idOf = { it.id }
        )
        val lodgingAdapter = SimpleAdapter<Lodging>(
            title = { it.name },
            subtitle = { "${TripFormat.shortDate(it.checkIn)} – ${TripFormat.shortDate(it.checkOut)}" +
                    getString(if (it.lat == 0.0 && it.lng == 0.0) R.string.lodging_no_location else R.string.lodging_has_location) },
            onClick = { openForm(TravelFormActivity.KIND_LODGING, it.id) },
            onMenu = { l, anchor -> rowMenu(anchor, { openForm(TravelFormActivity.KIND_LODGING, l.id) }) {
                confirmDelete(this, l.name, getString(R.string.lodging_delete_confirm)) { viewModel.deleteLodging(l) }
            } },
            icon = R.drawable.ic_hotel,
            idOf = { it.id }
        )
        val rvFlights = findViewById<RecyclerView>(R.id.rvFlights).apply {
            layoutManager = LinearLayoutManager(this@TravelInfoActivity); adapter = flightAdapter
        }
        val rvLodgings = findViewById<RecyclerView>(R.id.rvLodgings).apply {
            layoutManager = LinearLayoutManager(this@TravelInfoActivity); adapter = lodgingAdapter
        }
        val tvFlightTitle = findViewById<TextView>(R.id.tvFlightTitle)
        val tvLodgingTitle = findViewById<TextView>(R.id.tvLodgingTitle)

        // 섹션 제목에 개수, 비어 있으면 묶음 카드 없이 점선 '추가' 버튼만
        viewModel.flights.observe(this) {
            flightAdapter.submit(it)
            tvFlightTitle.text = if (it.isEmpty()) getString(R.string.flights) else getString(R.string.flights_n, it.size)
            rvFlights.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.lodgings.observe(this) {
            lodgingAdapter.submit(it)
            tvLodgingTitle.text = if (it.isEmpty()) getString(R.string.lodgings) else getString(R.string.lodgings_n, it.size)
            rvLodgings.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
        }

        findViewById<View>(R.id.btnAddFlight).setOnClickListener { openForm(TravelFormActivity.KIND_FLIGHT, 0L) }
        findViewById<View>(R.id.btnAddLodging).setOnClickListener { openForm(TravelFormActivity.KIND_LODGING, 0L) }
    }

    /** "KE2707 김포 → 하네다" (번호가 없는 렌터카·차 · 기타는 종류 이름) */
    private fun flightTitle(f: Flight) =
        getString(R.string.flight_title, f.flightNo.ifBlank { f.mode.label }, f.fromAirport, f.toAirport)

    private fun rowMenu(anchor: View, onEdit: () -> Unit, onDelete: () -> Unit) {
        showSelect(anchor, listOf(
            SelectRow.Option(getString(R.string.action_edit)) { onEdit() },
            SelectRow.Option(getString(R.string.action_delete), danger = true) { onDelete() }
        ), widthDp = 168)
    }

    private fun openForm(kind: String, id: Long) {
        startActivity(Intent(this, TravelFormActivity::class.java)
            .putExtra(TripDetailActivity.EXTRA_TRIP_ID, viewModel.tripId)
            .putExtra(TravelFormActivity.EXTRA_KIND, kind)
            .putExtra(TravelFormActivity.EXTRA_ITEM_ID, id))
    }
}
