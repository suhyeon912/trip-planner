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
    val candidateCount: Int,
    /** 후보 탭: 종류 칩 ('전체 · 맛집 3 · 카페 2', 종류가 2가지 이상일 때만, 아니면 빈 목록) */
    val kindChips: List<DayChip> = emptyList()
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
        const val KIND_ALL = 0      // 종류 칩 '전체'

        /** 종류 칩 key: 기본 종류 -(10 + 순서), 내가 만든 종류 -(1000 + id) */
        fun kindKey(p: Place): Int = PlaceKinds.type(p)?.let { -(1000 + it.id.toInt()) } ?: -(10 + p.category.ordinal)
    }

    private val repo = (app as TripPlannerApp).repository
    val tripId: Long = state.get<Long>(TripDetailActivity.EXTRA_TRIP_ID) ?: -1L

    val trip = repo.observeTrip(tripId)
    val flights = repo.observeFlights(tripId)
    val lodgings = repo.observeLodgings(tripId)
    private val places = repo.observePlaces(tripId)
    /** 예약·티켓 자료 (장소 카드 '티켓 n' · 전체 요약 줄) */
    private val attachments = repo.observeAttachments(tripId)

    /** 지금 보고 있는 탭 (확정 / 후보) — 회전해도 유지 */
    val currentTab = MutableLiveData(PlaceStatus.CONFIRMED)

    /** 일정 탭에서 고른 일차 — 회전·프로세스 재시작에도 유지 */
    val selectedDay: MutableLiveData<Int> = state.getLiveData("selected_day", DAY_ALL)

    /** 목록 대신 지도로 보는 중인지 — 회전해도 유지 */
    val mapMode: MutableLiveData<Boolean> = state.getLiveData("map_mode", false)
    fun toggleMapMode() { mapMode.value = mapMode.value != true }

    /** 후보 탭에서 고른 종류 (0 = 전체, kindKey) — 회전해도 유지 */
    val candidateKind: MutableLiveData<Int> = state.getLiveData("candidate_kind", KIND_ALL)
    fun selectKind(key: Int) { candidateKind.value = key }

    /** 장소 + 탭 + 일차 + 여행 날짜 + 숙소 → 화면 상태 */
    val ui: LiveData<DetailUi> = MediatorLiveData<DetailUi>().apply {
        fun update() { value = buildUi() }
        addSource(places) { update() }
        addSource(currentTab) { update() }
        addSource(selectedDay) { update() }
        addSource(candidateKind) { update() }
        addSource(mapMode) { update() }
        addSource(trip) { update() }
        addSource(lodgings) { update() }
        addSource(flights) { update() }
        addSource(attachments) { update() }
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

    /** 지도 보기: (고른 일차 또는 전체의 확정 장소, '전체'일 때 후보) */
    fun mapPlaces(): Pair<List<Place>, List<Place>> {
        val all = places.value.orEmpty()
        val confirmed = all.filter { it.status == PlaceStatus.CONFIRMED }
        return when (val sel = selectedDay.value ?: DAY_ALL) {
            DAY_ALL -> confirmed to all.filter { it.status == PlaceStatus.CANDIDATE }
            else -> confirmed.filter { it.day == sel } to emptyList()
        }
    }

    /** 지도에 없는 장소의 '위치 찾기' 결과 저장 (장소 수정의 위치와 같은 칸) */
    fun setLocation(place: Place, lat: Double, lng: Double) = save(place.copy(lat = lat, lng = lng))

    fun placeById(id: Long): Place? = places.value.orEmpty().firstOrNull { it.id == id }

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
        val list = orderedIds.mapNotNull { byId[it] }
        val updated = list.mapIndexed { i, p -> p.copy(orderInDay = i + 1) }
        if (updated.isNotEmpty()) repo.updatePlaces(updated)
    }

    fun updateMemo(place: Place, memo: String) = save(place.copy(memo = memo))
    fun deletePlace(place: Place, onDeleted: (com.tripplanner.data.TripRepository.Undo) -> Unit = {}) =
        viewModelScope.launch { onDeleted(repo.deletePlaceUndoable(place)) }

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

        // 날짜를 바꿔 고른 일차가 없어졌으면 전체로
        var sel = selectedDay.value ?: DAY_ALL
        if (sel > days || sel < 0) sel = DAY_ALL

        val chips = buildChips(confirmed, days, sel)

        if (currentTab.value == PlaceStatus.CANDIDATE) {
            // 종류별로 보기: 후보에 있는 종류만 칩으로 (기본 종류 순서 → 내가 만든 종류 순서)
            val groups = candidates.groupBy { kindKey(it) }.toList()
                .sortedWith(compareBy({ if (it.first > -1000) 0 else 1 }, { -it.first }))
            var kind = candidateKind.value ?: KIND_ALL
            if (groups.none { it.first == kind }) kind = KIND_ALL
            val kindChips = if (groups.size < 2 || mapMode.value == true) emptyList() else {
                val all = ctx.getString(R.string.day_all)
                listOf(DayChip(KIND_ALL, all, "", all, kind == KIND_ALL)) +
                    groups.map { (key, list) ->
                        // 종류 칩은 아이콘만 (이름은 낭독 · 길게 누르면 말풍선)
                        val label = PlaceKinds.label(list.first())
                        DayChip(key, label, "", label, kind == key, icon = PlaceKinds.icon(list.first()), iconSelected = PlaceKinds.iconFill(list.first()))
                    }
            }
            val shown = if (kindChips.isEmpty() || kind == KIND_ALL) candidates else candidates.filter { kindKey(it) == kind }
            val items = shown.map { DetailItem.PlaceRow(it, order = null, isFirst = false, isLast = false, timeline = false) }
            val empty = if (candidates.isEmpty())
                EmptyState(ctx.getString(R.string.empty_candidates_title), ctx.getString(R.string.empty_candidates_body),
                    icon = R.drawable.ic_place) else null
            return DetailUi(items + listOfNotNull(empty?.let { DetailItem.Empty(it) }), empty, chips, confirmed.size, candidates.size, kindChips)
        }

        val items = mutableListOf<DetailItem>()
        var empty: EmptyState? = null
        when (sel) {
            DAY_ALL -> {
                // 맨 위: 교통 · 숙소 요약 (누르면 교통·숙소 화면)
                items += DetailItem.Summary(summaryLines())
                if (confirmed.isEmpty()) {
                    empty = EmptyState(ctx.getString(R.string.empty_confirmed_title), ctx.getString(R.string.empty_confirmed_body),
                        ctx.getString(R.string.show_candidates), EmptyState.Action.SHOW_CANDIDATES, R.drawable.ic_event)
                } else {
                    confirmed.filter { it.day != null }.groupBy { it.day!! }.forEach { (day, group) ->
                        items += DetailItem.Header(ctx.dayLabel(day), headerDate(day), day = day, routeLink = true)
                        items += dayTimeline(day, group)
                    }
                }
            }
            else -> {
                val today = confirmed.filter { it.day == sel }
                if (today.isEmpty()) {
                    empty = EmptyState(ctx.getString(R.string.empty_day_title, sel), ctx.getString(R.string.empty_day_body),
                        ctx.getString(R.string.show_candidates), EmptyState.Action.SHOW_CANDIDATES, R.drawable.ic_event)
                } else {
                    items += DetailItem.Header(ctx.dayLabel(sel), headerDate(sel), day = sel)
                    items += dayTimeline(sel, today)
                }
            }
        }
        // 빈 상태도 목록 안에 (위 요약 · 아래 메모와 함께 보이게)
        empty?.let { items += DetailItem.Empty(it) }
        // 맨 아래: 여행 메모
        items += DetailItem.Memo(trip.value?.note.orEmpty())
        return DetailUi(items, empty, chips, confirmed.size, candidates.size)
    }

    /** 그날 출발 · 도착 = 그날 순서의 첫 · 마지막 장소 */
    fun endpoints(day: Int): DayEndpoints = DayEndpoints.of(dayPlaces(day))

    /** 그날 타임라인: 넣은 순서대로 (첫 장소에서 출발, 마지막 장소에서 끝) */
    private fun dayTimeline(day: Int, list: List<Place>): List<DetailItem> = rows(list, timeline = true)

    /** "11월 20일 (금)" — 날짜를 못 읽으면 빈 글자 */
    private fun headerDate(day: Int): String {
        val start = trip.value?.startDate ?: return ""
        val d = TimeUtil.dateOfDay(start, day) ?: return ""
        return ctx.getString(R.string.day_header_date, d.monthValue, d.dayOfMonth, TimeUtil.koreanDayName(d).first().toString())
    }

    /** '전체' 맨 위 요약: 첫 교통편 · 숙소 */
    private fun summaryLines(): List<SummaryLine> {
        val out = mutableListOf<SummaryLine>()
        flights.value.orEmpty().firstOrNull()?.let { f ->
            val time = f.departAt.trim().substringAfter(' ', "").take(5)
            out += SummaryLine(f.mode.icon, ctx.getString(R.string.summary_transport,
                "${f.fromAirport} → ${f.toAirport}" + if (time.isNotEmpty()) " $time" else ""))
        }
        lodgings.value.orEmpty().takeIf { it.isNotEmpty() }?.let { list ->
            out += SummaryLine(R.drawable.ic_hotel, ctx.getString(R.string.summary_lodging, list.joinToString(", ") { it.name }))
        }
        attachments.value.orEmpty().takeIf { it.isNotEmpty() }?.let { list ->
            val names = list.take(2).joinToString(", ") { it.name.substringBeforeLast('.') }
            out += SummaryLine(R.drawable.ic_ticket, ctx.getString(R.string.summary_tickets,
                if (list.size > 2) ctx.getString(R.string.summary_tickets_more, names, list.size - 2) else names), count = list.size)
        }
        if (out.isEmpty()) out += SummaryLine(R.drawable.ic_route, ctx.getString(R.string.summary_travel_empty))
        return out
    }

    private fun rows(list: List<Place>, timeline: Boolean) = list.mapIndexed { i, p ->
        // 타임라인 번호 = 보이는 순서 (넣은 순서 · 끌어서 바꾼 순서 · 경로 적용 순서)
        DetailItem.PlaceRow(p, order = if (timeline && p.day != null) (i + 1).toString() else null,
            isFirst = i == 0, isLast = i == list.lastIndex, timeline = timeline,
            files = attachments.value.orEmpty().count { it.placeId == p.id })
    }

    private fun buildChips(confirmed: List<Place>, days: Int, sel: Int): List<DayChip> {
        val all = ctx.getString(R.string.day_all)
        val chips = mutableListOf(DayChip(DAY_ALL, all, "",
            ctx.getString(R.string.chip_a11y, all, confirmed.size), sel == DAY_ALL))
        val start = trip.value?.startDate
        for (d in 1..days) {
            val n = confirmed.count { it.day == d }
            val date: LocalDate? = start?.let { TimeUtil.dateOfDay(it, d) }
            chips += ctx.dayChip(d, date, n, sel == d)
        }
        return chips
    }

    /** '확정하기' select 의 일차 목록: (일차, "11.20 (금)", 그날 확정 수) */
    fun dayOptions(): List<Triple<Int, String, Int>> {
        val confirmed = places.value.orEmpty().filter { it.status == PlaceStatus.CONFIRMED }
        val start = trip.value?.startDate
        return (1..dayCount()).map { d ->
            val date = start?.let { TimeUtil.dateOfDay(it, d) }
            val label = date?.let { "${it.monthValue}.${it.dayOfMonth} (${TimeUtil.koreanDayName(it).first()})" } ?: ""
            Triple(d, label, confirmed.count { it.day == d })
        }
    }

    /** 그날 묵는 숙소(경로 화면과 같은 기준), 없으면 첫 숙소 */
    fun lodgingFor(day: Int): Lodging? = DayEndpoints.lodgingFor(trip.value, day, lodgings.value.orEmpty())

    fun allLodgings(): List<Lodging> = lodgings.value.orEmpty()
    /** 첫 · 마지막 장소가 숙소면 머무는 시간 0 (지도 시각 계산용) */
    fun zeroStayIds(day: Int): Set<Long> = DayEndpoints.zeroStayIds(dayPlaces(day), lodgings.value.orEmpty())
    fun dayPlaces(day: Int): List<Place> = places.value.orEmpty().filter { it.status == PlaceStatus.CONFIRMED && it.day == day }
}
