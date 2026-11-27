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
    /** FILE 은 예전 기록을 읽기만 함 (파일은 기기에만 있어서 다른 사람에게 보이지 않으므로 더 남기지 않음) */
    enum class Type { JOIN, PLACE_ADD, PLACE_CONFIRM, PLACE_MOVE, PLACE_HOURS, PLACE_EDIT, PLACE_DELETE, FILE, TRAVEL,
        /** 여행 이름 (target = 새 이름) · 기간 · 메모 */
        TRIP_TITLE, TRIP_DATES, TRIP_NOTE }
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
     *  · 같은 날 순서 바꿈: 끌어서 옮긴 장소 하나만 'n일차 k번째로 옮겼어요' (k = 그날 보이는 순서)
     *  · 다른 날로 옮김: 'n일차 k번째로 옮겼어요'
     *  · 여행 이름 · 기간 · 메모 바뀜: 한 줄씩
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

        val confirmedNow = mutableSetOf<String>()
        val movedDay = mutableListOf<Place>()
        new.values.filter { it.name in old }.forEach { p ->
            val o = old.getValue(p.name)
            when {
                o.status != PlaceStatus.CONFIRMED && p.status == PlaceStatus.CONFIRMED -> {
                    out += ev(ActivityEvent.Type.PLACE_CONFIRM, p.name, p.day); confirmedNow += p.name
                }
                p.status == PlaceStatus.CONFIRMED && o.status == PlaceStatus.CONFIRMED && o.day != p.day -> movedDay += p
            }
            if (o.openingHours != p.openingHours || o.breakTime != p.breakTime || o.closedDays != p.closedDays)
                out += ev(ActivityEvent.Type.PLACE_HOURS, p.name)
            else if (contentOf(o) != contentOf(p)) out += ev(ActivityEvent.Type.PLACE_EDIT, p.name)
        }
        // 다른 날로 옮긴 장소: 새 날에서 보이는 순서
        movedDay.forEach { p -> out += ev(ActivityEvent.Type.PLACE_MOVE, p.name, p.day, rankIn(after.places, p)) }
        // 같은 날 안에서 순서 바꿈: 그날마다 끌어서 옮긴 장소 하나
        val moved = movedDay.map { it.name }.toSet()
        after.places.filter { it.status == PlaceStatus.CONFIRMED && it.day != null }.map { it.day!! }.distinct().forEach { day ->
            val keep = { p: Place -> p.status == PlaceStatus.CONFIRMED && p.day == day && p.name in old && p.name in new &&
                p.name !in moved && p.name !in confirmedNow && old.getValue(p.name).day == day }
            val before0 = dayOrder(before.places.filter(keep))
            val after0 = dayOrder(after.places.filter(keep))
            movedOne(before0, after0)?.let { name ->
                out += ev(ActivityEvent.Type.PLACE_MOVE, name, day, rankIn(after.places, new.getValue(name)))
            }
        }

        // 여행 칸
        if (before.trip.title != after.trip.title) out += ev(ActivityEvent.Type.TRIP_TITLE, after.trip.title)
        if (before.trip.startDate != after.trip.startDate || before.trip.endDate != after.trip.endDate)
            out += ev(ActivityEvent.Type.TRIP_DATES)
        if (before.trip.note.trim() != after.trip.note.trim()) out += ev(ActivityEvent.Type.TRIP_NOTE)

        fun travel(s: TripSnapshot) = s.flights.map { it.copy(id = 0, tripId = 0) }.toSet() to s.lodgings.map { it.copy(id = 0, tripId = 0) }.toSet()
        if (travel(before) != travel(after)) out += ev(ActivityEvent.Type.TRAVEL)
        return out
    }

    /** 그날 보이는 순서 (여행 상세와 같은 기준: 순서 → 없으면 맨 뒤, 같으면 먼저 넣은 것) → 이름 목록 */
    private fun dayOrder(list: List<Place>) =
        list.sortedWith(compareBy<Place>({ it.orderInDay == null }, { it.orderInDay }, { it.id })).map { it.name }

    /** p 가 그날 몇 번째로 보이는지 (1부터) */
    private fun rankIn(all: List<Place>, p: Place) =
        dayOrder(all.filter { it.status == PlaceStatus.CONFIRMED && it.day == p.day }).indexOf(p.name) + 1

    /**
     * 같은 장소들의 순서가 바뀌었을 때 끌어서 옮긴 장소 하나 (없으면 null).
     * 그 장소를 양쪽에서 빼면 나머지 순서가 같아지는 장소 중, 자리가 가장 많이 바뀐 것
     * (3번째를 1번째로 옮기면 1 · 2번째도 한 칸씩 밀리지만 옮긴 것은 3번째 장소)
     */
    internal fun movedOne(before: List<String>, after: List<String>): String? {
        if (before == after || before.toSet() != after.toSet()) return null
        val candidates = after.filter { n -> before.filter { it != n } == after.filter { it != n } }
        return (candidates.ifEmpty { after.filter { before.indexOf(it) != after.indexOf(it) } })
            .maxByOrNull { kotlin.math.abs(before.indexOf(it) - after.indexOf(it)) }
    }

    /** 순서·일차·상태·운영 정보·기기마다 다른 값을 뺀 장소 내용 */
    private fun contentOf(p: Place) = p.copy(id = 0, tripId = 0, status = PlaceStatus.CANDIDATE, day = null, orderInDay = null,
        photoUri = null, typeId = null, openingHours = "", breakTime = null, closedDays = null, syncId = "")
}
