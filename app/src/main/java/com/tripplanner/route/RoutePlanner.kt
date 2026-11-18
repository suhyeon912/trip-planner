package com.tripplanner.route

/** 경로 계산에 쓰는 장소 정보 (Room 엔티티와 분리해 순수 코틀린으로 테스트 가능하게) */
data class Stop(
    val id: Long,
    val name: String,
    val lat: Double,
    val lng: Double,
    val open: IntRange? = null,        // 운영시간 (null = 항상)
    val breakTime: IntRange? = null,   // 브레이크 타임
    val closedToday: Boolean = false,  // 그날 휴무
    val stayMinutes: Int = 60,
    val isCandidate: Boolean = false
)

/** 경로 안의 순서 기준 (시안 A~D 다음에 시간 맞춤 · 후보 포함) */
enum class RouteKind(val title: String, val description: String) {
    SHORTEST("이동 거리 우선", "총 이동거리가 가장 짧은 순서"),
    NEAREST("가까운 곳부터", "숙소에서 가까운 곳부터 차례로"),
    FEWER_TRANSFERS("환승 적은 순", "버스·지하철을 타는 횟수가 가장 적은 순서"),
    CURRENT("지금 순서 그대로", "여행 상세에서 정한 순서 그대로"),
    TIME("시간 맞춤", "브레이크 타임·운영시간을 피하는 순서"),
    CANDIDATES("후보 포함", "가는 길에 들를 수 있는 후보 추가")
}

/** 구간 이동 수단 (시안 RouteLegMode): 도보 · 대중교통 · 택시·차 */
enum class LegMode { WALK, TRANSIT, TAXI }

/**
 * 구간 이동 시간 정하기: (출발 id, 도착 id, 거리 km) → 분. 숙소 id 는 ANCHOR_ID.
 * 기본은 예전과 같은 어림값 (도보+대중교통 평균 15km/h).
 */
typealias LegTime = (from: Long, to: Long, km: Double) -> Int

/** 출발·도착 지점 (보통 숙소) */
/** 출발 · 도착점. lodging = 숙소인지 (아니면 공항 · 역 같은 장소) — 화면 아이콘에만 씀 */
data class Anchor(val name: String, val lat: Double, val lng: Double, val lodging: Boolean = true)

/**
 * 하루의 시작 · 끝.
 *  start / end: 숙소처럼 들르지 않는 출발점 · 도착점 (없으면 첫 장소에서 시작 / 마지막 장소에서 끝)
 *  first / last: 반드시 처음 / 마지막에 들르는 장소 (장소를 시작 · 끝으로 정했을 때)
 */
data class Ends(val start: Anchor?, val end: Anchor?, val firstId: Long? = null, val lastId: Long? = null) {
    companion object { fun of(anchor: Anchor?) = Ends(anchor, anchor) }
}

data class Visit(val stop: Stop, val arrive: Int, val leave: Int, val warning: String?, val waitMinutes: Int = 0)

data class RouteOption(
    val key: String,                 // "A" / "B" / "C"
    val title: String,
    val description: String,
    val visits: List<Visit>,
    val totalKm: Double,
    val moveMinutes: Int,
    val endTime: Int,                // 하루 끝 시각 (도착점이 있으면 그 도착, 없으면 마지막 장소 떠남 · 숙소면 도착)
    val added: List<Stop> = emptyList(),  // C안에서 새로 넣은 후보
    val endLimit: Int? = null,       // 숙소 도착 목표 시각 (null = 자유)
    /** 이 카드가 어떤 기준(들)의 순서인지 — 순서가 같은 기준은 한 카드로 합쳐짐 */
    val kinds: List<RouteKind> = emptyList(),
    /** 카드에 쓰는 기준 이름 (kinds 와 같은 순서, 어림이면 '(어림)' 붙음) */
    val kindTitles: List<String> = kinds.map { it.title }
) {
    /** 목표보다 늦게 도착하는 분 (늦지 않으면 0) */
    val lateMinutes: Int get() = endLimit?.let { maxOf(0, endTime - it) } ?: 0
    val warnings: List<String> get() = visits.mapNotNull { it.warning?.let { w -> "${it.stop.name}: $w" } } +
        if (lateMinutes > 0) listOf("도착 ${TimeUtil.format(endTime)} · 목표 ${TimeUtil.format(endLimit!!)}보다 ${lateMinutes}분 늦음") else emptyList()
}

