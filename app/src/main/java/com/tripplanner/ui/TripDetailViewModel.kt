package com.tripplanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tripplanner.R
import com.tripplanner.TripPlannerApp
import com.tripplanner.data.Lodging
import com.tripplanner.data.Place
import com.tripplanner.data.PlaceStatus
import com.tripplanner.route.Region
import com.tripplanner.route.TimeUtil
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 상세 화면 목록 + 빈 상태 + 일차 칩을 한 번에 담는 화면 상태 */
data class DetailUi(
    val items: List<DetailItem>,
    val empty: EmptyState?,
    val chips: List<DayChip>,
    val confirmedCount: Int,
    val candidateCount: Int
)

/**
 * 여행 상세 화면의 데이터 담당.
 * SavedStateHandle 에는 화면을 열 때 putExtra 로 넘긴 값(trip_id)이 자동으로 들어 있습니다.
 * 3단계: 일차 선택(selectedDay)은 화면 상태일 뿐이고 저장 로직은 그대로입니다.
 */
class TripDetailViewModel(app: Application, private val state: SavedStateHandle) : AndroidViewModel(app) {

    /** 화면 문구(strings.xml)를 읽을 때 쓰는 앱 컨텍스트 */
    private val ctx: Application get() = getApplication()

    companion object {
        const val DAY_ALL = 0       // 일차 칩 '전체'
        const val DAY_NONE = -1     // 일차 칩 '일차 미정'
    }

    private val repo = (app as TripPlannerApp).repository
    val tripId: Long = state.get<Long>(TripDetailActivity.EXTRA_TRIP_ID) ?: -1L

    val trip = repo.observeTrip(tripId)
    val flights = repo.observeFlights(tripId)
    val lodgings = repo.observeLodgings(tripId)
    private val places = repo.observePlaces(tripId)

    /** 지금 보고 있는 탭 (확정 / 후보) — 회전해도 유지 */
    val currentTab = MutableLiveData(PlaceStatus.CONFIRMED)

    /** 일정 탭에서 고른 일차 — 회전·프로세스 재시작에도 유지 */
    val selectedDay: MutableLiveData<Int> = state.getLiveData("selected_day", DAY_ALL)

    /** 목록 대신 지도로 보는 중인지 — 회전해도 유지 */
    val mapMode: MutableLiveData<Boolean> = state.getLiveData("map_mode", false)
    fun toggleMapMode() { mapMode.value = mapMode.value != true }

    /** 장소 + 탭 + 일차 + 여행 날짜 + 숙소 → 화면 상태 */
    val ui: LiveData<DetailUi> = MediatorLiveData<DetailUi>().apply {
        fun update() { value = buildUi() }
        addSource(places) { update() }
        addSource(currentTab) { update() }
        addSource(selectedDay) { update() }
        addSource(trip) { update() }
        addSource(lodgings) { update() }
    }

    fun setTab(status: PlaceStatus) { currentTab.value = status }
    fun selectDay(key: Int) { selectedDay.value = key }

    /**
     * 이 장소를 카카오맵(국내)으로 열지 구글맵(해외)으로 열지.
     * 위치가 없는 장소는 같은 여행의 다른 장소·숙소 위치로 짐작합니다.
     */
    fun isDomestic(place: Place): Boolean {
        if (place.hasLocation) return Region.isKorea(place.lat, place.lng)
        val coords = places.value.orEmpty().filter { it.hasLocation }.map { it.lat to it.lng } +
                lodgings.value.orEmpty().filter { it.lat != 0.0 || it.lng != 0.0 }.map { it.lat to it.lng }
        if (coords.isEmpty()) return true
        return coords.count { (lat, lng) -> Region.isKorea(lat, lng) } * 2 >= coords.size
    }

    /** 여행 전체(후보 포함)의 장소·숙소 좌표 — 앱 안 지도를 카카오/구글 중 무엇으로 할지 정할 때 사용 */
    fun tripCoords(): List<Pair<Double, Double>> =
        places.value.orEmpty().filter { it.hasLocation }.map { it.lat to it.lng } +
        lodgings.value.orEmpty().filter { it.lat != 0.0 || it.lng != 0.0 }.map { it.lat to it.lng }

    fun dayCount(): Int = trip.value?.let { TimeUtil.dayCount(it.startDate, it.endDate) } ?: 7

    /** "2/12(금)" — 날짜를 못 읽으면 null */
    fun shortDate(day: Int): String? {
        val start = trip.value?.startDate ?: return null
        val d = TimeUtil.dateOfDay(start, day) ?: return null
        return ctx.getString(R.string.short_date, d.monthValue, d.dayOfMonth, TimeUtil.koreanDayName(d).first().toString())
    }

    /** 후보 → 확정(일차 지정) / 확정 → 후보 */
    fun confirm(place: Place, day: Int?) = save(place.copy(status = PlaceStatus.CONFIRMED, day = day, orderInDay = null))
    fun backToCandidate(place: Place) = save(place.copy(status = PlaceStatus.CANDIDATE, day = null, orderInDay = null))
    fun setDay(place: Place, day: Int?) = save(place.copy(day = day, orderInDay = null))
    /** 그날 장소를 끌어서 바꾼 순서대로 저장 (1, 2, 3 …) */
    fun reorderDay(orderedIds: List<Long>) = viewModelScope.launch {
        val byId = places.value.orEmpty().associateBy { it.id }
        val updated = orderedIds.mapIndexedNotNull { i, id -> byId[id]?.copy(orderInDay = i + 1) }
        if (updated.isNotEmpty()) repo.updatePlaces(updated)
    }

