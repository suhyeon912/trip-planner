package com.tripplanner.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.tripplanner.R
import com.tripplanner.data.Place
import com.tripplanner.data.PlaceStatus

/**
 * 여행 상세 (시안 Detail)
 *  - 상단 바: 여행 이름 / 공유 중 · 코드, 지도로 보기 · 공유
 *  - 탭: 일정 n / 후보 n, 일정 탭에는 일차 칩
 *  - 목록: ('전체'는 교통·숙소 요약) 일차 제목 · 숙소 출발 · 장소 · 숙소 도착 · 여행 메모
 *  - 아래: 그날의 경로 제안 + 장소 추가(+)
 *  - 장소 카드: 누르면 장소 정보, 길게 누르면 수정, ⠿ 끌면 순서 변경
 */
class TripDetailActivity : AppCompatActivity(), PlaceAdapter.Listener, TripDetailMap.Listener {

    companion object {
        const val EXTRA_TRIP_ID = "trip_id"
        /** RouteActivity 가 SavedStateHandle "day" 로 읽는 키 */
        const val EXTRA_DAY = "day"
    }

    private val viewModel: TripDetailViewModel by viewModels()
    private lateinit var tabs: TabLayout
    private lateinit var detailMap: TripDetailMap

    /** 장소 정보에서 '여행 지도에서'를 누르면 그 장소가 보이게 지도를 열고 카드 표시 */
    private val placeView = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = r.data?.getLongExtra(PlaceViewActivity.RESULT_SHOW_ON_MAP, -1L) ?: -1L
        if (r.resultCode != RESULT_OK || id < 0) return@registerForActivityResult
        val place = viewModel.placeById(id) ?: return@registerForActivityResult
        if (place.status == PlaceStatus.CANDIDATE) tabs.getTabAt(1)?.select()
        else {
            tabs.getTabAt(0)?.select()
            val sel = viewModel.selectedDay.value ?: TripDetailViewModel.DAY_ALL
            if (sel != TripDetailViewModel.DAY_ALL && sel != place.day) viewModel.selectDay(TripDetailViewModel.DAY_ALL)
        }
        if (viewModel.mapMode.value != true) viewModel.toggleMapMode()
        detailMap.focus(id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_detail)

