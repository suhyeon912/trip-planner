package com.tripplanner.share

import com.tripplanner.data.Flight
import com.tripplanner.data.Lodging
import com.tripplanner.data.Place
import com.tripplanner.data.PlaceCategory
import com.tripplanner.data.PlaceStatus
import com.tripplanner.data.TransportMode
import com.tripplanner.data.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCodecTest {
    private val trip = Trip(id = 7, title = "오사카", startDate = "2026-12-20", endDate = "2026-12-23", note = "메모",
        startMinutes = 540, shareCode = "ABC234")
    private val snap = TripSnapshot(
        trip,
        listOf(Place(id = 3, tripId = 7, name = "오사카성", status = PlaceStatus.CONFIRMED, openingHours = "09:00 ~ 17:00",
            lat = 34.68, lng = 135.52, day = 1, orderInDay = 2, stayMinutes = 120, category = PlaceCategory.SIGHT,
            photoUri = "/data/user/0/x/files/photos/a.jpg", address = "오사카시 주오구", addressDetail = "천수각",
            locationMemo = "오사카성공원역에서 걸어서 20분")),
        listOf(Flight(id = 1, tripId = 7, flightNo = "KTX 101", fromAirport = "서울역", toAirport = "부산역",
            departAt = "2027-02-12 07:00", arriveAt = "2027-02-12 09:40", mode = TransportMode.TRAIN)),
        listOf(Lodging(id = 2, tripId = 7, name = "호텔", checkIn = "2026-12-20", checkOut = "2026-12-23", lat = 34.66, lng = 135.50))
    )

    @Test fun roundTrip_keepsContent_dropsDeviceOnlyValues() {
        val other = Trip(id = 99, title = "", startDate = "", endDate = "", shareCode = "ABC234")
        val back = ShareCodec.fromMap(ShareCodec.toMap(snap), other)
        assertEquals(99, back.trip.id)                 // 받는 기기의 id 유지
        assertEquals("오사카", back.trip.title); assertEquals(540, back.trip.startMinutes)
        val p = back.places.single()
        assertEquals("오사카성", p.name); assertEquals(2, p.orderInDay); assertEquals(PlaceCategory.SIGHT, p.category)
        assertNull(p.photoUri)                         // 사진 경로는 기기마다 달라서 공유 안 함
        assertEquals(TransportMode.TRAIN, back.flights.single().mode)
        assertEquals(34.66, back.lodgings.single().lat, 1e-9)
        assertEquals("오사카시 주오구", p.address); assertEquals("천수각", p.addressDetail)
        assertEquals("오사카성공원역에서 걸어서 20분", p.locationMemo)
        assertEquals("", back.lodgings.single().address)
    }

    @Test fun oldServerDocument_withoutAddress_readsEmpty() {
        val m = mapOf("title" to "x", "places" to listOf(mapOf("name" to "a")), "lodgings" to listOf(mapOf("name" to "h")))
        val back = ShareCodec.fromMap(m, trip)
        assertEquals("", back.places.single().address); assertEquals("", back.lodgings.single().locationMemo)
    }

    @Test fun serverNumbersAsLong_andUnknownEnums() {
        val m = mapOf("title" to "x", "startMinutes" to 600L,
            "places" to listOf(mapOf("name" to "a", "status" to "WHAT", "lat" to 35L, "category" to "NEW_KIND", "day" to 2L)))
        val p = ShareCodec.fromMap(m, trip).places.single()
        assertEquals(PlaceStatus.CANDIDATE, p.status); assertEquals(PlaceCategory.SIGHT, p.category)
        assertEquals(35.0, p.lat, 0.0); assertEquals(2, p.day)
    }

    @Test fun codes() {
        repeat(50) { assertTrue(ShareCodec.isValidCode(ShareCodec.newCode())) }
        assertEquals("ABC234", ShareCodec.normalize(" abc-234 "))
        assertFalse(ShareCodec.isValidCode("ABC10O"))
    }
}
