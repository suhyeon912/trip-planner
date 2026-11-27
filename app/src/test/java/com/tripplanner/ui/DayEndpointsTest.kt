package com.tripplanner.ui

import com.tripplanner.data.Lodging
import com.tripplanner.data.Place
import com.tripplanner.data.PlaceStatus
import com.tripplanner.data.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 하루의 출발 · 도착: 숙소는 체크인 · 체크아웃 날짜로 자동, 없는 쪽은 첫 · 마지막 장소 */
class DayEndpointsTest {

    private val trip = Trip(id = 1, title = "도쿄 3박 4일", startDate = "2026-11-20", endDate = "2026-11-23")
    private val hotel = Lodging(id = 7, tripId = 1, name = "호텔", checkIn = "2026-11-20", checkOut = "2026-11-23",
        lat = 35.71, lng = 139.79, checkInTime = "15:00", checkOutTime = "11:00")
    private fun place(id: Long, name: String, day: Int, order: Int, lat: Double = 35.6, lng: Double = 139.7) =
        Place(id = id, tripId = 1, name = name, status = PlaceStatus.CONFIRMED, openingHours = "",
            lat = lat, lng = lng, day = day, orderInDay = order)

    @Test fun firstDay_airportStart_hotelEnd() {
        val day1 = listOf(place(1, "하네다 공항", 1, 1), place(2, "스카이트리", 1, 2), place(3, "긴자", 1, 3))
        val e = DayEndpoints.of(day1, trip, 1, listOf(hotel))
        assertNull(e.startLodging)
        assertEquals(hotel, e.endLodging)
        // 출발은 첫 장소(공항) 고정, 도착은 숙소
        assertEquals(1L, e.ends.firstId)
        assertNull(e.ends.lastId)
        assertEquals("호텔", e.ends.end?.name)
        assertNull(e.ends.start)
    }

    @Test fun middleDay_hotelBothEnds() {
        val day2 = listOf(place(4, "시부야", 2, 1), place(5, "신주쿠", 2, 2))
        val e = DayEndpoints.of(day2, trip, 2, listOf(hotel))
        assertEquals(hotel, e.startLodging)
        assertEquals(hotel, e.endLodging)
        assertNull(e.ends.firstId); assertNull(e.ends.lastId)
    }

    @Test fun lastDay_hotelStart_airportEnd() {
        val day4 = listOf(place(6, "우에노", 4, 1), place(7, "하네다 공항", 4, 2))
        val e = DayEndpoints.of(day4, trip, 4, listOf(hotel))
        assertEquals(hotel, e.startLodging)
        assertNull(e.endLodging)
        assertEquals(7L, e.ends.lastId)
    }

    @Test fun oldStyle_hotelAsPlace_notDoubled() {
        // 예전처럼 숙소를 장소로 맨 앞 · 맨 뒤에 넣어 둔 날은 장소를 그대로 씀
        val day2 = listOf(place(8, "호텔", 2, 1, 35.71, 139.79), place(4, "시부야", 2, 2), place(9, "호텔", 2, 3, 35.71, 139.79))
        val e = DayEndpoints.of(day2, trip, 2, listOf(hotel))
        assertNull(e.startLodging); assertNull(e.endLodging)
        assertEquals(8L, e.ends.firstId); assertEquals(9L, e.ends.lastId)
    }

    @Test fun lodgingWithoutLocation_fallsBackToPlaces() {
        val noLoc = hotel.copy(lat = 0.0, lng = 0.0)
        val day2 = listOf(place(4, "시부야", 2, 1), place(5, "신주쿠", 2, 2))
        val e = DayEndpoints.of(day2, trip, 2, listOf(noLoc))
        assertEquals(noLoc, e.startLodging)     // 화면에는 보이지만
        assertNull(e.ends.start)                // 경로에는 못 넣음 → 첫 장소 고정
        assertEquals(4L, e.ends.firstId)
    }

    @Test fun hotelChangeDay_checkoutStart_checkinEnd() {
        val b = Lodging(id = 8, tripId = 1, name = "다른 호텔", checkIn = "2026-11-22", checkOut = "2026-11-23", lat = 35.0, lng = 139.0)
        val a = hotel.copy(checkOut = "2026-11-22")
        val e = DayEndpoints.of(listOf(place(4, "시부야", 3, 1)), trip, 3, listOf(a, b))
        assertEquals(a, e.startLodging)
        assertEquals(b, e.endLodging)
    }
}
