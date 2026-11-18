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
import com.tripplanner.route.LegMode
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
    /** 하루의 출발 · 도착 = 그날 순서의 첫 · 마지막 장소 */
    val endpoints: DayEndpoints,
    val options: List<RouteOption>,
    val empty: EmptyState?,        // 경로를 못 만들 때 빈 상태
    val notes: List<String>,       // 위치 없음으로 빠진 장소 등
    val startMinutes: Int = 600,   // 그날 첫 장소 출발 시각
    val endMinutes: Int? = null,   // 마지막 장소 도착 목표 시각 (null = 자유)
    /** 이동 시간을 어디서 얻었는지: 모두 길찾기 · 일부 · 어림 */
    val source: RouteSource = RouteSource.ESTIMATE
)

enum class RouteSource { API, PARTIAL, ESTIMATE }

class RouteViewModel(app: Application, private val state: SavedStateHandle) : AndroidViewModel(app) {

    /** 화면 문구(strings.xml)를 읽을 때 쓰는 앱 컨텍스트 */
    private val ctx: Application get() = getApplication()

    private val repo = (app as TripPlannerApp).repository
    val tripId: Long = state.get<Long>(TripDetailActivity.EXTRA_TRIP_ID) ?: -1L

    val ui = MutableLiveData<RouteUiState>()
    val applied = MutableLiveData<String?>()
    /** 경로 계산 중 */
    val loading = MutableLiveData(false)

    /** 비교 카드에서 고른 안 ("A"/"B"/"C") — 회전해도 유지 */
    val selectedKey: MutableLiveData<String> = state.getLiveData("option", "A")

    /** 여행 상세에서 고른 일차 (화면 안에서는 바꾸지 않음) */
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

    /** 도착 목표 시각 바꾸기 (null = 자유) → 여행에 저장하고 다시 계산 */
    fun setEndTime(minutes: Int?) = viewModelScope.launch {
        val t = trip ?: return@launch
        if (t.endMinutes == minutes) return@launch
        val updated = t.copy(endMinutes = minutes)
        repo.updateTrip(updated)
        trip = updated
        compute()
    }

    /** 시간표에서 떠나는 시각을 고르면 머무는 시간(떠남 − 도착, 최소 10분)으로 장소에 저장 */
    fun setLeaveTime(placeId: Long, arrive: Int, leave: Int) = viewModelScope.launch {
        val p = places.firstOrNull { it.id == placeId } ?: return@launch
        val stay = (leave - arrive).coerceIn(10, 720)
        if (stay == p.stayMinutes) return@launch
        repo.savePlace(p.copy(stayMinutes = stay))
        places = repo.getPlaces(tripId)
            .sortedWith(compareBy<Place>({ it.orderInDay == null }, { it.orderInDay }, { it.id }))
        compute()
    }

    fun selectOption(key: String) { selectedKey.value = key }

    fun allLodgings(): List<Lodging> = lodgings
    fun dayPlaces(): List<Place> = places.filter { it.status == PlaceStatus.CONFIRMED && it.day == selectedDay }

    // ---------------- 구간 이동 수단 (기기에 여행마다 저장) ----------------
    private val legModes = LegModes(app, tripId)

    /** 이 구간의 수단 (고르지 않았으면 거리로 정함) */
    fun legMode(from: Long, to: Long, km: Double): LegMode = legModes.mode(from, to, km)

    /** 시간표 한 줄: 수단 · 시간 · 거리 · 노선 (길찾기 결과가 있으면 그것) */
    fun legInfo(from: Long, to: Long, km: Double): LegInfo {
        val mode = legModes.mode(from, to, km)
        val api = apiLeg(from, to, km, mode)
        return if (api == null) LegInfo(mode, com.tripplanner.route.RoutePlanner.modeMinutes(mode, km), km)
        else LegInfo(mode, api.minutes, if (api.meters > 0) api.meters / 1000.0 else km,
            if (mode == LegMode.TRANSIT) Directions.linesLabel(api.lines) else null)
    }