/**
 * 경로 안을 만듭니다. 실제 길찾기 API 없이 '직선거리 × 1.3'으로 이동거리를 추정합니다.
 *  이동 거리 우선 — 총 이동거리가 가장 짧은 순서
 *  가까운 곳부터 — 숙소에서 가장 가까운 곳부터 차례로
 *  환승 적은 순   — 대중교통 구간이 가장 적은 순서 (지금은 거리로 어림)
 *  지금 순서 그대로 — 여행 상세의 순서
 *  시간 맞춤     — 운영시간·브레이크 타임·휴무를 가장 덜 어기는 순서
 *  후보 포함     — 최단 경로에서 크게 벗어나지 않는 후보 장소를 최대 2곳 끼워 넣음
 * 순서가 같은 안은 카드 하나로 합치고 앞에서부터 A · B · C …
 */
object RoutePlanner {

    const val DEFAULT_START = 10 * 60      // 10:00 출발
    private const val DETOUR_FACTOR = 1.3  // 직선거리 → 실제 이동거리 보정
    private const val SPEED_KMH = 15.0     // 도보+대중교통 평균 속도 가정
    private const val MAX_EXACT = 8        // 이 개수까지는 모든 순서를 다 비교
    const val MAX_DETOUR_KM = 3.0          // 후보 포함: 이만큼 이하로 돌아가는 후보만 추가

    const val ANCHOR_ID = -1L
    val DEFAULT_LEG: LegTime = { _, _, km -> legMinutes(km) }

    /**
     * @param anchor 숙소 (시작 · 끝이 같을 때). 시작 · 끝을 따로 정했으면 ends 를 씀
     * @param boardings 구간에서 버스·지하철을 타는 횟수 ('환승 적은 순'). null 이면 isTransit 으로 어림
     * @param transferEstimated '환승 적은 순'이 어림값인지 (카드 이름에 '(어림)')
     */
    fun plan(
        anchor: Anchor?,
        stops: List<Stop>,
        candidates: List<Stop>,
        startTime: Int = DEFAULT_START,
        endLimit: Int? = null,
        legTime: LegTime = DEFAULT_LEG,
        /** 구간을 대중교통으로 타는지 ('환승 적은 순'용, 기본: 1.2km 넘으면 대중교통) */
        isTransit: (from: Long, to: Long, km: Double) -> Boolean = { _, _, km -> defaultMode(km) == LegMode.TRANSIT },
        ends: Ends = Ends.of(anchor),
        boardings: ((from: Long, to: Long, km: Double) -> Int)? = null,
        transferEstimated: Boolean = true
    ): List<RouteOption> {
        if (stops.isEmpty()) return emptyList()
        val e = normalize(ends, stops)
        val mid = stops.filter { it.id != e.firstId && it.id != e.lastId }
        val board = boardings ?: { f, t, km -> if (isTransit(f, t, km)) 1 else 0 }
        fun title(k: RouteKind) = if (k == RouteKind.FEWER_TRANSFERS && transferEstimated) k.title + "(어림)" else k.title
        fun build(kind: RouteKind, order: List<Stop>, added: List<Stop> = emptyList()): RouteOption {
            val sim = simulate(e, order, startTime, legTime)
            return RouteOption("", title(kind), kind.description, sim.visits, totalKm(e, order), sim.moveMinutes, sim.endTime,
                added, endLimit, kinds = listOf(kind), kindTitles = listOf(title(kind)))
        }
        val shortest = wrap(e, stops, shortestMid(e, stops, mid))
        val all = mutableListOf(
            build(RouteKind.SHORTEST, shortest),
            build(RouteKind.NEAREST, wrap(e, stops, nearestMid(e, stops, mid))),
            build(RouteKind.FEWER_TRANSFERS, wrap(e, stops, fewerTransitMid(e, stops, mid, board))),
            build(RouteKind.CURRENT, wrap(e, stops, mid)),
            build(RouteKind.TIME, wrap(e, stops, timeAwareMid(e, stops, mid, startTime, legTime)))
        )
        val (orderC, added) = insertCandidates(e, stops, shortest, candidates)
        if (added.isNotEmpty()) all += build(RouteKind.CANDIDATES, orderC, added)
        return merge(all)
    }

