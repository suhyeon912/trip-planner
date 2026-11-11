package com.tripplanner.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.tripplanner.R
import com.tripplanner.data.Trip

/** 첫 화면 — 여행 목록 (5단계) */
class MainActivity : AppCompatActivity() {

    private val viewModel: TripListViewModel by viewModels()
    private lateinit var prefs: TripViewPrefs
    private lateinit var adapter: TripAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = setupToolbar(showBack = false)
        toolbar.inflateMenu(R.menu.main)
        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_join) openJoinDialog { id ->
                startActivity(Intent(this, TripDetailActivity::class.java).putExtra(TripDetailActivity.EXTRA_TRIP_ID, id))
            }
            true
        }

        prefs = TripViewPrefs(this)
        adapter = TripAdapter(
            onClick = { trip ->
                val intent = Intent(this, TripDetailActivity::class.java)
                intent.putExtra(TripDetailActivity.EXTRA_TRIP_ID, trip.id)
                startActivity(intent)
            },
            onMenu = { trip, anchor -> showTripMenu(trip, anchor) }
        )
        val rvTrips = findViewById<RecyclerView>(R.id.rvTrips)
        // 넓은 화면(840dp 이상)은 2열 (지난 여행 제목·줄은 한 줄 전체)
        val spans = if (resources.configuration.screenWidthDp >= 840) 2 else 1
        rvTrips.layoutManager = GridLayoutManager(this, spans).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = if (adapter.isFullWidth(position)) spans else 1
            }
        }
        rvTrips.adapter = adapter
        if (resources.configuration.screenWidthDp < 840) rvTrips.limitContentWidth()
        val emptyState = findViewById<View>(R.id.emptyState)

        // DB 목록을 관찰 → 바뀔 때마다 화면 갱신
        val viewRow = findViewById<View>(R.id.viewRow)
        viewModel.trips.observe(this) { list ->
            showRows()
            viewRow.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            bindEmptyState(emptyState,
                if (list.isEmpty()) EmptyState(getString(R.string.empty_trips_title), getString(R.string.empty_trips_body),
                    icon = R.drawable.ic_flight) else null
            ) { showTripForm(null) }
        }
        findViewById<View>(R.id.btnViewSettings).setOnClickListener { showViewSettings(it) }

        // 새 여행 (스크롤하면 아이콘만 남게 줄어듦)
        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fabNewTrip)
        fab.setOnClickListener { showTripForm(null) }
        rvTrips.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 4 && fab.isExtended) fab.shrink()
                else if (dy < -4 && !fab.isExtended) fab.extend()
            }
        })
    }

    /** 정렬·지난 여행 설정을 반영해 목록 다시 그리기 */
    private fun showRows() {
        val list = viewModel.trips.value ?: emptyList()
        adapter.submit(TripArrange.rows(list, prefs.sort, prefs.showPast))
        val sortLabel = getString(sortLabel(prefs.sort))
        findViewById<android.widget.TextView>(R.id.tvViewSummary).text =
            getString(R.string.view_summary, sortLabel, getString(if (prefs.showPast) R.string.past_shown else R.string.past_hidden))
    }

    private fun sortLabel(sort: TripSort) = when (sort) {
        TripSort.NAME -> R.string.sort_name
        TripSort.RECENT -> R.string.sort_recent
        TripSort.DATE -> R.string.sort_date
    }

    /** 보기 설정: 정렬 기준(select) + 지난 여행 숨기기/보이기 */
    private fun showViewSettings(anchor: View) {
        val rows = mutableListOf<SelectRow>(SelectRow.Header(getString(R.string.sort_header)))
        TripSort.entries.forEach { sort ->
            rows += SelectRow.Option(getString(sortLabel(sort)), selected = prefs.sort == sort) { prefs.sort = sort; showRows() }
        }
        rows += SelectRow.Divider
        rows += SelectRow.Option(getString(if (prefs.showPast) R.string.past_hide else R.string.past_show)) {
            prefs.showPast = !prefs.showPast; showRows()
        }
        showSelect(anchor, rows, widthDp = 208)
    }

    /** ⋮ 또는 길게 누르기: 수정 / 삭제(빨간 글자, 확인 후 삭제) */
    private fun showTripMenu(trip: Trip, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.inflate(R.menu.trip_actions)
        popup.markDanger(this, R.id.action_delete)
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_edit -> showTripForm(trip)
                R.id.action_delete -> confirmDelete(this, trip.title, getString(R.string.trip_delete_confirm)) {
                    viewModel.deleteTrip(trip)
                }
            }
            true
        }
        popup.show()
    }

    /** 새 여행 · 여행 수정: 전체 화면 */
    private fun showTripForm(trip: Trip?) {
        startActivity(Intent(this, TripFormActivity::class.java).apply {
            if (trip != null) putExtra(TripDetailActivity.EXTRA_TRIP_ID, trip.id)
        })
    }
}
