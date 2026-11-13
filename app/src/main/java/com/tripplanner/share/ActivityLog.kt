package com.tripplanner.share

import com.tripplanner.data.Place
import com.tripplanner.data.PlaceStatus

/**
 * 공유 여행의 활동 기록 한 줄 (시안 TripHistory).
 * 서버 문서의 activity 목록에 최근 MAX 개만 남깁니다. 순수 코드라 단위 테스트로 확인.
 */
data class ActivityEvent(
    val uid: String,
    val name: String,
    val type: Type,
    /** 굵게 보일 대상 (장소 이름 · 파일 이름), 없으면 빈 글자 */
    val target: String = "",
    /** 일차 · 순서 (옮김 · 확정) */
    val day: Int? = null,
    val order: Int? = null,
    val at: Long
) {
    enum class Type { JOIN, PLACE_ADD, PLACE_CONFIRM, PLACE_MOVE, PLACE_HOURS, PLACE_EDIT, PLACE_DELETE, FILE, TRAVEL }
}

object ActivityLog {
    const val MAX = 100

    fun toMap(e: ActivityEvent): Map<String, Any?> = mapOf(
        "uid" to e.uid, "name" to e.name, "type" to e.type.name, "target" to e.target,
        "day" to e.day, "order" to e.order, "at" to e.at
    )

    fun fromList(raw: Any?): List<ActivityEvent> = (raw as? List<*>).orEmpty().mapNotNull { item ->
        @Suppress("UNCHECKED_CAST")
        val m = item as? Map<String, Any?> ?: return@mapNotNull null
        val type = ActivityEvent.Type.entries.firstOrNull { it.name == m["type"] } ?: return@mapNotNull null
        ActivityEvent(
            uid = m["uid"] as? String ?: "", name = m["name"] as? String ?: "", type = type,
            target = m["target"] as? String ?: "",
            day = (m["day"] as? Number)?.toInt(), order = (m["order"] as? Number)?.toInt(),
            at = (m["at"] as? Number)?.toLong() ?: 0L
        )
    }

    /** 기존 기록 + 새 기록 → 최근 MAX 개 */
    fun append(old: List<ActivityEvent>, new: List<ActivityEvent>) = (old + new).sortedBy { it.at }.takeLast(MAX)

    /**
     * 내가 바꾼 것 → 기록 (장소는 이름으로 짝지음).
     *  · 새 장소: 후보에 추가 / n일차에 추가
     *  · 후보 → 확정: n일차로 확정
     *  · 같은 날 순서 바꿈: 가장 많이 움직인 장소 하나만 'n일차 k번째로 옮겼어요'
     *  · 운영시간 · 브레이크 · 휴무 바뀜: 운영시간을 바꿨어요 / 그 밖의 내용: 정보를 바꿨어요
     *  · 없어진 장소: 삭제 · 교통·숙소 바뀜: 한 줄
     */
    fun diff(before: TripSnapshot, after: TripSnapshot, uid: String, name: String, now: Long): List<ActivityEvent> {
        val out = mutableListOf<ActivityEvent>()
        fun ev(type: ActivityEvent.Type, target: String = "", day: Int? = null, order: Int? = null) =
            ActivityEvent(uid, name, type, target, day, order, now)
        val old = before.places.associateBy { it.name }
        val new = after.places.associateBy { it.name }

        new.values.filter { it.name !in old }.forEach { p ->
            out += ev(ActivityEvent.Type.PLACE_ADD, p.name, if (p.status == PlaceStatus.CONFIRMED) p.day else null)
        }
        old.values.filter { it.name !in new }.forEach { out += ev(ActivityEvent.Type.PLACE_DELETE, it.name) }

        var bestMove: Pair<Place, Int>? = null
        new.values.filter { it.name in old }.forEach { p ->
            val o = old.getValue(p.name)
            when {
                o.status != PlaceStatus.CONFIRMED && p.status == PlaceStatus.CONFIRMED ->
                    out += ev(ActivityEvent.Type.PLACE_CONFIRM, p.name, p.day)
                p.status == PlaceStatus.CONFIRMED && (o.day != p.day || o.orderInDay != p.orderInDay) -> {
                    val dist = if (o.day != p.day) 1000 else kotlin.math.abs((p.orderInDay ?: 0) - (o.orderInDay ?: 0))
                    if (bestMove == null || dist > bestMove!!.second) bestMove = p to dist
                }
            }
            if (o.openingHours != p.openingHours || o.breakTime != p.breakTime || o.closedDays != p.closedDays)
                out += ev(ActivityEvent.Type.PLACE_HOURS, p.name)
            else if (contentOf(o) != contentOf(p)) out += ev(ActivityEvent.Type.PLACE_EDIT, p.name)
        }
        bestMove?.let { (p, _) -> out += ev(ActivityEvent.Type.PLACE_MOVE, p.name, p.day, p.orderInDay) }

        fun travel(s: TripSnapshot) = s.flights.map { it.copy(id = 0, tripId = 0) }.toSet() to s.lodgings.map { it.copy(id = 0, tripId = 0) }.toSet()
        if (travel(before) != travel(after)) out += ev(ActivityEvent.Type.TRAVEL)
        return out
    }

    /** 순서·일차·상태·운영 정보·기기마다 다른 값을 뺀 장소 내용 */
    private fun contentOf(p: Place) = p.copy(id = 0, tripId = 0, status = PlaceStatus.CANDIDATE, day = null, orderInDay = null,
        photoUri = null, typeId = null, openingHours = "", breakTime = null, closedDays = null)
}
