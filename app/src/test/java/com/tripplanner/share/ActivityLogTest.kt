package com.tripplanner.share

import com.tripplanner.data.Flight
import com.tripplanner.data.Place
import com.tripplanner.data.PlaceStatus
import com.tripplanner.data.Trip
import com.tripplanner.share.ActivityEvent.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityLogTest {
    private val trip = Trip(id = 1, title = "도쿄", startDate = "2026-11-20", endDate = "2026-11-23")
    private fun p(name: String, day: Int? = 1, order: Int? = null, status: PlaceStatus = PlaceStatus.CONFIRMED, hours: String = "10:00 ~ 21:00") =
        Place(tripId = 1, name = name, status = status, openingHours = hours, day = if (status == PlaceStatus.CONFIRMED) day else null, orderInDay = order)
    private fun snap(vararg places: Place, flights: List<Flight> = emptyList()) = TripSnapshot(trip, places.toList(), flights, emptyList())
    private fun diff(a: TripSnapshot, b: TripSnapshot) = ActivityLog.diff(a, b, "u1", "민지", 100L).map { it.type to it.target }

    @Test fun addCandidateAndDelete() {
        val before = snap(p("츠지한", order = 1))
        val after = snap(p("시부야 스카이", status = PlaceStatus.CANDIDATE))
        assertEquals(listOf(Type.PLACE_ADD to "시부야 스카이", Type.PLACE_DELETE to "츠지한"), diff(before, after))
        assertEquals(null, ActivityLog.diff(before, after, "u1", "민지", 1).first().day)   // 후보 → 일차 없음
    }

    @Test fun reorder_reportsOnlyBiggestMove() {
        val before = snap(p("A", order = 1), p("B", order = 2), p("C", order = 3))
        val after = snap(p("C", order = 1), p("A", order = 2), p("B", order = 3))
        val e = ActivityLog.diff(before, after, "u1", "민지", 1)
        assertEquals(1, e.size)
        assertEquals(Type.PLACE_MOVE, e[0].type); assertEquals("C", e[0].target); assertEquals(1, e[0].order)
    }

    @Test fun confirmHoursEditTravel() {
        val before = snap(p("센소지", status = PlaceStatus.CANDIDATE), p("블루보틀", order = 1))
        val after = snap(p("센소지", day = 2, order = 1), p("블루보틀", order = 1, hours = "08:00 ~ 19:00"),
            flights = listOf(Flight(tripId = 1, flightNo = "KE1", fromAirport = "김포", toAirport = "하네다", departAt = "", arriveAt = "")))
        val types = diff(before, after)
        assertTrue(Type.PLACE_CONFIRM to "센소지" in types)
        assertTrue(Type.PLACE_HOURS to "블루보틀" in types)
        assertTrue(Type.TRAVEL to "" in types)
        // 메모만 바꾸면 '정보를 바꿨어요', 사진·로컬 id 는 무시
        val memo = diff(snap(p("A", order = 1)), snap(p("A", order = 1).copy(memo = "야경", photoUri = "/x", id = 9)))
        assertEquals(listOf(Type.PLACE_EDIT to "A"), memo)
        assertEquals(emptyList<Any>(), diff(snap(p("A", order = 1)), snap(p("A", order = 1).copy(photoUri = "/y"))))
    }

    @Test fun mapRoundTripAndCap() {
        val e = ActivityEvent("u1", "지우", Type.FILE, "예약 확인서.pdf", at = 5L)
        val back = ActivityLog.fromList(listOf(ActivityLog.toMap(e), mapOf("type" to "UNKNOWN"), "x"))
        assertEquals(listOf(e), back)
        val many = (1..150).map { e.copy(at = it.toLong()) }
        val capped = ActivityLog.append(emptyList(), many)
        assertEquals(ActivityLog.MAX, capped.size); assertEquals(150L, capped.last().at)
    }
}