    /** 수단 메뉴의 '약 n분': 받아 둔 길찾기 결과가 있으면 그 값 */
    fun modeMinutes(from: Long, to: Long, km: Double, mode: LegMode): Pair<Int, String?> {
        val api = apiLeg(from, to, km, mode)
        return (api?.minutes ?: com.tripplanner.route.RoutePlanner.modeMinutes(mode, km)) to
            (if (mode == LegMode.TRANSIT) api?.let { Directions.linesLabel(it.lines) } else null)
    }

    // ---------------- 길찾기 (Google Routes API) ----------------
    private var currentEnds = com.tripplanner.route.Ends(null, null)
    private var coords: Map<Long, Directions.Point> = emptyMap()
    /** Routes API 를 쓸 수 없음 (꺼져 있거나 키 제한) — 이번 화면에서는 다시 묻지 않음 */
    private var routesOff = false
    private var fetching = false

    /** 구간 끝점 좌표: 숙소 id(-1)는 출발이면 시작점, 도착이면 도착점 */
    private fun point(id: Long, asDestination: Boolean): Directions.Point? =
        if (id == com.tripplanner.route.RoutePlanner.ANCHOR_ID)
            (if (asDestination) currentEnds.end else currentEnds.start)?.let { Directions.Point(it.lat, it.lng) }
        else coords[id]

    private fun apiLeg(from: Long, to: Long, km: Double, mode: LegMode = legModes.mode(from, to, km)): Directions.Leg? {
        val a = point(from, false) ?: return null
        val b = point(to, true) ?: return null
        return (Directions.cached(ctx, mode, a, b) as? Directions.Cached.Hit)?.leg
    }

    /** 구간 시간: 길찾기 결과, 없으면 수단별 어림 */
    private val legTime: com.tripplanner.route.LegTime = { f, to, km ->
        val mode = legModes.mode(f, to, km)
        apiLeg(f, to, km, mode)?.minutes ?: com.tripplanner.route.RoutePlanner.modeMinutes(mode, km)
    }

    /** 순서 비교에 쓰는 구간: 장소 사이 모든 방향 + 시작점 → 장소 + 장소 → 도착점 (장소가 많으면 생략) */
    private fun corePairs(e: com.tripplanner.route.Ends, stops: List<com.tripplanner.route.Stop>): List<Triple<Long, Long, Double>> {
        if (stops.size > 7) return emptyList()
        val out = mutableListOf<Triple<Long, Long, Double>>()
        for (a in stops) for (b in stops) if (a.id != b.id) out += Triple(a.id, b.id, com.tripplanner.route.RoutePlanner.legKm(a.lat, a.lng, b.lat, b.lng))
        e.start?.let { s -> stops.forEach { out += Triple(com.tripplanner.route.RoutePlanner.ANCHOR_ID, it.id, com.tripplanner.route.RoutePlanner.legKm(s.lat, s.lng, it.lat, it.lng)) } }
        e.end?.let { d -> stops.forEach { out += Triple(it.id, com.tripplanner.route.RoutePlanner.ANCHOR_ID, com.tripplanner.route.RoutePlanner.legKm(it.lat, it.lng, d.lat, d.lng)) } }
        return out
    }

    private fun fetchMissing(date: java.time.LocalDate?, startMinutes: Int, pairs: List<Triple<Long, Long, Double>>) {
        if (routesOff || fetching || !Directions.available()) return
        val req = pairs.mapNotNull { (f, to, km) ->
            val a = point(f, false) ?: return@mapNotNull null
            val b = point(to, true) ?: return@mapNotNull null
            Triple(legModes.mode(f, to, km), a, b)
        }.filter { (m, a, b) -> Directions.cached(ctx, m, a, b) is Directions.Cached.Miss }
        if (req.isEmpty()) return
        fetching = true
        viewModelScope.launch {
            val got = try {
                Directions.fetchAll(ctx, req, Directions.departureDate(date), startMinutes)
            } catch (e: Directions.Unavailable) { routesOff = true; 0 }
            catch (e: Exception) { 0 }
            fetching = false
            if (got > 0 || routesOff) compute()
        }
    }

    /** 구간 수단 고르기. legs = 바꿀 구간들 (남은 구간도 같게면 이 구간부터 끝까지) */
    fun setLegMode(legs: List<Pair<Long, Long>>, mode: LegMode) = viewModelScope.launch {
        legModes.set(legs, mode)
        compute()
    }

