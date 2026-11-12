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
import com.google.android.material.tabs.TabLayout
import com.tripplanner.R
import com.tripplanner.data.Trip

/** 첫 화면 — 여행 목록 (5단계) */
class MainActivity : AppCompatActivity() {

    private val viewModel: TripListViewModel by viewModels()
    private lateinit var prefs: TripViewPrefs
    private lateinit var adapter: TripAdapter
    private lateinit var tabs: TabLayout
    /** 설정에서 '공유 코드로 참여'를 하면 그 여행을 바로 엶 */
    private val settings = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        val id = r.data?.getLongExtra(SettingsActivity.RESULT_JOINED, -1L) ?: -1L
        if (id > 0) openTrip(id)
    }

    private fun openTrip(id: Long) =
        startActivity(Intent(this, TripDetailActivity::class.java).putExtra(TripDetailActivity.EXTRA_TRIP_ID, id))

    /** 설정에서 정렬 · 지난 여행을 바꿨을 수 있으니 돌아오면 다시 그림 */
    override fun onResume() {
        super.onResume()
        if (viewModel.trips.value != null) showRows()
    }

    /** 탭: 전체 / 내 여행 / 초대받은 (회전해도 유지) */
    private var filter = TripFilter.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = setupToolbar(showBack = false)
        toolbar.inflateMenu(R.menu.main)
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_join -> openJoinDialog { id -> openTrip(id) }
                R.id.action_settings -> settings.launch(Intent(this, SettingsActivity::class.java))
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

        // DB 목록을 관찰 → 바뀔 때마다 화면 갱신
        // ---------- 탭: 전체 / 내 여행 / 초대받은 ----------
        filter = savedInstanceState?.getString("filter")?.let { runCatching { TripFilter.valueOf(it) }.getOrNull() } ?: TripFilter.ALL
        tabs = findViewById(R.id.tabs)
        TripFilter.entries.forEach { tabs.addTab(tabs.newTab()) }
        tabs.getTabAt(filter.ordinal)?.select()
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { filter = TripFilter.entries[tab.position]; showRows() }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        val viewRow = findViewById<View>(R.id.viewRow)
        viewModel.trips.observe(this) { list ->
            showRows()
            val noTrips = list.isEmpty()
            viewRow.visibility = if (noTrips) View.GONE else View.VISIBLE
            (tabs.parent as View).visibility = if (noTrips) View.GONE else View.VISIBLE
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("filter", filter.name)
    }

    /** 탭·정렬·지난 여행 설정을 반영해 목록 다시 그리기 */
    private fun showRows() {
        val list = viewModel.trips.value ?: emptyList()
        val rows = TripArrange.rows(list, prefs.sort, prefs.showPast, filter = filter)
        adapter.submit(rows)
        tabs.getTabAt(0)?.text = getString(R.string.tab_all_n, list.size)
        tabs.getTabAt(1)?.text = getString(R.string.tab_mine_n, list.count { TripArrange.matches(it, TripFilter.MINE) })
        tabs.getTabAt(2)?.text = getString(R.string.tab_invited_n, list.count { TripArrange.matches(it, TripFilter.INVITED) })
        // 빈 상태: 여행이 하나도 없을 때 / 고른 탭에 없을 때
        val empty = when {
            list.isEmpty() -> EmptyState(getString(R.string.empty_trips_title), getString(R.string.empty_trips_body), icon = R.drawable.ic_flight)
            rows.isEmpty() && filter == TripFilter.INVITED ->
                EmptyState(getString(R.string.empty_invited_title), getString(R.string.empty_invited_body), icon = R.drawable.ic_group_add)
            rows.isEmpty() && filter == TripFilter.MINE ->
                EmptyState(getString(R.string.empty_mine_title), getString(R.string.empty_trips_body), icon = R.drawable.ic_flight)
            else -> null
        }
        bindEmptyState(findViewById(R.id.emptyState), empty) { showTripForm(null) }
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
