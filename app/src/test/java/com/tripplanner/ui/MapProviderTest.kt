package com.tripplanner.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 여행 전체가 국내면 카카오, 해외가 한 곳이라도 있으면 구글, 키가 없으면 OpenStreetMap */
class MapProviderTest {
    private val busan = listOf(35.0975 to 129.0106, 35.1587 to 129.1604)
    private val osaka = listOf(34.6873 to 135.5262, 34.6687 to 135.5013)
    private val incheonAirport = 37.4602 to 126.4407

    @Test fun domesticUsesKakaoOverseasUsesGoogle() {
        assertEquals(MapProvider.KAKAO, pickProvider(busan, null, kakaoOk = true, googleOk = true))
        assertEquals(MapProvider.GOOGLE, pickProvider(osaka, null, kakaoOk = true, googleOk = true))
    }

    @Test fun departingFromKoreaToOverseasUsesGoogle() {
        // 국내 출발 해외여행: 인천공항을 장소로 넣어도(국내 1 + 해외 1, 국내가 더 많아도) 구글
        assertEquals(MapProvider.GOOGLE, pickProvider(listOf(incheonAirport) + osaka.take(1), null, true, true))
        assertEquals(MapProvider.GOOGLE, pickProvider(busan + incheonAirport + osaka.take(1), null, true, true))
    }

    @Test fun missingKeyFallsBackToOsm() {
        assertEquals(MapProvider.OSM, pickProvider(busan, null, kakaoOk = false, googleOk = true))
        assertEquals(MapProvider.OSM, pickProvider(osaka, null, kakaoOk = true, googleOk = false))
    }

    @Test fun emptyKeepsCurrent() {
        assertEquals(MapProvider.GOOGLE, pickProvider(emptyList(), MapProvider.GOOGLE, true, true))
        assertEquals(MapProvider.KAKAO, pickProvider(emptyList(), null, true, true))
    }
}
