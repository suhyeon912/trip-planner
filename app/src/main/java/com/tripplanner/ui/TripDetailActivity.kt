package com.tripplanner.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.tripplanner.R
import com.tripplanner.data.Place
import com.tripplanner.data.PlaceStatus
import com.tripplanner.data.Trip

/**
 * 여행 상세 (3단계 구조)
 *  - 요약 카드: 기간·D-day(누르면 여행 수정) / 숙소·교통(→ 항공·숙소) / 하고 싶은 거 메모(→ 메모 창)
 *  - 탭: 일정 n / 후보 n, 일정 탭에는 일차 칩
 *  - 목록: 일차 타임라인 + "n일차 경로 제안 보기"
 *  - 장소 추가: Extended FAB
 *  - 툴바 지도 버튼: 목록 ↔ 지도 전환 (같은 탭·일차의 장소를 지도에 표시)
 */
class TripDetailActivity : AppCompatActivity(), PlaceAdapter.Listener {

    companion object {
        const val EXTRA_TRIP_ID = "trip_id"
        /** RouteActivity 가 SavedStateHandle "day" 로 읽는 키 */
        const val EXTRA_DAY = "day"
    }

    private val viewModel: TripDetailViewModel by viewModels()
    private lateinit var tabs: TabLayout
    private lateinit var detailMap: TripDetailMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_detail)

        val toolbar = setupToolbar()
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_edit_trip -> editTrip()
                R.id.action_travel_info -> openTravelInfo()
                R.id.action_note -> showTripNoteDialog()
                R.id.action_view_mode -> viewModel.toggleMapMode()
                R.id.action_share -> openShareDialog(viewModel.tripId)
            }
            true
        }

        // ---------- 요약 카드 ----------
        val tvDates = findViewById<TextView>(R.id.tvDates)
        val tvDday = findViewById<TextView>(R.id.tvDday)
        val tvTravel = findViewById<TextView>(R.id.tvTravel)
        val tvNote = findViewById<TextView>(R.id.tvNote)
        findViewById<View>(R.id.rowDates).setOnClickListener { editTrip() }
        findViewById<View>(R.id.rowTravel).setOnClickListener { openTravelInfo() }
        findViewById<View>(R.id.rowNote).setOnClickListener { showTripNoteDialog() }

        viewModel.trip.observe(this) { trip ->
            if (trip == null) return@observe
            toolbar.title = trip.title
            toolbar.subtitle = trip.shareCode?.let { getString(R.string.shared_subtitle, it) }
            tvDates.text = TripFormat.dateRange(trip)
            val dday = TripFormat.dday(trip)?.text
            tvDday.visibility = if (dday == null) View.GONE else View.VISIBLE
            tvDday.text = dday
            val note = trip.note.trim()
            tvNote.text = note.ifEmpty { getString(R.string.note_placeholder) }
            tvNote.setTextColor(ContextCompat.getColor(this,
                if (note.isEmpty()) R.color.text_sub else R.color.on_surface))
        }
        // 숙소·교통 한 줄: 숙소 이름들 · 비행기 n편
        fun updateTravel() {
            val lodgings = viewModel.lodgings.value.orEmpty()
            val flights = viewModel.flights.value.orEmpty()
            val lodgingText = if (lodgings.isEmpty()) getString(R.string.no_lodging) else lodgings.joinToString(", ") { it.name }
            val flightText = if (flights.isEmpty()) getString(R.string.no_flight) else getString(R.string.flight_count, flights.size)
            tvTravel.text = lodgingText + getString(R.string.list_separator) + flightText
        }
        viewModel.lodgings.observe(this) { updateTravel() }
        viewModel.flights.observe(this) { updateTravel() }

        // ---------- 탭 ----------
        tabs = findViewById(R.id.tabs)
        tabs.addTab(tabs.newTab().setText(R.string.tab_schedule))
        tabs.addTab(tabs.newTab().setText(R.string.tab_candidates))
        if (viewModel.currentTab.value == PlaceStatus.CANDIDATE) tabs.getTabAt(1)?.select()
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setTab(if (tab.position == 0) PlaceStatus.CONFIRMED else PlaceStatus.CANDIDATE)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // ---------- 일차 칩 ----------
        val rvDays = findViewById<RecyclerView>(R.id.rvDays)
        val chipAdapter = DayChipAdapter { key -> viewModel.selectDay(key) }
        rvDays.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvDays.adapter = chipAdapter

        // ---------- 장소 목록 ----------
        val adapter = PlaceAdapter(this) { day -> openRoute(day) }
        val rvPlaces = findViewById<RecyclerView>(R.id.rvPlaces)
        rvPlaces.layoutManager = LinearLayoutManager(this)
        rvPlaces.adapter = adapter
        // 일차 하나를 보고 있을 때: 장소를 길게 누르거나 손잡이를 끌어 순서 바꾸기 → 놓으면 저장
        var moved = false
        val touch = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder) =
                if (adapter.dragEnabled && vh is PlaceAdapter.PlaceHolder)
                    makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) else 0
            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                val ok = adapter.moveItem(from.bindingAdapterPosition, to.bindingAdapterPosition)
                if (ok) moved = true
                return ok
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled() = adapter.dragEnabled
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                if (moved) {
                    moved = false
                    viewModel.reorderDay(adapter.currentItems.filterIsInstance<DetailItem.PlaceRow>().map { it.place.id })
                }
            }
        })
        touch.attachToRecyclerView(rvPlaces)
        adapter.onStartDrag = { touch.startDrag(it) }

        // 2단 배치(layout-w840dp)가 아니면 목록 폭을 640dp 로 제한
        if (findViewById<View>(R.id.summaryPane) == null) rvPlaces.limitContentWidth()
        val emptyState = findViewById<View>(R.id.emptyState)
        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fabAddPlace)

        // ---------- 지도로 보기 ----------
        detailMap = TripDetailMap(findViewById(android.R.id.content), this, this) { cardShown ->
            if (cardShown) fab.hide() else fab.show()
        }
        fun renderMap() {
            val ui = viewModel.ui.value ?: return
            val mapOn = viewModel.mapMode.value == true
            rvPlaces.visibility = if (mapOn) View.GONE else View.VISIBLE
            detailMap.container.visibility = if (mapOn && ui.empty == null) View.VISIBLE else View.GONE
            if (!mapOn || ui.empty != null) return
            val candidatesTab = viewModel.currentTab.value == PlaceStatus.CANDIDATE
            val day = viewModel.selectedDay.value ?: TripDetailViewModel.DAY_ALL
            val lodgings = if (!candidatesTab && day > 0) listOfNotNull(viewModel.lodgingFor(day))
                           else viewModel.lodgings.value.orEmpty()
            val places = ui.items.filterIsInstance<DetailItem.PlaceRow>().map { it.place }
            detailMap.render(places, candidatesTab, day, lodgings, viewModel.tripCoords())
        }
        viewModel.mapMode.observe(this) { mapOn ->
            toolbar.menu.findItem(R.id.action_view_mode)?.apply {
                setIcon(if (mapOn) R.drawable.ic_list else R.drawable.ic_map)
                setTitle(if (mapOn) R.string.show_list else R.string.show_map)
            }
            // 한 화면 배치에서는 지도를 넓게 보려고 요약 카드를 접고, 목록으로 돌아오면 펼침
            findViewById<AppBarLayout>(R.id.appBar)?.setExpanded(!mapOn, true)
            if (!mapOn) fab.show()
            renderMap()
        }

        viewModel.ui.observe(this) { ui ->
            tabs.getTabAt(0)?.text = getString(R.string.tab_schedule_n, ui.confirmedCount)
            tabs.getTabAt(1)?.text = getString(R.string.tab_candidates_n, ui.candidateCount)
            val onSchedule = viewModel.currentTab.value != PlaceStatus.CANDIDATE
            rvDays.visibility = if (onSchedule) View.VISIBLE else View.GONE
            chipAdapter.submit(ui.chips)
            adapter.dragEnabled = onSchedule && (viewModel.selectedDay.value ?: 0) > 0
            adapter.submit(ui.items)
            bindEmptyState(emptyState, ui.empty) { action ->
                when (action) {
                    EmptyState.Action.SHOW_CANDIDATES -> tabs.getTabAt(1)?.select()
                    EmptyState.Action.ADD_PLACE -> openPlaceEditor(null)
                    EmptyState.Action.GO_BACK -> finish()
                    EmptyState.Action.NEW_TRIP -> Unit
                }
            }
            renderMap()
        }

        // ---------- 장소 추가 (스크롤하면 아이콘만 남게 줄어듦) ----------
        fab.setOnClickListener { openPlaceEditor(null) }
        rvPlaces.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 4 && fab.isExtended) fab.shrink()
                else if (dy < -4 && !fab.isExtended) fab.extend()
            }
        })
    }

    // ---------- 화면 이동 ----------
    private fun openPlaceEditor(place: Place?) {
        val intent = Intent(this, AddPlaceActivity::class.java)
            .putExtra(EXTRA_TRIP_ID, viewModel.tripId)
            .putExtra(AddPlaceActivity.EXTRA_DAY_COUNT, viewModel.dayCount())
        if (place != null) intent.putExtra(AddPlaceActivity.EXTRA_PLACE_ID, place.id)
        startActivity(intent)   // 저장 결과는 Room → LiveData 로 자동 반영
    }

    private fun openTravelInfo() {
        startActivity(Intent(this, TravelInfoActivity::class.java).putExtra(EXTRA_TRIP_ID, viewModel.tripId))
    }

    /** 경로 제안 화면을 그 일차로 열기 */
    private fun openRoute(day: Int) {
        startActivity(Intent(this, RouteActivity::class.java)
            .putExtra(EXTRA_TRIP_ID, viewModel.tripId)
            .putExtra(EXTRA_DAY, day))
    }

    private fun editTrip() {
        val trip = viewModel.trip.value ?: return
        startActivity(Intent(this, TripFormActivity::class.java).putExtra(EXTRA_TRIP_ID, trip.id))
    }

    // ---------- PlaceAdapter.Listener (기존과 동일) ----------
    override fun mapLabel(place: Place) = MapLauncher.mapName(this, viewModel.isDomestic(place))

    /** 국내면 카카오맵, 해외면 구글맵 (API 키 필요 없음) */
    override fun onOpenMap(place: Place) {
        val point = if (place.hasLocation) MapPoint(place.name, place.lat, place.lng) else null
        MapLauncher.openPlace(this, place.name, point, viewModel.isDomestic(place))
    }

    override fun onEditMemo(place: Place) {
        showFormDialog(this, getString(R.string.place_memo_title, place.name), listOf(Field(getString(R.string.action_memo), place.memo, minLines = 2))) { (memo) ->
            viewModel.updateMemo(place, memo)
            true
        }
    }

    override fun onEdit(place: Place) = openPlaceEditor(place)

    override fun onSetDay(place: Place) =
        showDayPicker(this, viewModel.trip.value?.startDate, viewModel.dayCount(), allowNone = true) { day -> viewModel.setDay(place, day) }

    override fun onToggleStatus(place: Place) {
        if (place.status == PlaceStatus.CANDIDATE)
            showDayPicker(this, viewModel.trip.value?.startDate, viewModel.dayCount(), allowNone = true) { day -> viewModel.confirm(place, day) }
        else viewModel.backToCandidate(place)
    }

    override fun onDelete(place: Place) =
        confirmDialog(this, getString(R.string.place_delete_confirm, place.name)) { viewModel.deletePlace(place) }

    /** 여행 전체의 '하고 싶은 거' 메모 */
    private fun showTripNoteDialog() {
        showFormDialog(this, getString(R.string.menu_note),
            listOf(Field(getString(R.string.note_placeholder), viewModel.trip.value?.note ?: "", minLines = 4, trim = false))
        ) { (note) ->
            viewModel.updateNote(note)
            true
        }
    }
}