    /** 지금 고른 안 (없으면 첫 번째) */
    fun selectedOption(): RouteOption? {
        val options = ui.value?.options.orEmpty()
        return options.firstOrNull { it.key == selectedKey.value } ?: options.firstOrNull()
    }

    /** 시간표의 종류 아이콘·사진용 */
    fun placeOf(id: Long): Place? = places.firstOrNull { it.id == id }

    private fun reload() = viewModelScope.launch {
        val t = repo.getTrip(tripId) ?: return@launch
        trip = t
        places = repo.getPlaces(tripId)
            .sortedWith(compareBy<Place>({ it.orderInDay == null }, { it.orderInDay }, { it.id }))
        lodgings = repo.getLodgings(tripId)
        val n = TimeUtil.dayCount(t.startDate, t.endDate)
        if (selectedDay !in 1..n) selectedDay = 1
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

        // 하루의 출발 · 도착: 그날 순서의 첫 · 마지막 장소 (경로 안은 가운데 순서만 바꿈)
        val endpoints = DayEndpoints.of(todays)
        val ends = endpoints.ends

        if (withLoc.isEmpty()) {
            val empty = if (todays.isEmpty())
                EmptyState(ctx.getString(R.string.empty_route_day_title, day), ctx.getString(R.string.empty_route_day_body),
                    ctx.getString(R.string.empty_route_day_action), EmptyState.Action.GO_BACK)
            else EmptyState(ctx.getString(R.string.empty_route_loc_title), ctx.getString(R.string.empty_route_loc_body),
                    ctx.getString(R.string.back_to_detail), EmptyState.Action.GO_BACK)
            ui.value = RouteUiState(day, dateLabel, endpoints, emptyList(), empty, notes, t.startMinutes, t.endMinutes)
            return
        }

        // 첫 · 마지막 장소가 숙소면 머무는 시간 0 (출발 시각에 바로 떠남 · 도착하면 하루 끝)
        val zeroStay = DayEndpoints.zeroStayIds(todays, lodgings)
        val stops = withLoc.map { it.toStop(date, false).let { s -> if (s.id in zeroStay) s.copy(stayMinutes = 0) else s } }
        val candidates = places.filter { it.status == PlaceStatus.CANDIDATE && it.hasLocation }
            .map { it.toStop(date, true) }
        currentEnds = ends
        coords = (stops + candidates).associate { it.id to Directions.Point(it.lat, it.lng) }

        // 순서 비교에 쓸 구간들 (시작점 · 장소 · 도착점 사이 모든 방향, 후보 구간은 어림)
        val core = corePairs(ends, stops)
        val transferEstimated = core.any { (f, to, km) -> legModes.mode(f, to, km) == LegMode.TRANSIT && apiLeg(f, to, km) == null }

        // 순서 비교는 계산이 많으니 백그라운드에서
        loading.value = true
        val options = withContext(Dispatchers.Default) { RoutePlanner.plan(null, stops, candidates, t.startMinutes, t.endMinutes, legTime,
            isTransit = { f, to, km -> legModes.mode(f, to, km) == LegMode.TRANSIT }, ends = ends,
            boardings = { f, to, km -> if (legModes.mode(f, to, km) == LegMode.TRANSIT) apiLeg(f, to, km)?.boardings ?: 1 else 0 },
            transferEstimated = transferEstimated) }
        loading.value = false

        // 화면에 보이는 구간이 모두 길찾기 결과인지
        val shownLegs = options.flatMap { o -> RoutePlanner.legs(ends, o.visits.map { it.stop }) }.distinct()
        val known = shownLegs.count { (f, to, km) -> apiLeg(f, to, km) != null }
        val source = when { shownLegs.isEmpty() || known == shownLegs.size -> if (known > 0) RouteSource.API else RouteSource.ESTIMATE
            known == 0 -> RouteSource.ESTIMATE; else -> RouteSource.PARTIAL }
        // 아직 모르는 구간을 길찾기로 받아 오고, 새로 받은 게 있으면 다시 계산
        fetchMissing(date, t.startMinutes, (core + shownLegs).distinct())
        ui.value = RouteUiState(day, dateLabel, endpoints, options, null,
            notes + (if (routesOff) listOf(ctx.getString(R.string.note_routes_off)) else emptyList()),
            t.startMinutes, t.endMinutes, source)
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
