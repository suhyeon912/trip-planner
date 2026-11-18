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

    @Test fun renamePlace() {
        val raw = DayAnchors.with("", 2, start = true, value = Endpoint.AtPlace("츠지한"))
        assertEquals(DayEnds(Endpoint.AtPlace("츠지한 니혼바시"), Endpoint.Default),
            DayAnchors.parse(DayAnchors.renamePlace(raw, "츠지한", "츠지한 니혼바시"))[2])
    }
}
