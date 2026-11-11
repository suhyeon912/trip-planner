package com.tripplanner.ui

import com.tripplanner.data.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TripFormatTest {
    private val trip = Trip(title = "부산", startDate = "2027-02-12", endDate = "2027-02-14")

    @Test fun dday() {
        assertEquals("D-97", TripFormat.dday(trip, LocalDate.of(2026, 11, 7))!!.text)
        assertEquals("D-1", TripFormat.dday(trip, LocalDate.of(2027, 2, 11))!!.text)
        assertEquals(TripFormat.Phase.TODAY, TripFormat.dday(trip, LocalDate.of(2027, 2, 12))!!.phase)
        assertEquals("여행 중", TripFormat.dday(trip, LocalDate.of(2027, 2, 14))!!.text)
        assertEquals("지난 여행", TripFormat.dday(trip, LocalDate.of(2027, 2, 15))!!.text)
        assertNull(TripFormat.dday(trip.copy(startDate = "2/12"), LocalDate.of(2026, 11, 7)))
    }

    @Test fun dateRange() {
        assertEquals("2/12(금) – 2/14(일) · 3일", TripFormat.dateRange(trip))
        assertEquals("2/12 ~ 2027-02-14", TripFormat.dateRange(trip.copy(startDate = "2/12")))
    }

    @Test fun fullRange() {
        assertEquals("2027.2.12 (금) – 2.14 (일)", TripFormat.fullRange(trip))
        assertEquals("2026.12.30 (수) – 2027.1.2 (토)",
            TripFormat.fullRange(trip.copy(startDate = "2026-12-30", endDate = "2027-01-02")))
    }

    @Test fun nightsDays() {
        assertEquals("2박 3일", TripFormat.nightsDays("2027-02-12", "2027-02-14"))
        assertEquals("1일", TripFormat.nightsDays("2027-02-12", "2027-02-12"))
        assertNull(TripFormat.nightsDays("2027-02-14", "2027-02-12"))
        assertNull(TripFormat.nightsDays("", ""))
    }
}
