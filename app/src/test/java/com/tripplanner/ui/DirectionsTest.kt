package com.tripplanner.ui

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Google Routes API 응답 해석 · 대중교통 시간표 날짜 */
class DirectionsTest {
    private fun step(mode: String, line: String? = null, short: String? = null) = JSONObject().put("travelMode", mode).apply {
        if (line != null || short != null) put("transitDetails", JSONObject().put("transitLine", JSONObject().apply {
            line?.let { put("name", it) }; short?.let { put("nameShort", it) } }))
    }

    @Test fun parseTransit() {
        val o = JSONObject().put("routes", JSONArray().put(JSONObject()
            .put("duration", "1290s").put("distanceMeters", 5300)
            .put("legs", JSONArray().put(JSONObject().put("steps", JSONArray()
                .put(step("WALK")).put(step("TRANSIT", "도쿄 메트로 한조몬선", "한조몬선")).put(step("WALK"))
                .put(step("TRANSIT", "긴자선")).put(step("TRANSIT", "긴자선")))))))
        val leg = Directions.parse(o)!!
        assertEquals(22, leg.minutes)          // 1290초 → 21.5 → 22분
        assertEquals(5300, leg.meters)
        assertEquals(3, leg.boardings)
        assertEquals(listOf("한조몬선", "긴자선"), leg.lines)
        assertEquals("한조몬선 외 1", Directions.linesLabel(leg.lines))
    }

    @Test fun parseWalkAndEmpty() {
        val walk = Directions.parse(JSONObject().put("routes", JSONArray().put(JSONObject().put("duration", "30s").put("distanceMeters", 40))))!!
        assertEquals(1, walk.minutes); assertEquals(0, walk.boardings); assertNull(Directions.linesLabel(walk.lines))
        assertNull(Directions.parse(JSONObject()))   // 길 없음
    }

    @Test fun departureDate() {
        val today = LocalDate.of(2026, 11, 17)   // 화요일
        // 30일 안의 여행 날은 그날
        assertEquals(LocalDate.of(2026, 11, 20), Directions.departureDate(LocalDate.of(2026, 11, 20), today))
        // 먼 미래 · 지난 날은 가장 가까운 같은 요일 (2027-02-12 금 → 11/20 금)
        assertEquals(LocalDate.of(2026, 11, 20), Directions.departureDate(LocalDate.of(2027, 2, 12), today))
        assertEquals(LocalDate.of(2026, 11, 18), Directions.departureDate(null, today))
    }
}