    fun updateMemo(place: Place, memo: String) = save(place.copy(memo = memo))
    fun deletePlace(place: Place) = viewModelScope.launch { repo.deletePlace(place) }

    fun updateNote(note: String) = viewModelScope.launch {
        trip.value?.let { repo.updateTrip(it.copy(note = note)) }
    }

    /** 여행 이름·날짜 수정 (목록 화면의 '여행 수정'과 같은 저장) */

    private fun save(place: Place) = viewModelScope.launch { repo.savePlace(place) }

    // ------------------------------------------------------------------
    private fun buildUi(): DetailUi {
        val all = places.value.orEmpty()
        val confirmed = all.filter { it.status == PlaceStatus.CONFIRMED }   // DAO 가 일차·순서대로 정렬해 줌
        val candidates = all.filter { it.status == PlaceStatus.CANDIDATE }
        val days = dayCount()
        val hasUndated = confirmed.any { it.day == null }

        // 날짜를 바꿔 고른 일차가 없어졌으면 전체로
        var sel = selectedDay.value ?: DAY_ALL
        if (sel > days || (sel == DAY_NONE && !hasUndated)) sel = DAY_ALL

        val chips = buildChips(confirmed, days, hasUndated, sel)

        if (currentTab.value == PlaceStatus.CANDIDATE) {
            val items = candidates.map { DetailItem.PlaceRow(it, order = null, isFirst = false, isLast = false, timeline = false) }
            val empty = if (candidates.isEmpty())
                EmptyState(ctx.getString(R.string.empty_candidates_title), ctx.getString(R.string.empty_candidates_body),
                    ctx.getString(R.string.add_place), EmptyState.Action.ADD_PLACE) else null
            return DetailUi(items, empty, chips, confirmed.size, candidates.size)
        }

        val items = mutableListOf<DetailItem>()
        var empty: EmptyState? = null
        when (sel) {
            DAY_ALL -> {
                if (confirmed.isEmpty()) {
                    empty = EmptyState(ctx.getString(R.string.empty_confirmed_title), ctx.getString(R.string.empty_confirmed_body),
                        ctx.getString(R.string.show_candidates), EmptyState.Action.SHOW_CANDIDATES)
                } else {
                    confirmed.groupBy { it.day }.forEach { (day, group) ->
                        if (day == null) items += DetailItem.Header(ctx.getString(R.string.day_none), ctx.getString(R.string.header_no_day_sub, group.size))
                        else items += DetailItem.Header(ctx.dayLabel(day), day = day, sub =
                            listOfNotNull(shortDate(day), ctx.getString(R.string.places_count, group.size))
                                .joinToString(ctx.getString(R.string.list_separator)))
                        items += rows(group, timeline = day != null)
                        if (day != null) items += DetailItem.RouteCta(day)
                    }
                }
            }
            DAY_NONE -> {
                items += DetailItem.Header(ctx.getString(R.string.day_none), ctx.getString(R.string.header_no_day_hint))
                items += rows(confirmed.filter { it.day == null }, timeline = false)
            }
            else -> {
                val today = confirmed.filter { it.day == sel }
                if (today.isEmpty()) {
                    empty = EmptyState(ctx.getString(R.string.empty_day_title, sel), ctx.getString(R.string.empty_day_body),
                        ctx.getString(R.string.show_candidates), EmptyState.Action.SHOW_CANDIDATES)
                } else {
                    lodgingFor(sel)?.let { items += DetailItem.Start(ctx.getString(R.string.start_from, it.name)) }
                    items += rows(today, timeline = true)
                    items += DetailItem.RouteCta(sel)
                }
            }
        }
        return DetailUi(items, empty, chips, confirmed.size, candidates.size)
    }

    private fun rows(list: List<Place>, timeline: Boolean) = list.mapIndexed { i, p ->
        // 방문 순서가 아직 없으면 목록 순서로 번호 (경로 확정·끌어서 바꾸면 저장된 순서)
        DetailItem.PlaceRow(p, order = if (timeline && p.day != null) (p.orderInDay ?: (i + 1)).toString() else null,
            isFirst = i == 0, isLast = i == list.lastIndex, timeline = timeline)
    }

    private fun buildChips(confirmed: List<Place>, days: Int, hasUndated: Boolean, sel: Int): List<DayChip> {
        val all = ctx.getString(R.string.day_all)
        val chips = mutableListOf(DayChip(DAY_ALL, all, ctx.getString(R.string.chip_sub, confirmed.size),
            ctx.getString(R.string.chip_a11y, all, confirmed.size), sel == DAY_ALL))
        val start = trip.value?.startDate
        for (d in 1..days) {
            val n = confirmed.count { it.day == d }
            val date: LocalDate? = start?.let { TimeUtil.dateOfDay(it, d) }
            chips += ctx.dayChip(d, date, n, sel == d)
        }
        if (hasUndated) {
            val n = confirmed.count { it.day == null }
            val none = ctx.getString(R.string.day_none)
            chips += DayChip(DAY_NONE, none, ctx.getString(R.string.chip_sub, n), ctx.getString(R.string.chip_a11y, none, n), sel == DAY_NONE)
        }
        return chips
    }

    /** 그날 묵는 숙소(경로 화면과 같은 기준), 없으면 첫 숙소 */
    fun lodgingFor(day: Int): Lodging? {
        val list = lodgings.value.orEmpty()
        val date = trip.value?.startDate?.let { TimeUtil.dateOfDay(it, day) }?.toString()
        return list.firstOrNull { date != null && it.checkIn <= date && date < it.checkOut } ?: list.firstOrNull()
    }
}
