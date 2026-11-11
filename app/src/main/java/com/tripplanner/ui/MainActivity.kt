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

        val adapter = TripAdapter(
            onClick = { trip ->
                val intent = Intent(this, TripDetailActivity::class.java)
                intent.putExtra(TripDetailActivity.EXTRA_TRIP_ID, trip.id)
                startActivity(intent)
            },
            onMenu = { trip, anchor -> showTripMenu(trip, anchor) }
        )
        val rvTrips = findViewById<RecyclerView>(R.id.rvTrips)
        // 넓은 화면(840dp 이상)은 2열
        rvTrips.layoutManager = GridLayoutManager(this, if (resources.configuration.screenWidthDp >= 840) 2 else 1)
        rvTrips.adapter = adapter
        if (resources.configuration.screenWidthDp < 840) rvTrips.limitContentWidth()
        val emptyState = findViewById<View>(R.id.emptyState)

        // DB 목록을 관찰 → 바뀔 때마다 화면 갱신
        viewModel.trips.observe(this) { list ->
            adapter.submit(list)
            // 툴바 부제: 다가오는 여행 n · 지난 여행 n
            val past = list.count { TripFormat.dday(it.trip)?.phase == TripFormat.Phase.PAST }
            toolbar.subtitle = if (list.isEmpty()) null else getString(R.string.trips_summary, list.size - past, past)
            bindEmptyState(emptyState,
                if (list.isEmpty()) EmptyState(getString(R.string.empty_trips_title), getString(R.string.empty_trips_body),
                    getString(R.string.new_trip), EmptyState.Action.NEW_TRIP) else null
            ) { showTripForm(null) }
        }

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

    /** ⋮ 또는 길게 누르기: 수정 / 삭제(빨간 글자, 확인 후 삭제) */
    private fun showTripMenu(trip: Trip, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.inflate(R.menu.trip_actions)
        popup.markDanger(this, R.id.action_delete)
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_edit -> showTripForm(trip)
                R.id.action_delete -> confirmDialog(this, getString(R.string.trip_delete_confirm, trip.title)) {
                    viewModel.deleteTrip(trip)
                }
            }
            true
        }
        popup.show()
    }

    private fun showTripForm(trip: Trip?) {
        showTripFormDialog(this, trip) { viewModel.saveTrip(it) }
    }
}