    /** 처음 · 마지막 장소가 목록에 없거나 같으면 풀어 줌 */
    private fun normalize(e: Ends, stops: List<Stop>): Ends {
        val ids = stops.map { it.id }.toSet()
        val first = e.firstId?.takeIf { it in ids }
        val last = e.lastId?.takeIf { it in ids && it != first }
        return e.copy(firstId = first, lastId = last)
    }

    /** 가운데 순서 → 처음 · 마지막 장소를 붙인 전체 순서 */
    private fun wrap(e: Ends, stops: List<Stop>, mid: List<Stop>): List<Stop> =
        listOfNotNull(stops.firstOrNull { it.id == e.firstId }) + mid + listOfNotNull(stops.firstOrNull { it.id == e.lastId })

    /** 순서가 같은 안(과 추가된 후보)은 카드 하나로 합치고, 앞에서부터 A · B · C … */
    fun merge(options: List<RouteOption>): List<RouteOption> {
        val groups = LinkedHashMap<List<Long>, MutableList<RouteOption>>()
        options.forEach { o -> groups.getOrPut(o.visits.map { it.stop.id } + listOf(-1L) + o.added.map { it.id }) { mutableListOf() } += o }
        return groups.values.mapIndexed { i, g ->
            val kinds = g.flatMap { it.kinds }
            val titles = g.flatMap { it.kindTitles }
            g.first().copy(key = ('A' + i).toString(), title = titles.joinToString(" · "),
                description = g.first().description, kinds = kinds, kindTitles = titles)
        }
    }

    // ------------------------------------------------------------------
    fun legKm(aLat: Double, aLng: Double, bLat: Double, bLng: Double) =
        Geo.distanceKm(aLat, aLng, bLat, bLng) * DETOUR_FACTOR

    fun legMinutes(km: Double): Int = if (km < 0.05) 0 else maxOf(5, Math.round(km / SPEED_KMH * 60).toInt())

    /** 고르지 않은 구간의 수단: 1.2km 까지는 도보, 그보다 멀면 대중교통 */
    fun defaultMode(km: Double) = if (km <= 1.2) LegMode.WALK else LegMode.TRANSIT

    /**
     * 수단별 어림 시간 (직선 × 1.3 거리 기준, 길찾기 API 없이):
     *  도보 4.5km/h · 대중교통 = 기다림·환승 5분 + 22km/h · 택시·차 = 부르기·주차 3분 + 24km/h
     */
    fun modeMinutes(mode: LegMode, km: Double): Int = if (km < 0.05) 0 else when (mode) {
        LegMode.WALK -> maxOf(1, Math.round(km / 4.5 * 60).toInt())
        LegMode.TRANSIT -> 5 + Math.round(km / 22.0 * 60).toInt()
        LegMode.TAXI -> 3 + Math.round(km / 24.0 * 60).toInt()
    }

    /** 구간들 (출발 id, 도착 id, km): 시작점 → 장소들 → 도착점 */
    fun legs(e: Ends, order: List<Stop>): List<Triple<Long, Long, Double>> {
        if (order.isEmpty()) return emptyList()
        val out = mutableListOf<Triple<Long, Long, Double>>()
        e.start?.let { out += Triple(ANCHOR_ID, order.first().id, legKm(it.lat, it.lng, order.first().lat, order.first().lng)) }
        for (i in 0 until order.size - 1) out += Triple(order[i].id, order[i + 1].id, legKm(order[i].lat, order[i].lng, order[i + 1].lat, order[i + 1].lng))
        e.end?.let { out += Triple(order.last().id, ANCHOR_ID, legKm(order.last().lat, order.last().lng, it.lat, it.lng)) }
        return out
    }

