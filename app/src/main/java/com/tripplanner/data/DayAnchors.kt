package com.tripplanner.data

/**
 * 하루의 시작 · 끝 장소 (여행 상세 · 경로 제안에서 고름) — Trip.dayAnchors 에 글자로 저장 · 공유.
 *  · DEFAULT: 그날 묵는 숙소 (예전과 같음)
 *  · LODGING: 고른 숙소 (숙소를 옮기는 날 등)
 *  · PLACE: 여행에 넣은 장소 중 하나
 *      - 그날 일정에 있는 장소면 그 장소를 처음 / 마지막에 들름
 *      - 다른 날 · 후보 장소면 거기서 출발 / 거기로 도착 (공항 · 역 · 짐 맡긴 곳 등, 들르는 장소로 세지 않음)
 *  · NONE: 정하지 않음 — 첫 장소에서 시작 / 마지막 장소에서 끝
 * 숙소 · 장소는 이름으로 가리킴 (공유하면 기기마다 id 가 달라서).
 */
sealed class Endpoint {
    data object Default : Endpoint()
    data object None : Endpoint()
    data class AtLodging(val name: String) : Endpoint()
    data class AtPlace(val name: String) : Endpoint()
}

data class DayEnds(val start: Endpoint = Endpoint.Default, val end: Endpoint = Endpoint.Default)

object DayAnchors {
    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()

    private fun enc(e: Endpoint) = when (e) {
        Endpoint.Default -> ""
        Endpoint.None -> "N"
        is Endpoint.AtLodging -> "L:" + clean(e.name)
        is Endpoint.AtPlace -> "P:" + clean(e.name)
    }

    private fun dec(s: String): Endpoint = when {
        s == "N" -> Endpoint.None
        s.startsWith("L:") -> Endpoint.AtLodging(s.substring(2))
        s.startsWith("P:") -> Endpoint.AtPlace(s.substring(2))
        else -> Endpoint.Default
    }

    /** 저장 글자 → 일차별 시작 · 끝 (한 줄에 '일차\t시작\t끝') */
    fun parse(raw: String): Map<Int, DayEnds> = raw.lines().mapNotNull { line ->
        val p = line.split('\t')
        val day = p.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        day to DayEnds(dec(p.getOrElse(1) { "" }), dec(p.getOrElse(2) { "" }))
    }.toMap()

    fun encode(map: Map<Int, DayEnds>): String = map.toSortedMap()
        .filterValues { it != DayEnds() }
        .entries.joinToString("\n") { (d, e) -> "$d\t${enc(e.start)}\t${enc(e.end)}" }

    /** 하루의 시작(start = true) 또는 끝만 바꾼 저장 글자 */
    fun with(raw: String, day: Int, start: Boolean, value: Endpoint): String {
        val map = parse(raw).toMutableMap()
        val cur = map[day] ?: DayEnds()
        map[day] = if (start) cur.copy(start = value) else cur.copy(end = value)
        return encode(map)
    }

    /** 장소 이름이 바뀌면 시작 · 끝에 적힌 이름도 바꿈 */
    fun renamePlace(raw: String, old: String, new: String): String = encode(parse(raw).mapValues { (_, e) ->
        fun r(x: Endpoint) = if (x is Endpoint.AtPlace && x.name == old) Endpoint.AtPlace(new) else x
        DayEnds(r(e.start), r(e.end))
    })

    /** 실제로 쓸 시작 · 끝: 고른 숙소 · 장소가 없어졌으면 기본(그날 숙소)으로 */
    sealed class Resolved {
        data class Lodge(val lodging: Lodging) : Resolved()
        /** 그날 일정에 있는 장소 (처음 · 마지막에 들름) */
        data class AtPlace(val place: Place) : Resolved()
        /** 그날 일정 밖의 장소 (다른 날 · 후보) — 숙소처럼 출발점 · 도착점으로만 씀 */
        data class Spot(val place: Place) : Resolved()
        data object None : Resolved()
    }

    private fun hasLoc(p: Place) = p.lat != 0.0 || p.lng != 0.0

    /**
     * @param dayPlaces 그날 일정의 장소
     * @param allPlaces 여행의 모든 장소 (다른 날 · 후보 포함). 위치가 없는 장소는 출발 · 도착점이 될 수 없어 기본으로
     */
    fun resolve(e: Endpoint, defaultLodging: Lodging?, lodgings: List<Lodging>, dayPlaces: List<Place>,
                allPlaces: List<Place> = dayPlaces): Resolved {
        fun def(): Resolved = defaultLodging?.let { Resolved.Lodge(it) } ?: Resolved.None
        return when (e) {
            Endpoint.Default -> def()
            Endpoint.None -> Resolved.None
            is Endpoint.AtLodging -> lodgings.firstOrNull { it.name == e.name }?.let { Resolved.Lodge(it) } ?: def()
            is Endpoint.AtPlace -> dayPlaces.firstOrNull { it.name == e.name }?.let { Resolved.AtPlace(it) }
                ?: allPlaces.firstOrNull { it.name == e.name && it.id !in dayPlaces.map { d -> d.id } && hasLoc(it) }?.let { Resolved.Spot(it) }
                ?: def()
        }
    }
}
