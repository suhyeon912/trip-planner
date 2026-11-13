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

/** 구간 이동 수단 (시안 RouteLegMode): 도보 · 대중교통 · 택시·차 */
enum class LegMode { WALK, TRANSIT, TAXI }

/**
 * 구간 이동 시간 정하기: (출발 id, 도착 id, 거리 km) → 분. 숙소 id 는 ANCHOR_ID.
 * 기본은 예전과 같은 어림값 (도보+대중교통 평균 15km/h).
 */
typealias LegTime = (from: Long, to: Long, km: Double) -> Int

/** 출발·도착 지점 (보통 숙소) */
data class Anchor(val name: String, val lat: Double, val lng: Double)

data class Visit(val stop: Stop, val arrive: Int, val leave: Int, val warning: String?, val waitMinutes: Int = 0)

data class RouteOption(
    val key: String,                 // "A" / "B" / "C"
    val title: String,
    val description: String,
    val visits: List<Visit>,
    val totalKm: Double,
    val moveMinutes: Int,
    val endTime: Int,                // 숙소 도착(또는 마지막 장소 출발) 시각
    val added: List<Stop> = emptyList(),  // C안에서 새로 넣은 후보
    val endLimit: Int? = null        // 숙소 도착 목표 시각 (null = 자유)
) {
    /** 목표보다 늦게 도착하는 분 (늦지 않으면 0) */
    val lateMinutes: Int get() = endLimit?.let { maxOf(0, endTime - it) } ?: 0
    val warnings: List<String> get() = visits.mapNotNull { it.warning?.let { w -> "${it.stop.name}: $w" } } +
        if (lateMinutes > 0) listOf("숙소 도착 ${TimeUtil.format(endTime)} · 목표 ${TimeUtil.format(endLimit!!)}보다 ${lateMinutes}분 늦음") else emptyList()
}

/**
 * 경로 후보 3가지를 만듭니다. 실제 길찾기 API 없이 '직선거리 × 1.3'으로 이동거리를 추정합니다.
 *  A. 최단 경로   — 총 이동거리가 가장 짧은 순서
 *  B. 시간 맞춤   — 운영시간·브레이크 타임·휴무를 가장 덜 어기는 순서
 *  C. 후보 포함   — A 경로에서 크게 벗어나지 않는 후보 장소를 최대 2곳 끼워 넣음
 */
object RoutePlanner {

    const val DEFAULT_START = 10 * 60      // 10:00 출발
    private const val DETOUR_FACTOR = 1.3  // 직선거리 → 실제 이동거리 보정
    private const val SPEED_KMH = 15.0     // 도보+대중교통 평균 속도 가정
    private const val MAX_EXACT = 8        // 이 개수까지는 모든 순서를 다 비교
    const val MAX_DETOUR_KM = 3.0          // C안: 이만큼 이하로 돌아가는 후보만 추가

    fun plan(
        anchor: Anchor?,
        stops: List<Stop>,
        candidates: List<Stop>,
        startTime: Int = DEFAULT_START,
        endLimit: Int? = null,
        legTime: LegTime = DEFAULT_LEG
    ): List<RouteOption> {
        if (stops.isEmpty()) return emptyList()
        fun build(key: String, title: String, desc: String, anchor: Anchor?, order: List<Stop>, startTime: Int,
                  added: List<Stop> = emptyList(), endLimit: Int? = null) =
            build(key, title, desc, anchor, order, startTime, added, endLimit, legTime)

        // A. 최단
        val orderA = shortestOrder(anchor, stops)
        val a = build("A", "최단 경로", "이동거리가 가장 짧은 순서", anchor, orderA, startTime, endLimit = endLimit)

        // B. 운영시간 맞춤
        val orderB = timeAwareOrder(anchor, stops, startTime, legTime)
        val b = if (orderB.map { it.id } == orderA.map { it.id })
            build("B", "시간 맞춤", "최단 경로가 이미 운영시간과 잘 맞아요", anchor, orderB, startTime, endLimit = endLimit)
        else
            build("B", "시간 맞춤", "브레이크 타임·운영시간을 피하는 순서", anchor, orderB, startTime, endLimit = endLimit)

        val result = mutableListOf(a, b)

        // C. 후보 포함
        val (orderC, added) = insertCandidates(anchor, orderA, candidates)
        if (added.isNotEmpty()) {
            result += build("C", "후보 포함", "가는 길에 들를 수 있는 후보 ${added.size}곳 추가",
                anchor, orderC, startTime, added, endLimit)
        }

        // D. 지금 순서 그대로 (여행 상세에서 정한 순서, A 와 같으면 빼기)
        if (stops.size >= 2 && stops.map { it.id } != orderA.map { it.id }) {
            result += build("D", "지금 순서 그대로", "여행 상세에서 정한 순서 그대로", anchor, stops, startTime, endLimit = endLimit)
        }
        return result
    }

    // ------------------------------------------------------------------
    fun legKm(aLat: Double, aLng: Double, bLat: Double, bLng: Double) =
        Geo.distanceKm(aLat, aLng, bLat, bLng) * DETOUR_FACTOR

