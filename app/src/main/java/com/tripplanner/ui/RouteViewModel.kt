package com.tripplanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tripplanner.R
import com.tripplanner.TripPlannerApp
import com.tripplanner.data.Lodging
import com.tripplanner.data.Place
import com.tripplanner.data.PlaceStatus
import com.tripplanner.data.Trip
import com.tripplanner.route.Anchor
import com.tripplanner.route.RouteOption
import com.tripplanner.route.RoutePlanner
import com.tripplanner.route.Stop
import com.tripplanner.route.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 경로 화면에 보여줄 내용 */
data class RouteUiState(
    val day: Int,
    val dateLabel: String,
    val anchor: Anchor?,
    val options: List<RouteOption>,
    val empty: EmptyState?,        // 경로를 못 만들 때 빈 상태
    val notes: List<String>,       // 위치 없음으로 빠진 장소 등
    val startMinutes: Int = 600    // 그날 숙소 출발 시각
)

class RouteViewModel(app: Application, private val state: SavedStateHandle) : AndroidViewModel(app) {

    /** 화면 문구(strings.xml)를 읽을 때 쓰는 앱 컨텍스트 */
    private val ctx: Application get() = getApplication()

    private val repo = (app as TripPlannerApp).repository
    val tripId: Long = state.get<Long>(TripDetailActivity.EXTRA_TRIP_ID) ?: -1L

    /** 일차 칩 (여행 상세와 같은 모양) */
    val dayChips = MutableLiveData<List<DayChip>>()
    val ui = MutableLiveData<RouteUiState>()
    val applied = MutableLiveData<String?>()
    /** 경로 계산 중 */
    val loading = MutableLiveData(false)

    /** 비교 카드에서 고른 안 ("A"/"B"/"C") — 회전해도 유지 */
    val selectedKey: MutableLiveData<String> = state.getLiveData("option", "A")

    var selectedDay: Int
        get() = state.get<Int>("day") ?: 1
        private set(v) { state["day"] = v }

    private var trip: Trip? = null
    private var places: List<Place> = emptyList()
    private var lodgings: List<Lodging> = emptyList()

    /** 여행 전체(후보 포함)의 장소·숙소 좌표 — 앱 안 지도를 카카오/구글 중 무엇으로 할지 정할 때 사용 */
    fun tripCoords(): List<Pair<Double, Double>> =
        places.filter { it.hasLocation }.map { it.lat to it.lng } +
        lodgings.filter { it.lat != 0.0 || it.lng != 0.0 }.map { it.lat to it.lng }

    init { reload() }

    /** 출발 시각 바꾸기 → 여행에 저장하고 다시 계산 (모든 일차에 같은 시각) */
    fun setStartTime(minutes: Int) = viewModelScope.launch {
        val t = trip ?: return@launch
        if (t.startMinutes == minutes) return@launch
        val updated = t.copy(startMinutes = minutes)
        repo.updateTrip(updated)
        trip = updated
        compute()
    }

    fun selectDay(day: Int) {
        if (day == selectedDay && ui.value?.day == day) return
        selectedDay = day
        selectedKey.value = "A"
        updateChips()
        viewModelScope.launch { compute() }
    }

    fun selectOption(key: String) { selectedKey.value = key }

    /** 지금 고른 안 (없으면 첫 번째) */
    fun selectedOption(): RouteOption? {
        val options = ui.value?.options.orEmpty()
        return options.firstOrNull { it.key == selectedKey.value } ?: options.firstOrNull()
    }

    private fun updateChips() {
        val t = trip ?: return
        val n = TimeUtil.dayCount(t.startDate, t.endDate)
        dayChips.value = (1..n).map { d ->
            val count = places.count { it.status == PlaceStatus.CONFIRMED && it.day == d }
            val date = TimeUtil.dateOfDay(t.startDate, d)
            ctx.dayChip(d, date, count, d == selectedDay)
        }
    }

    private fun reload() = viewModelScope.launch {
        val t = repo.getTrip(tripId) ?: return@launch
        trip = t
        places = repo.getPlaces(tripId)
        lodgings = repo.getLodgings(tripId)
        val n = TimeUtil.dayCount(t.startDate, t.endDate)
        if (selectedDay !in 1..n) selectedDay = 1
        updateChips()
        compute()
    }

    private suspend fun compute() {
        val t = trip ?: return
        val day = selectedDay
        val date = TimeUtil.dateOfDay(t.startDate, day)
        val dateLabel = ctx.dayWithDate(day, date)

        val todays = places.filter { it.status == PlaceStatus.CONFIRMED && it.day == day }
        val withLoc = todays.filter { it.hasLocation }
        val notes = todays.filterNot { it.hasLocation }.map { ctx.getString(R.string.note_no_location, it.name) }

        // 그날 묵는 숙소를 출발점으로 (없으면 위치 있는 첫 숙소)
        val dateStr = date?.toString()
        val lodging = lodgings.firstOrNull { l ->
            l.lat != 0.0 && dateStr != null && l.checkIn <= dateStr && dateStr < l.checkOut
        } ?: lodgings.firstOrNull { it.lat != 0.0 || it.lng != 0.0 }
        val anchor = lodging?.let { Anchor(it.name, it.lat, it.lng) }

        if (withLoc.isEmpty()) {
            val empty = if (todays.isEmpty())
                EmptyState(ctx.getString(R.string.empty_route_day_title, day), ctx.getString(R.string.empty_route_day_body),
                    ctx.getString(R.string.empty_route_day_action), EmptyState.Action.GO_BACK)
            else EmptyState(ctx.getString(R.string.empty_route_loc_title), ctx.getString(R.string.empty_route_loc_body),
                    ctx.getString(R.string.back_to_detail), EmptyState.Action.GO_BACK)
            ui.value = RouteUiState(day, dateLabel, anchor, emptyList(), empty, notes, t.startMinutes)
            return
        }

        val stops = withLoc.map { it.toStop(date, false) }
        val candidates = places.filter { it.status == PlaceStatus.CANDIDATE && it.hasLocation }
            .map { it.toStop(date, true) }

        // 순서 비교는 계산이 많으니 백그라운드에서
        loading.value = true
        val options = withContext(Dispatchers.Default) { RoutePlanner.plan(anchor, stops, candidates, t.startMinutes) }
        loading.value = false
        ui.value = RouteUiState(day, dateLabel, anchor, options, null,
            notes + if (anchor == null) listOf(ctx.getString(R.string.note_no_anchor)) else emptyList(), t.startMinutes)
    }

    /** 선택한 경로의 순서를 저장. C안이면 추가된 후보도 그날 확정으로 옮김 */
    fun apply(option: RouteOption) = viewModelScope.launch {
        val byId = places.associateBy { it.id }
        val updated = option.visits.mapIndexedNotNull { i, v ->
            byId[v.stop.id]?.copy(status = PlaceStatus.CONFIRMED, day = selectedDay, orderInDay = i + 1)
        }
        repo.updatePlaces(updated)
        applied.value = if (option.added.isEmpty()) ctx.getString(R.string.applied_order, option.key)
                        else ctx.getString(R.string.applied_with_added, option.key, option.added.size)
        reload()
    }

    fun appliedShown() { applied.value = null }

    private fun Place.toStop(date: java.time.LocalDate?, candidate: Boolean) = Stop(
        id = id, name = name, lat = lat, lng = lng,
        open = TimeUtil.parseRange(openingHours),
        breakTime = TimeUtil.parseRange(breakTime),
        closedToday = TimeUtil.isClosedOn(closedDays, date),
        stayMinutes = stayMinutes,
        isCandidate = candidate
    )
}
