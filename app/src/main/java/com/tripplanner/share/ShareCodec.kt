package com.tripplanner.share

import com.tripplanner.data.Flight
import com.tripplanner.data.Lodging
import com.tripplanner.data.Place
import com.tripplanner.data.PlaceCategory
import com.tripplanner.data.PlaceStatus
import com.tripplanner.data.TransportMode
import com.tripplanner.data.Trip

/** 여행 하나 전체 (서버에 문서 하나로 저장) */
data class TripSnapshot(
    val trip: Trip,
    val places: List<Place>,
    val flights: List<Flight>,
    val lodgings: List<Lodging>
)

/**
 * 여행 ↔ 서버 문서(Map) 변환. Firebase 와 상관없는 순수 코드라 단위 테스트로 확인합니다.
 * - 기기마다 다른 값(로컬 id, 사진 파일 경로)은 올리지 않음
 * - 모르는 값(새 버전에서 추가된 종류 등)은 기본값으로 읽음
 */
object ShareCodec {

    /** 헷갈리는 글자(0/O, 1/I/L) 뺀 6자리 초대 코드 */
    private const val CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    fun newCode(random: kotlin.random.Random = kotlin.random.Random): String =
        (1..6).map { CODE_CHARS[random.nextInt(CODE_CHARS.length)] }.joinToString("")

    /** 입력한 코드 정리: 공백·하이픈 제거, 대문자 */
    fun normalize(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }

    fun isValidCode(code: String) = code.length == 6 && code.all { it in CODE_CHARS }

    fun toMap(s: TripSnapshot): Map<String, Any?> = mapOf(
        "title" to s.trip.title,
        "startDate" to s.trip.startDate,
        "endDate" to s.trip.endDate,
        "note" to s.trip.note,
        "startMinutes" to s.trip.startMinutes,
        "endMinutes" to s.trip.endMinutes,
        "places" to s.places.map { p ->
            mapOf(
                "name" to p.name, "status" to p.status.name, "openingHours" to p.openingHours,
                "breakTime" to p.breakTime, "closedDays" to p.closedDays,
                "reservation" to p.isReservationRequired, "memo" to p.memo,
                "lat" to p.lat, "lng" to p.lng, "day" to p.day, "order" to p.orderInDay,
                "stay" to p.stayMinutes, "category" to p.category.name,
                "address" to p.address, "addressDetail" to p.addressDetail, "locMemo" to p.locationMemo
            )
        },
        "flights" to s.flights.map { f ->
            mapOf("no" to f.flightNo, "from" to f.fromAirport, "to" to f.toAirport,
                "departAt" to f.departAt, "arriveAt" to f.arriveAt, "mode" to f.mode.name)
        },
        "lodgings" to s.lodgings.map { l ->
            mapOf("name" to l.name, "checkIn" to l.checkIn, "checkOut" to l.checkOut, "lat" to l.lat, "lng" to l.lng,
                "address" to l.address, "addressDetail" to l.addressDetail, "locMemo" to l.locationMemo)
        }
    )

    /** 서버 문서 → 여행. base 의 id·공유 코드·updatedAt 은 유지 */
    fun fromMap(m: Map<String, Any?>, base: Trip): TripSnapshot {
        val trip = base.copy(
            title = m.str("title") ?: base.title,
            startDate = m.str("startDate") ?: base.startDate,
            endDate = m.str("endDate") ?: base.endDate,
            note = m.str("note") ?: "",
            startMinutes = m.int("startMinutes") ?: 600,
            endMinutes = m.int("endMinutes")
        )
        val places = m.list("places").map { p ->
            Place(
                tripId = base.id, name = p.str("name") ?: "",
                status = enumOr(p.str("status"), PlaceStatus.CANDIDATE),
                openingHours = p.str("openingHours") ?: "",
                breakTime = p.str("breakTime"), closedDays = p.str("closedDays"),
                isReservationRequired = p["reservation"] as? Boolean ?: false,
                memo = p.str("memo") ?: "",
                lat = p.dbl("lat") ?: 0.0, lng = p.dbl("lng") ?: 0.0,
                day = p.int("day"), orderInDay = p.int("order"),
                stayMinutes = p.int("stay") ?: 60,
                category = enumOr(p.str("category"), PlaceCategory.SIGHT),
                address = p.str("address") ?: "", addressDetail = p.str("addressDetail") ?: "",
                locationMemo = p.str("locMemo") ?: ""
            )
        }
        val flights = m.list("flights").map { f ->
            Flight(tripId = base.id, flightNo = f.str("no") ?: "", fromAirport = f.str("from") ?: "",
                toAirport = f.str("to") ?: "", departAt = f.str("departAt") ?: "", arriveAt = f.str("arriveAt") ?: "",
                mode = enumOr(f.str("mode"), TransportMode.FLIGHT))
        }
        val lodgings = m.list("lodgings").map { l ->
            Lodging(tripId = base.id, name = l.str("name") ?: "", checkIn = l.str("checkIn") ?: "",
                checkOut = l.str("checkOut") ?: "", lat = l.dbl("lat") ?: 0.0, lng = l.dbl("lng") ?: 0.0,
                address = l.str("address") ?: "", addressDetail = l.str("addressDetail") ?: "",
                locationMemo = l.str("locMemo") ?: "")
        }
        return TripSnapshot(trip, places, flights, lodgings)
    }

    /** 서버 문서의 멤버 이름 정보 → (만든 사람 닉네임: 내가 만들었으면 "", 나를 뺀 멤버 닉네임들) */
    fun memberInfo(m: Map<String, Any?>, me: String): Pair<String, List<String>> {
        @Suppress("UNCHECKED_CAST")
        val names = (m["names"] as? Map<String, Any?>).orEmpty().mapValues { it.value as? String ?: "" }
        val owner = m.str("ownerUid")
        val members = (m["members"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        val inviter = if (owner == null || owner == me) "" else names[owner].orEmpty()
        val others = members.filter { it != me }.map { names[it].orEmpty() }.filter { it.isNotBlank() }
        return inviter to others
    }

    // ---- 서버 값 읽기 (숫자는 Long/Double 어느 쪽으로든 올 수 있음) ----
    private fun Map<String, Any?>.str(k: String) = this[k] as? String
    private fun Map<String, Any?>.int(k: String) = (this[k] as? Number)?.toInt()
    private fun Map<String, Any?>.dbl(k: String) = (this[k] as? Number)?.toDouble()
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.list(k: String) = (this[k] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