    fun legMinutes(km: Double): Int = if (km < 0.05) 0 else maxOf(5, Math.round(km / SPEED_KMH * 60).toInt())

    const val ANCHOR_ID = -1L
    val DEFAULT_LEG: LegTime = { _, _, km -> legMinutes(km) }

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

    /** 숙소 → 장소들 → 숙소 총 거리. 숙소가 없으면 첫 장소에서 출발해 마지막 장소에서 끝 */
    fun totalKm(anchor: Anchor?, order: List<Stop>): Double {
        if (order.isEmpty()) return 0.0
        var km = 0.0
        for (i in 0 until order.size - 1) km += legKm(order[i].lat, order[i].lng, order[i + 1].lat, order[i + 1].lng)
        if (anchor != null) {
            km += legKm(anchor.lat, anchor.lng, order.first().lat, order.first().lng)
            km += legKm(order.last().lat, order.last().lng, anchor.lat, anchor.lng)
        }
        return km
    }

    fun shortestOrder(anchor: Anchor?, stops: List<Stop>): List<Stop> =
        if (stops.size <= MAX_EXACT) permutations(stops).minBy { totalKm(anchor, it) }
        else twoOpt(anchor, nearestNeighbor(anchor, stops))

    fun timeAwareOrder(anchor: Anchor?, stops: List<Stop>, startTime: Int, legTime: LegTime = DEFAULT_LEG): List<Stop> {
        val candidates = if (stops.size <= MAX_EXACT) permutations(stops).toList()
                         else listOf(shortestOrder(anchor, stops))
        return candidates.minBy { order ->
            val sim = simulate(anchor, order, startTime, legTime)
            sim.penalty * 1000 + sim.waitMinutes * 2 + totalKm(anchor, order)
        }
    }

    /** A 순서에 후보를 하나씩, 가장 덜 돌아가는 자리에 끼워 넣음 (최대 2곳) */
    fun insertCandidates(anchor: Anchor?, base: List<Stop>, candidates: List<Stop>): Pair<List<Stop>, List<Stop>> {
        var order = base
        val added = mutableListOf<Stop>()
        val pool = candidates.toMutableList()
        repeat(2) {
            val baseKm = totalKm(anchor, order)
            var best: Triple<Stop, List<Stop>, Double>? = null
            for (c in pool) {
                for (pos in 0..order.size) {
                    val trial = order.toMutableList().apply { add(pos, c) }
                    val extra = totalKm(anchor, trial) - baseKm
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

    /** 출발 시각부터 차례로 이동·체류하며 도착 시각과 문제(경고)를 계산 */
    fun simulate(anchor: Anchor?, order: List<Stop>, startTime: Int, legTime: LegTime = DEFAULT_LEG): Simulation {
        var t = startTime
        var penalty = 0
        var wait = 0
        var move = 0
        var prevLat = anchor?.lat
        var prevLng = anchor?.lng
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
        if (anchor != null && order.isNotEmpty()) {
            val m = legTime(order.last().id, ANCHOR_ID, legKm(order.last().lat, order.last().lng, anchor.lat, anchor.lng))
            t += m; move += m
        }
        return Simulation(visits, penalty, wait, move, t)
    }

    private fun build(key: String, title: String, desc: String, anchor: Anchor?, order: List<Stop>,
                      startTime: Int, added: List<Stop>, endLimit: Int?, legTime: LegTime): RouteOption {
        val sim = simulate(anchor, order, startTime, legTime)
        return RouteOption(key, title, desc, sim.visits, totalKm(anchor, order), sim.moveMinutes, sim.endTime, added, endLimit)
    }

    // ---------- 순서 만들기 ----------
    fun <T> permutations(list: List<T>): Sequence<List<T>> = sequence {
        if (list.size <= 1) { yield(list); return@sequence }
        for (i in list.indices) {
            val rest = list.toMutableList().apply { removeAt(i) }
            for (p in permutations(rest)) yield(listOf(list[i]) + p)
        }
    }

    private fun nearestNeighbor(anchor: Anchor?, stops: List<Stop>): List<Stop> {
        val left = stops.toMutableList()
        val out = mutableListOf<Stop>()
        var lat = anchor?.lat ?: left.first().lat
        var lng = anchor?.lng ?: left.first().lng
        while (left.isNotEmpty()) {
            val next = left.minBy { Geo.distanceKm(lat, lng, it.lat, it.lng) }
            out += next; left -= next
            lat = next.lat; lng = next.lng
        }
        return out
    }

    private fun twoOpt(anchor: Anchor?, start: List<Stop>): List<Stop> {
        var best = start
        var improved = true
        while (improved) {
            improved = false
            for (i in 0 until best.size - 1) for (j in i + 1 until best.size) {
                val trial = best.subList(0, i) + best.subList(i, j + 1).reversed() + best.subList(j + 1, best.size)
                if (totalKm(anchor, trial) + 1e-9 < totalKm(anchor, best)) { best = trial; improved = true }
            }
        }
        return best
    }
}