    /** 시작점 → 장소들 → 도착점 총 거리 */
    fun totalKm(e: Ends, order: List<Stop>): Double = legs(e, order).sumOf { it.third }
    fun totalKm(anchor: Anchor?, order: List<Stop>): Double = totalKm(Ends.of(anchor), order)

    // ---------- 순서 만들기 (가운데 장소들만 움직이고, 처음 · 마지막 장소는 고정) ----------
    private fun candidatesOf(e: Ends, stops: List<Stop>, mid: List<Stop>): List<List<Stop>> =
        if (mid.size <= MAX_EXACT) permutations(mid).toList()
        else listOf(shortestMid(e, stops, mid), nearestMid(e, stops, mid))

    private fun shortestMid(e: Ends, stops: List<Stop>, mid: List<Stop>): List<Stop> =
        if (mid.size <= MAX_EXACT) permutations(mid).minBy { totalKm(e, wrap(e, stops, it)) }
        else twoOpt(e, stops, nearestMid(e, stops, mid))

    private fun nearestMid(e: Ends, stops: List<Stop>, mid: List<Stop>): List<Stop> {
        if (mid.isEmpty()) return mid
        val left = mid.toMutableList()
        val out = mutableListOf<Stop>()
        val first = stops.firstOrNull { it.id == e.firstId }
        var lat = first?.lat ?: e.start?.lat ?: left.first().lat
        var lng = first?.lng ?: e.start?.lng ?: left.first().lng
        while (left.isNotEmpty()) {
            val next = left.minBy { Geo.distanceKm(lat, lng, it.lat, it.lng) }
            out += next; left -= next
            lat = next.lat; lng = next.lng
        }
        return out
    }

    private fun fewerTransitMid(e: Ends, stops: List<Stop>, mid: List<Stop>, board: (Long, Long, Double) -> Int): List<Stop> =
        candidatesOf(e, stops, mid).minWith(compareBy<List<Stop>>(
            { o -> legs(e, wrap(e, stops, o)).sumOf { (f, t, km) -> board(f, t, km) } }, { totalKm(e, wrap(e, stops, it)) }))

    private fun timeAwareMid(e: Ends, stops: List<Stop>, mid: List<Stop>, startTime: Int, legTime: LegTime): List<Stop> =
        candidatesOf(e, stops, mid).minBy { o ->
            val full = wrap(e, stops, o)
            val sim = simulate(e, full, startTime, legTime)
            sim.penalty * 1000 + sim.waitMinutes * 2 + totalKm(e, full)
        }

    private fun twoOpt(e: Ends, stops: List<Stop>, start: List<Stop>): List<Stop> {
        var best = start
        fun cost(o: List<Stop>) = totalKm(e, wrap(e, stops, o))
        var improved = true
        while (improved) {
            improved = false
            for (i in 0 until best.size - 1) for (j in i + 1 until best.size) {
                val trial = best.subList(0, i) + best.subList(i, j + 1).reversed() + best.subList(j + 1, best.size)
                if (cost(trial) + 1e-9 < cost(best)) { best = trial; improved = true }
            }
        }
        return best
    }

    // ---------- 예전 모양 (숙소 하나로 시작 · 끝) — 테스트 · 지도에서 씀 ----------
    fun shortestOrder(anchor: Anchor?, stops: List<Stop>): List<Stop> = shortestMid(Ends.of(anchor), stops, stops)
    fun nearestNeighbor(anchor: Anchor?, stops: List<Stop>): List<Stop> = nearestMid(Ends.of(anchor), stops, stops)
    fun fewerTransitOrder(anchor: Anchor?, stops: List<Stop>, isTransit: (Long, Long, Double) -> Boolean): List<Stop> =
        fewerTransitMid(Ends.of(anchor), stops, stops) { f, t, km -> if (isTransit(f, t, km)) 1 else 0 }
    fun timeAwareOrder(anchor: Anchor?, stops: List<Stop>, startTime: Int, legTime: LegTime = DEFAULT_LEG): List<Stop> =
        timeAwareMid(Ends.of(anchor), stops, stops, startTime, legTime)

