package com.tripplanner.share

/**
 * 공유 여행 합치기 (3-way). Firebase 와 상관없는 순수 코드라 단위 테스트로 확인합니다.
 *
 *  base   = 마지막으로 서버와 맞춘 문서 (ShareCodec.toMap 모양)
 *  local  = 지금 내 기기 내용
 *  server = 지금 서버에 있는 문서 (그사이 다른 사람이 올렸을 수 있음)
 *
 * 규칙: 칸 하나 · 장소 하나 · 교통편 목록 · 숙소 목록 단위로
 *  - 내가 안 바꿨으면 서버 것
 *  - 서버가 안 바꿨으면 내 것
 *  - 둘 다 바꿨으면 내 것 (지금 저장하는 사람이 마지막)
 *  - 장소는 sid 로 짝지음 (예전 문서처럼 sid 가 없으면 이름으로)
 *  - 한쪽이 지웠고 다른 쪽이 안 바꿨으면 지움, 다른 쪽이 고쳤으면 고친 것을 남김
 */
object ShareMerge {

    /** 여행 칸 (장소 · 교통 · 숙소를 뺀 것) */
    private val TRIP_FIELDS = listOf("title", "startDate", "endDate", "note", "startMinutes", "endMinutes", "dayAnchors", "timeZone")

    fun merge(base: Map<String, Any?>?, local: Map<String, Any?>, server: Map<String, Any?>?): Map<String, Any?> {
        if (server == null) return local
        val b = base ?: emptyMap()
        val out = LinkedHashMap<String, Any?>()
        for (k in TRIP_FIELDS) out[k] = pick(b[k], local[k], server[k], hasBase = base != null)
        out["places"] = mergePlaces(list(b["places"]), list(local["places"]), list(server["places"]), base != null)
        for (k in listOf("flights", "lodgings")) out[k] = pick(b[k], local[k], server[k], hasBase = base != null)
        return out
    }

    /** 한 칸 고르기. base 를 모르면(처음 맞추는 중) 내 것 */
    private fun pick(base: Any?, local: Any?, server: Any?, hasBase: Boolean): Any? = when {
        !hasBase -> local
        same(local, base) -> server
        same(server, base) -> local
        else -> local
    }

    private fun mergePlaces(base: List<Map<String, Any?>>, local: List<Map<String, Any?>>, server: List<Map<String, Any?>>,
                            hasBase: Boolean): List<Map<String, Any?>> {
        // 짝짓기 열쇠: sid, 없으면 이름. sid 없는 서버 · base 장소는 같은 이름의 내 장소 열쇠를 씀
        val localKey = local.associateBy { key(it) }
        val byName = local.associateBy { it["name"] as? String ?: "" }
        fun keyFor(p: Map<String, Any?>): String {
            val sid = p["sid"] as? String
            if (!sid.isNullOrBlank()) return "s:$sid"
            val name = p["name"] as? String ?: ""
            return byName[name]?.let { key(it) } ?: "n:$name"
        }
        val b = base.associateBy { keyFor(it) }
        val s = server.associateBy { keyFor(it) }
        val keys = LinkedHashSet<String>().apply { addAll(s.keys); addAll(localKey.keys); addAll(b.keys) }
        val out = mutableListOf<Map<String, Any?>>()
        for (k in keys) {
            val bp = b[k]; val lp = localKey[k]; val sp = s[k]
            val pickedValue: Map<String, Any?>? = when {
                !hasBase -> lp ?: sp                                  // 처음: 내 것 + 서버에만 있는 것
                bp == null -> lp ?: sp                                // 새로 생김 (어느 쪽이든)
                lp == null && sp == null -> null                      // 둘 다 지움
                lp == null -> if (samePlace(sp, bp)) null else sp     // 내가 지움: 서버가 안 고쳤으면 지움
                sp == null -> if (samePlace(lp, bp)) null else lp     // 서버에서 지움: 내가 안 고쳤으면 지움
                samePlace(lp, bp) -> sp
                samePlace(sp, bp) -> lp
                else -> lp
            }
            // 서버 쪽 장소에 sid 가 없었으면 내 sid 를 붙여 둠 (다음부터 sid 로 짝지음)
            if (pickedValue != null) out += if ((pickedValue["sid"] as? String).isNullOrBlank() && lp != null)
                pickedValue + ("sid" to lp["sid"]) else pickedValue
        }
        return out
    }

    private fun key(p: Map<String, Any?>): String {
        val sid = p["sid"] as? String
        return if (!sid.isNullOrBlank()) "s:$sid" else "n:${p["name"] as? String ?: ""}"
    }

    @Suppress("UNCHECKED_CAST")
    private fun list(v: Any?): List<Map<String, Any?>> = (v as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()

    /** 장소 비교: sid 는 빼고 (예전 문서에는 sid 가 없어서) */
    private fun samePlace(a: Map<String, Any?>?, b: Map<String, Any?>?) = same(a?.minus("sid"), b?.minus("sid"))

    /** 서버에서 온 숫자(Long · Double)와 기기의 숫자(Int)를 같게 보고 비교 */
    fun same(a: Any?, b: Any?): Boolean = norm(a) == norm(b)

    private fun norm(v: Any?): Any? = when (v) {
        is Number -> v.toDouble()
        is Map<*, *> -> v.entries.filter { it.value != null }.associate { it.key.toString() to norm(it.value) }
        is List<*> -> v.map { norm(it) }
        else -> v
    }
}