        val toolbar = setupToolbar()
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_view_mode -> viewModel.toggleMapMode()
                R.id.action_share -> openShareDialog(viewModel.tripId)
            }
            true
        }
        viewModel.trip.observe(this) { trip ->
            if (trip == null) return@observe
            toolbar.title = trip.title
            if (viewModel.mapMode.value != true) toolbar.subtitle = trip.shareCode?.let { getString(R.string.shared_subtitle, it) }
        }

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
        // 일차 하나를 보고 있을 때: 손잡이를 끌어 순서 바꾸기 → 놓으면 저장
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
            // 길게 누르기는 '장소 수정'이라 끌기는 손잡이로만 시작
            override fun isLongPressDragEnabled() = false
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
        rvPlaces.limitContentWidth()

        // ---------- 아래 버튼 ----------
        val bottom = findViewById<View>(R.id.bottomActions)
        val routeCta = findViewById<MaterialButton>(R.id.btnRouteCta)
        val fab = findViewById<FloatingActionButton>(R.id.fabAddPlace)
        fab.setOnClickListener { openPlaceEditor(null) }

        // ---------- 지도로 보기 ----------
        detailMap = TripDetailMap(findViewById(android.R.id.content), this, this) { cardShown ->
            if (viewModel.mapMode.value != true) bottom.visibility = View.VISIBLE
        }
        fun mapSubtitle(): String? {
            val name = when (detailMap.provider) {
                MapProvider.KAKAO -> getString(R.string.map_kakao)
                MapProvider.GOOGLE -> getString(R.string.map_google)
                MapProvider.OSM -> getString(R.string.map_osm)
                null -> return null
            }
            val day = viewModel.selectedDay.value ?: TripDetailViewModel.DAY_ALL
            return if (day > 0) getString(R.string.map_sub_day, name, dayLabel(day))
            else getString(R.string.map_sub_all, name, viewModel.mapPlaces().let { it.first.size + it.second.size })
        }
        fun renderMap() {
            val mapOn = viewModel.mapMode.value == true
            rvPlaces.visibility = if (mapOn) View.GONE else View.VISIBLE
            detailMap.container.visibility = if (mapOn) View.VISIBLE else View.GONE
            // 지도 보기: 탭 없이 일차 칩(한 줄 + 색 점)만, 아래 버튼 없음
            (tabs.parent as View).visibility = if (mapOn) View.GONE else View.VISIBLE
            chipAdapter.compact = mapOn
            if (mapOn) rvDays.visibility = View.VISIBLE
            bottom.visibility = if (mapOn) View.GONE else View.VISIBLE
            val trip = viewModel.trip.value
            if (!mapOn) {
                toolbar.subtitle = trip?.shareCode?.let { getString(R.string.shared_subtitle, it) }
                return
            }
            val day = viewModel.selectedDay.value ?: TripDetailViewModel.DAY_ALL
            val lodgings = if (day > 0) listOfNotNull(viewModel.lodgingFor(day)) else viewModel.lodgings.value.orEmpty()
            val (places, candidates) = viewModel.mapPlaces()
            detailMap.render(places, candidates, day, lodgings, viewModel.tripCoords())
            toolbar.subtitle = mapSubtitle()
        }
        fun updateBottom() {
            val ui = viewModel.ui.value
            val day = viewModel.selectedDay.value ?: 0
            val onSchedule = viewModel.currentTab.value != PlaceStatus.CANDIDATE
            // 그날을 고른 일정 탭이고 장소가 있을 때만 '경로 제안'
            val showRoute = onSchedule && day > 0 && ui != null && ui.empty == null && viewModel.mapMode.value != true
            routeCta.visibility = if (showRoute) View.VISIBLE else View.GONE
            if (showRoute) {
                routeCta.text = getString(R.string.route_cta, day)
                routeCta.setOnClickListener { openRoute(day) }
            }
        }
        viewModel.mapMode.observe(this) { mapOn ->
            toolbar.menu.findItem(R.id.action_view_mode)?.apply {
                setIcon(if (mapOn) R.drawable.ic_list else R.drawable.ic_map)
                setTitle(if (mapOn) R.string.show_list else R.string.show_map)
            }
            renderMap(); updateBottom()
        }

        viewModel.ui.observe(this) { ui ->
            tabs.getTabAt(0)?.text = getString(R.string.tab_schedule_n, ui.confirmedCount)
            tabs.getTabAt(1)?.text = getString(R.string.tab_candidates_n, ui.candidateCount)
            val onSchedule = viewModel.currentTab.value != PlaceStatus.CANDIDATE
            rvDays.visibility = if (onSchedule || viewModel.mapMode.value == true) View.VISIBLE else View.GONE
            // 후보 탭: 탭 아래 16 띄우고 카드 시작 (카드 위아래 여백 5 포함, 시안 DetailCandidates)
            rvPlaces.setPadding(rvPlaces.paddingLeft, ((if (onSchedule) 4 else 11) * resources.displayMetrics.density).toInt(),
                rvPlaces.paddingRight, rvPlaces.paddingBottom)
            chipAdapter.submit(ui.chips)
            adapter.dragEnabled = onSchedule && (viewModel.selectedDay.value ?: 0) > 0
            adapter.submit(ui.items)
            renderMap(); updateBottom()
        }
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

    // ---------- PlaceAdapter.Listener ----------
    override fun onOpen(place: Place) {
        placeView.launch(Intent(this, PlaceViewActivity::class.java)
            .putExtra(EXTRA_TRIP_ID, viewModel.tripId)
            .putExtra(AddPlaceActivity.EXTRA_PLACE_ID, place.id)
            .putExtra(AddPlaceActivity.EXTRA_DAY_COUNT, viewModel.dayCount()))
    }

    override fun onEdit(place: Place) = openPlaceEditor(place)

    /** 후보 '확정하기' → 누른 자리 아래 일차 select (● n일차 · 11.20 (금) · n곳 / 일차 미정) */
    override fun onConfirmPick(place: Place, anchor: View) {
        val rows = viewModel.dayOptions().map { (day, date, count) ->
            SelectRow.Option(listOf(dayLabel(day), date, getString(R.string.places_count, count))
                .filter { it.isNotEmpty() }.joinToString(getString(R.string.list_separator)),
                dotColor = PinIcons.dayColor(day)) { viewModel.confirm(place, day) }
        } + SelectRow.Option(getString(R.string.day_none), hollowDot = true) { viewModel.confirm(place, null) }
        showSelect(anchor, rows, widthDp = 220)
    }

    override fun onOpenTravel() = openTravelInfo()
    override fun onOpenNote() = showTripNoteDialog()
    override fun onEmptyAction(action: EmptyState.Action) {
        when (action) {
            EmptyState.Action.SHOW_CANDIDATES -> tabs.getTabAt(1)?.select()
            EmptyState.Action.ADD_PLACE -> openPlaceEditor(null)
            EmptyState.Action.GO_BACK -> finish()
            EmptyState.Action.NEW_TRIP -> Unit
        }
    }

    // ---------- TripDetailMap.Listener ----------
    override fun mapLabel(place: Place) = MapLauncher.mapName(this, viewModel.isDomestic(place))
    override fun isDomestic(place: Place) = viewModel.isDomestic(place)

    /** 지도에 없는 장소 → 검색해서 위치 정하기 */
    override fun onFindLocation(place: Place) {
        showPlaceSearch(this, place.name, viewModel.isDomestic(place)) { hit ->
            viewModel.setLocation(place, hit.lat, hit.lng)
        }
    }

    /** 국내면 카카오맵, 해외면 Google Maps (API 키 필요 없음) */
    override fun onOpenMap(place: Place) {
        val point = if (place.hasLocation) MapPoint(place.name, place.lat, place.lng) else null
        MapLauncher.openPlace(this, place.name, point, viewModel.isDomestic(place))
    }

    /** 여행 메모 */
    private fun showTripNoteDialog() {
        showFormDialog(this, getString(R.string.menu_note),
            listOf(Field(getString(R.string.memo_label), viewModel.trip.value?.note ?: "", minLines = 4, trim = false))
        ) { (note) ->
            viewModel.updateNote(note)
            true
        }
    }
}