    /** 최단 순서에 후보를 하나씩, 가장 덜 돌아가는 자리에 끼워 넣음 (최대 2곳, 처음 · 마지막 장소 자리는 그대로) */
    fun insertCandidates(anchor: Anchor?, base: List<Stop>, candidates: List<Stop>): Pair<List<Stop>, List<Stop>> =
        insertCandidates(Ends.of(anchor), base, base, candidates)

    private fun insertCandidates(e: Ends, stops: List<Stop>, base: List<Stop>, candidates: List<Stop>): Pair<List<Stop>, List<Stop>> {
        var order = base
        val added = mutableListOf<Stop>()
        val pool = candidates.toMutableList()
        repeat(2) {
            val baseKm = totalKm(e, order)
            val lo = if (e.firstId != null) 1 else 0
            val hi = order.size - if (e.lastId != null) 1 else 0
            var best: Triple<Stop, List<Stop>, Double>? = null
            for (c in pool) {
                for (pos in lo..hi) {
                    val trial = order.toMutableList().apply { add(pos, c) }
                    val extra = totalKm(e, trial) - baseKm
                    if (best == null || extra < best.third) best = Triple(c, trial, extra)
                }
            }
            val pick = best ?: return order to added
            if (pick.third > MAX_DETOUR_KM) return order to added
            order = pick.second
            added += pick.first
            pool.remove(pick.first)
        }
        return order to added
    }

    // ------------------------------------------------------------------
    data class Simulation(val visits: List<Visit>, val penalty: Int, val waitMinutes: Int,
                          val moveMinutes: Int, val endTime: Int)

    fun simulate(anchor: Anchor?, order: List<Stop>, startTime: Int, legTime: LegTime = DEFAULT_LEG): Simulation =
        simulate(Ends.of(anchor), order, startTime, legTime)

    /** 출발 시각부터 차례로 이동·체류하며 도착 시각과 문제(경고)를 계산 */
    fun simulate(e: Ends, order: List<Stop>, startTime: Int, legTime: LegTime = DEFAULT_LEG): Simulation {
        var t = startTime
        var penalty = 0
        var wait = 0
        var move = 0
        var prevLat = e.start?.lat
        var prevLng = e.start?.lng
        var prevId = ANCHOR_ID
        val visits = mutableListOf<Visit>()

        for (s in order) {
            if (prevLat != null && prevLng != null) {
                val m = legTime(prevId, s.id, legKm(prevLat, prevLng, s.lat, s.lng))
                t += m; move += m
            }
            var arrive = t
            var warning: String? = null
            var waited = 0

            if (s.closedToday) { warning = "이날 휴무"; penalty++ }
            else if (s.open != null && arrive < s.open.first) {
                waited = s.open.first - arrive             // 문 열 때까지 기다림
                wait += waited
                arrive = s.open.first
            }
            val leave = arrive + s.stayMinutes
            if (warning == null && s.open != null && arrive >= s.open.last) {
                warning = "운영시간(${TimeUtil.format(s.open.last)}) 이후 도착"; penalty++
            } else if (warning == null && s.breakTime != null &&
                arrive < s.breakTime.last && leave > s.breakTime.first) {
                warning = "브레이크 타임(${TimeUtil.format(s.breakTime.first)}~${TimeUtil.format(s.breakTime.last)})과 겹침"
                penalty++
            } else if (warning == null && s.open != null && leave > s.open.last) {
                warning = "머무는 중 영업 종료(${TimeUtil.format(s.open.last)})"; penalty++
            }
            visits += Visit(s, arrive, leave, warning, waited)
            t = leave
            prevLat = s.lat; prevLng = s.lng; prevId = s.id
        }
        val end = e.end
        if (end != null && order.isNotEmpty()) {
            val m = legTime(order.last().id, ANCHOR_ID, legKm(order.last().lat, order.last().lng, end.lat, end.lng))
            t += m; move += m
        }
        return Simulation(visits, penalty, wait, move, t)
    }

    fun <T> permutations(list: List<T>): Sequence<List<T>> = sequence {
        if (list.size <= 1) { yield(list); return@sequence }
        for (i in list.indices) {
            val rest = list.toMutableList().apply { removeAt(i) }
            for (p in permutations(rest)) yield(listOf(list[i]) + p)
        }
    }
}
