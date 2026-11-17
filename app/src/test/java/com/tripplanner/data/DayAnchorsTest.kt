package com.tripplanner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DayAnchorsTest {
    private val hotelA = Lodging(id = 1, tripId = 1, name = "A 호텔", checkIn = "2026-11-20", checkOut = "2026-11-22", lat = 35.0, lng = 139.0)
    private val hotelB = Lodging(id = 2, tripId = 1, name = "B 호텔", checkIn = "2026-11-22", checkOut = "2026-11-23", lat = 35.1, lng = 139.1)
    private fun place(id: Long, name: String) = Place(id = id, tripId = 1, name = name, status = PlaceStatus.CONFIRMED, openingHours = "", day = 1)

    @Test fun roundTrip_andDefaultsDropped() {
        var raw = ""
        raw = DayAnchors.with(raw, 1, start = true, value = Endpoint.AtPlace("공항\t1"))
        raw = DayAnchors.with(raw, 1, start = false, value = Endpoint.AtLodging("B 호텔"))
        raw = DayAnchors.with(raw, 3, start = false, value = Endpoint.None)
        val map = DayAnchors.parse(raw)
        assertEquals(DayEnds(Endpoint.AtPlace("공항 1"), Endpoint.AtLodging("B 호텔")), map[1])
        assertEquals(DayEnds(Endpoint.Default, Endpoint.None), map[3])
        // 기본으로 되돌리면 그 일차 줄은 사라짐
        raw = DayAnchors.with(raw, 3, start = false, value = Endpoint.Default)
        assertEquals(null, DayAnchors.parse(raw)[3])
        assertEquals(emptyMap<Int, DayEnds>(), DayAnchors.parse(""))
    }

    @Test fun resolve_fallsBackToDefault() {
        val places = listOf(place(10, "츠지한"), place(11, "긴자 식스"))
        assertEquals(DayAnchors.Resolved.Lodge(hotelA), DayAnchors.resolve(Endpoint.Default, hotelA, listOf(hotelA, hotelB), places))
        assertEquals(DayAnchors.Resolved.Lodge(hotelB), DayAnchors.resolve(Endpoint.AtLodging("B 호텔"), hotelA, listOf(hotelA, hotelB), places))
        assertEquals(DayAnchors.Resolved.AtPlace(places[1]), DayAnchors.resolve(Endpoint.AtPlace("긴자 식스"), hotelA, listOf(hotelA), places))
        // 없어진 숙소 · 그날이 아닌 장소 → 그날 숙소
        assertEquals(DayAnchors.Resolved.Lodge(hotelA), DayAnchors.resolve(Endpoint.AtLodging("C 호텔"), hotelA, listOf(hotelA), places))
        assertEquals(DayAnchors.Resolved.Lodge(hotelA), DayAnchors.resolve(Endpoint.AtPlace("센소지"), hotelA, listOf(hotelA), places))
        assertEquals(DayAnchors.Resolved.None, DayAnchors.resolve(Endpoint.None, hotelA, listOf(hotelA), places))
        assertEquals(DayAnchors.Resolved.None, DayAnchors.resolve(Endpoint.Default, null, emptyList(), places))
    }

    @Test fun resolve_placeOutsideTheDay_isSpot() {
        val today = listOf(place(10, "츠지한"))
        val airport = Place(id = 20, tripId = 1, name = "나리타 공항", status = PlaceStatus.CONFIRMED, openingHours = "", day = 3, lat = 35.77, lng = 140.39)
        val cand = Place(id = 21, tripId = 1, name = "도쿄역", status = PlaceStatus.CANDIDATE, openingHours = "", lat = 35.68, lng = 139.76)
        val noLoc = Place(id = 22, tripId = 1, name = "어딘가", status = PlaceStatus.CANDIDATE, openingHours = "")
        val all = today + airport + cand + noLoc
        // 다른 날 · 후보 장소 → 출발 · 도착점 (들르는 장소로 세지 않음)
        assertEquals(DayAnchors.Resolved.Spot(airport), DayAnchors.resolve(Endpoint.AtPlace("나리타 공항"), hotelA, listOf(hotelA), today, all))
        assertEquals(DayAnchors.Resolved.Spot(cand), DayAnchors.resolve(Endpoint.AtPlace("도쿄역"), hotelA, listOf(hotelA), today, all))
        // 그날 장소면 그대로 처음 · 마지막에 들르는 장소
        assertEquals(DayAnchors.Resolved.AtPlace(today[0]), DayAnchors.resolve(Endpoint.AtPlace("츠지한"), hotelA, listOf(hotelA), today, all))
        // 위치 없는 장소는 출발점이 될 수 없어 기본으로
        assertEquals(DayAnchors.Resolved.Lodge(hotelA), DayAnchors.resolve(Endpoint.AtPlace("어딘가"), hotelA, listOf(hotelA), today, all))
    }

    @Test fun renamePlace() {
        val raw = DayAnchors.with("", 2, start = true, value = Endpoint.AtPlace("츠지한"))
        assertEquals(DayEnds(Endpoint.AtPlace("츠지한 니혼바시"), Endpoint.Default),
            DayAnchors.parse(DayAnchors.renamePlace(raw, "츠지한", "츠지한 니혼바시"))[2])
    }
}
