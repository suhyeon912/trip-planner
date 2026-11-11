package com.tripplanner.ui

import com.tripplanner.data.PlaceCategory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 장소 검색 결과 → 종류·운영시간 변환 */
class PlaceSearchTest {
    @Test fun kakaoCategories() {
        assertEquals(PlaceCategory.FOOD, PlaceSearch.kakaoCategory("FD6", "음식점 > 한식"))
        assertEquals(PlaceCategory.CAFE, PlaceSearch.kakaoCategory("CE7", "음식점 > 카페"))
        assertEquals(PlaceCategory.SIGHT, PlaceSearch.kakaoCategory("AT4", "여행 > 관광,명소"))
        assertEquals(PlaceCategory.ACTIVITY, PlaceSearch.kakaoCategory("", "여행 > 테마파크"))
        assertEquals(PlaceCategory.ETC, PlaceSearch.kakaoCategory("", "교통 > 지하철역"))
    }

    @Test fun googleCategories() {
        assertEquals(PlaceCategory.FOOD, PlaceSearch.googleCategory(listOf("ramen_restaurant", "food")))
        assertEquals(PlaceCategory.CAFE, PlaceSearch.googleCategory(listOf("cafe", "food")))
        assertEquals(PlaceCategory.SIGHT, PlaceSearch.googleCategory(listOf("tourist_attraction")))
        assertEquals(PlaceCategory.ACTIVITY, PlaceSearch.googleCategory(listOf("amusement_park")))
        assertEquals(PlaceCategory.SHOPPING, PlaceSearch.googleCategory(listOf("shopping_mall")))
    }

    private fun period(oDay: Int, oh: Int, om: Int, cDay: Int?, ch: Int = 0, cm: Int = 0) = JSONObject()
        .put("open", JSONObject().put("day", oDay).put("hour", oh).put("minute", om))
        .apply { if (cDay != null) put("close", JSONObject().put("day", cDay).put("hour", ch).put("minute", cm)) }

    @Test fun googleHours() {
        // 요일마다 다르면 가장 이른 시작 ~ 가장 늦은 끝
        val h = JSONObject().put("periods", org.json.JSONArray()
            .put(period(1, 9, 0, 1, 17, 0)).put(period(6, 9, 30, 6, 18, 0)))
        assertEquals("09:00 ~ 18:00", PlaceSearch.googleHours(h))
        // 자정 넘김 (17:00 ~ 다음날 01:00) → 24:00 으로 표시
        val night = JSONObject().put("periods", org.json.JSONArray().put(period(1, 17, 0, 2, 1, 0)))
        assertEquals("17:00 ~ 24:00", PlaceSearch.googleHours(night))
        // 닫는 시각 없음 = 24시간
        val allDay = JSONObject().put("periods", org.json.JSONArray().put(period(0, 0, 0, null)))
        assertEquals("00:00 ~ 24:00", PlaceSearch.googleHours(allDay))
        assertNull(PlaceSearch.googleHours(null))
    }
}
