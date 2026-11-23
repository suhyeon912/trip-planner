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
import com.tripplanner.ui.widget.TpButton
import com.tripplanner.ui.widget.TpFab
import com.tripplanner.ui.widget.TpTabs
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
    private lateinit var tabs: TpTabs
    private lateinit var detailMap: TripDetailMap

    /** 장소 정보에서 '여행 지도에서'를 누르면 그 장소가 보이게 지도를 열고 카드 표시 */
    private val placeView = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = r.data?.getLongExtra(PlaceViewActivity.RESULT_SHOW_ON_MAP, -1L) ?: -1L
        if (r.resultCode != RESULT_OK || id < 0) return@registerForActivityResult
        val place = viewModel.placeById(id) ?: return@registerForActivityResult
        if (place.status == PlaceStatus.CANDIDATE) tabs.select(1)
        else {
            tabs.select(0)
            val sel = viewModel.selectedDay.value ?: TripDetailViewModel.DAY_ALL
            if (sel != TripDetailViewModel.DAY_ALL && sel != place.day) viewModel.selectDay(TripDetailViewModel.DAY_ALL)
        }
        if (viewModel.mapMode.value != true) viewModel.toggleMapMode()
        detailMap.focus(id)
    }

    /** 구간 이동 수단 (지도 시간 이름표 계산용, 경로 제안과 같은 값) */
    private val legModes by lazy { LegModes(this, intent.getLongExtra(EXTRA_TRIP_ID, -1L)) }

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
            if (viewModel.mapMode.value != true) toolbar.subtitle = sharedSubtitle(trip)
        }

        // ---------- 탭 ----------
        tabs = findViewById(R.id.tabs)
        tabs.addTab(getString(R.string.tab_schedule))
        tabs.addTab(getString(R.string.tab_candidates))
        if (viewModel.currentTab.value == PlaceStatus.CANDIDATE) tabs.select(1, animate = false, notify = false)
        tabs.onSelect = { i -> viewModel.setTab(if (i == 0) PlaceStatus.CONFIRMED else PlaceStatus.CANDIDATE) }

        // ---------- 일차 칩 ----------
        val rvDays = findViewById<RecyclerView>(R.id.rvDays)
        lateinit var chipAdapter: DayChipAdapter
        chipAdapter = DayChipAdapter { key -> if (chipAdapter.kindMode) viewModel.selectKind(key) else viewModel.selectDay(key) }
        rvDays.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvDays.adapter = chipAdapter

        // ---------- 장소 목록 ----------
        val adapter = PlaceAdapter(this) { day -> openRoute(day) }
        val rvPlaces = findViewById<RecyclerView>(R.id.rvPlaces)
        rvPlaces.layoutManager = LinearLayoutManager(this)
        rvPlaces.adapter = adapter
        // 일차 하나를 보고 있을 때: 손잡이를 끌어 순서 바꾸기 → 놓으면 저장
        // 왼쪽 순서 점은 제자리에 두고 카드만 움직임 (끄는 카드 · 밀려나는 카드 모두)
        var moved = false
        var dragging: RecyclerView.ViewHolder? = null
        rvPlaces.itemAnimator = CardOnlyMoveAnimator { dragging }
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
            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && vh is PlaceAdapter.PlaceHolder) {
                    dragging = vh
                    // 들어 올린 느낌: 그림자 (크기는 눌림 효과가 맡음)
                    vh.card.animate().translationZ(8 * resources.displayMetrics.density)
                        .setDuration(resources.getInteger(R.integer.motion_short).toLong()).start()
                    vh.card.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                }
            }
            // 끄는 동안: 줄 전체가 아니라 카드만 손가락을 따라감
            override fun onChildDraw(c: android.graphics.Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                     dX: Float, dY: Float, actionState: Int, active: Boolean) {
                if (vh is PlaceAdapter.PlaceHolder) vh.card.translationY = dY
                else super.onChildDraw(c, rv, vh, dX, dY, actionState, active)
            }
            // 길게 누르기는 '장소 수정'이라 끌기는 손잡이로만 시작
            override fun isLongPressDragEnabled() = false
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                dragging = null
                if (vh is PlaceAdapter.PlaceHolder) {
                    vh.card.translationY = 0f
                    vh.card.animate().translationZ(0f)
                        .setDuration(resources.getInteger(R.integer.motion_short).toLong()).start()
                }
                if (moved) {
                    moved = false
                    Haptics.dragEnd(vh.itemView)
                    viewModel.reorderDay(adapter.currentItems.filterIsInstance<DetailItem.PlaceRow>().map { it.place.id })
                }
            }
        })
        touch.attachToRecyclerView(rvPlaces)
        adapter.onStartDrag = { touch.startDrag(it) }
        rvPlaces.limitContentWidth()

        // ---------- 아래 버튼 ----------
        val bottom = findViewById<View>(R.id.bottomActions)
        val routeCta = findViewById<TpButton>(R.id.btnRouteCta)
        val fab = findViewById<TpFab>(R.id.fabAddPlace)
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
            chipAdapter.compact = mapOn || chipAdapter.kindMode
            if (mapOn) rvDays.visibility = View.VISIBLE
            bottom.visibility = if (mapOn) View.GONE else View.VISIBLE
            val trip = viewModel.trip.value
            if (!mapOn) {
                toolbar.subtitle = trip?.let { sharedSubtitle(it) }
                return
            }
            val day = viewModel.selectedDay.value ?: TripDetailViewModel.DAY_ALL
            // n일차: 그날 장소만 (숙소에서 출발하는 날은 숙소도 장소로 들어 있음) / 전체: 숙소도 점으로
            val ends = if (day > 0) viewModel.endpoints(day) else null
            val lodgings = if (ends != null) emptyList() else viewModel.lodgings.value.orEmpty()
            val (places, candidates) = viewModel.mapPlaces()
            legModes.reload()   // 경로 제안에서 바꿨을 수 있음
            // 구간 시간: 경로 제안에서 받아 둔 길찾기 결과가 있으면 그것 (같은 시각이 보이게), 없으면 어림
            val e = ends?.ends
            val pts = places.associate { it.id to Directions.Point(it.lat, it.lng) }
            val legTime: com.tripplanner.route.LegTime = { f, t, km ->
                val mode = legModes.mode(f, t, km)
                val a = if (f == com.tripplanner.route.RoutePlanner.ANCHOR_ID) e?.start?.let { Directions.Point(it.lat, it.lng) } else pts[f]
                val b = if (t == com.tripplanner.route.RoutePlanner.ANCHOR_ID) e?.end?.let { Directions.Point(it.lat, it.lng) } else pts[t]
                val api = if (a != null && b != null) (Directions.cached(this, mode, a, b) as? Directions.Cached.Hit)?.leg else null
                api?.minutes ?: com.tripplanner.route.RoutePlanner.modeMinutes(mode, km)
            }
            detailMap.render(places, candidates, day, lodgings, viewModel.tripCoords(),
                startMinutes = trip?.startMinutes ?: com.tripplanner.route.RoutePlanner.DEFAULT_START, legTime = legTime,
                ends = e, zeroStay = if (day > 0) viewModel.zeroStayIds(day) else emptySet())
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
        // 지도 보기에서 뒤로(상단 ← 또는 시스템 뒤로) → 목록으로 돌아감 (여행 상세를 닫지 않음)
        val backToList = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() { viewModel.toggleMapMode() }
        }
        onBackPressedDispatcher.addCallback(this, backToList)
        viewModel.mapMode.observe(this) { mapOn ->
            backToList.isEnabled = mapOn
            toolbar.menu.findItem(R.id.action_view_mode)?.apply {
                setIcon(if (mapOn) R.drawable.ic_list else R.drawable.ic_map)
                setTitle(if (mapOn) R.string.show_list else R.string.show_map)
                // 시안 DetailMap: 지도 보기 중에는 '목록' 버튼이 연파랑 동그라미 (지금 지도 보기라는 표시)
                actionView = if (!mapOn) null else android.widget.ImageButton(this@TripDetailActivity).apply {
                    val d = resources.displayMetrics.density
                    setImageResource(R.drawable.ic_list)
                    imageTintList = android.content.res.ColorStateList.valueOf(color(R.color.on_primary_container))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(color(R.color.primary_light))
                    }
                    foreground = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.press_overlay_borderless)
                    contentDescription = getString(R.string.show_list)
                    layoutParams = android.view.ViewGroup.MarginLayoutParams((48 * d).toInt(), (48 * d).toInt()).apply { marginEnd = (8 * d).toInt() }
                    setOnClickListener { viewModel.toggleMapMode() }
                }
            }
            // 지도 보기에서는 공유 버튼을 숨김 (시안)
            toolbar.menu.findItem(R.id.action_share)?.isVisible = !mapOn
            renderMap(); updateBottom()
        }

        viewModel.ui.observe(this) { ui ->
            tabs.setTabText(0, getString(R.string.tab_schedule_n, ui.confirmedCount))
            tabs.setTabText(1, getString(R.string.tab_candidates_n, ui.candidateCount))
            val onSchedule = viewModel.currentTab.value != PlaceStatus.CANDIDATE
            val mapOn = viewModel.mapMode.value == true
            // 후보 탭: 종류가 2가지 이상이면 종류 칩 줄 (전체 · 맛집 3 · 카페 2)
            val kinds = !onSchedule && !mapOn && ui.kindChips.isNotEmpty()
            chipAdapter.kindMode = kinds
            chipAdapter.compact = mapOn || kinds
            rvDays.visibility = if (onSchedule || mapOn || kinds) View.VISIBLE else View.GONE
            // 후보 탭: 탭 아래 16 띄우고 카드 시작 (카드 위아래 여백 5 포함, 시안 DetailCandidates)
            rvPlaces.setPadding(rvPlaces.paddingLeft, ((if (onSchedule || kinds) 4 else 11) * resources.displayMetrics.density).toInt(),
                rvPlaces.paddingRight, rvPlaces.paddingBottom)
            val shownChips = if (kinds) ui.kindChips else ui.chips
            chipAdapter.submit(shownChips)
            // 고른 일차 칩이 화면 밖이면 보이게 밀어 줌 (일차가 많을 때)
            shownChips.indexOfFirst { it.selected }.takeIf { it >= 0 }?.let { i ->
                val lm = rvDays.layoutManager as LinearLayoutManager
                if (i < lm.findFirstCompletelyVisibleItemPosition() || i > lm.findLastCompletelyVisibleItemPosition()) rvDays.smoothScrollToPosition(i)
            }
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
                dotColor = PinIcons.dayColor(day)) { Haptics.confirm(anchor); viewModel.confirm(place, day) }
        } + SelectRow.Option(getString(R.string.day_none), hollowDot = true) { Haptics.confirm(anchor); viewModel.confirm(place, null) }
        showSelect(anchor, rows, widthDp = 220)
    }

    override fun onOpenTravel() = openTravelInfo()

    override fun onOpenNote() = showTripNoteDialog()
    override fun onEmptyAction(action: EmptyState.Action) {
        when (action) {
            EmptyState.Action.SHOW_CANDIDATES -> tabs.select(1)
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

    /** '공유 중 · 코드' 는 다른 사람과 함께 볼 때만 (혼자 쓰는 여행도 백업용 코드가 있어서) */
    private fun sharedSubtitle(t: com.tripplanner.data.Trip): String? =
        t.shareCode?.takeIf { !t.ownedByMe || t.members.isNotEmpty() }?.let { getString(R.string.shared_subtitle, it) }
}
