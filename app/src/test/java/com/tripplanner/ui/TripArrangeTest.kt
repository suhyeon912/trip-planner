package com.tripplanner.ui

import com.tripplanner.data.Trip
import com.tripplanner.data.TripWithCounts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TripArrangeTest {
    private val today = LocalDate.of(2026, 11, 7)
    private fun t(id: Long, title: String, start: String, end: String) =
        TripWithCounts(Trip(id = id, title = title, startDate = start, endDate = end), 0, 0)
    private val list = listOf(
        t(1, "타이베이", "2026-12-25", "2026-12-27"),
        t(2, "도쿄", "2026-11-20", "2026-11-23"),
        t(3, "오사카", "2026-08-14", "2026-08-17"),   // 지난 여행
        t(4, "부산", "2027-02-12", "2027-02-14"),
        t(5, "제주", "2026-03-01", "2026-03-02")      // 지난 여행
    )
    private fun titles(rows: List<TripRow>) = rows.map {
        when (it) { is TripRow.Card -> it.item.trip.title; is TripRow.Past -> "~" + it.item.trip.title; is TripRow.PastHeader -> "#${it.count}" }
    }

    @Test fun dateSortPutsPastBelow() {
        assertEquals(listOf("도쿄", "타이베이", "부산", "#2", "~오사카", "~제주"),
            titles(TripArrange.rows(list, TripSort.DATE, showPast = true, today = today)))
    }

    @Test fun nameAndRecent() {
        assertEquals(listOf("도쿄", "부산", "타이베이"), titles(TripArrange.rows(list, TripSort.NAME, false, today)))
        assertEquals(listOf("부산", "도쿄", "타이베이"), titles(TripArrange.rows(list, TripSort.RECENT, false, today)))
    }

    @Test fun filterMineAndInvited() {
        val mixed = list.mapIndexed { i, t -> if (i == 0) t.copy(trip = t.trip.copy(ownedByMe = false, inviterName = "민지")) else t }
        assertEquals(listOf("타이베이"), titles(TripArrange.rows(mixed, TripSort.DATE, true, today, TripFilter.INVITED)))
        assertEquals(listOf("도쿄", "부산", "#2", "~오사카", "~제주"), titles(TripArrange.rows(mixed, TripSort.DATE, true, today, TripFilter.MINE)))
    }
}
