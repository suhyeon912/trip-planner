package com.tripplanner.data

/**
 * (예전 기능) 일차별 하루의 시작 · 끝 장소 — Trip.dayAnchors 에 글자로 저장 · 공유.
 * 지금은 화면 · 경로 계산에서 쓰지 않아요: 하루의 출발 · 도착 = 그날 순서의 첫 · 마지막 장소 (ui/DayEndsUi.kt).
 * 이미 저장 · 공유된 값을 읽고 쓸 수 있게 형식만 남겨 둠 (DB 10 칸 그대로).
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
}
