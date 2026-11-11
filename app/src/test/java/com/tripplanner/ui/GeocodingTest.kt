package com.tripplanner.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GeocodingTest {
    @Test fun keepsCoordsWhenAddressUnchangedOrEmpty() {
        assertEquals(1.0 to 2.0, Geocoding.resolve("도쿄도 주오구", "도쿄도 주오구", 1.0, 2.0, Geocoding.Result.NotFound))
        assertEquals(1.0 to 2.0, Geocoding.resolve("  ", null, 1.0, 2.0, Geocoding.Result.NotFound))
    }

    @Test fun changedAddressFollowsGeocoder() {
        assertEquals(35.0 to 139.0, Geocoding.resolve("새 주소", "옛 주소", 1.0, 2.0, Geocoding.Result.Found(35.0, 139.0)))
        assertEquals(0.0 to 0.0, Geocoding.resolve("새 주소", "옛 주소", 1.0, 2.0, Geocoding.Result.NotFound))
        // 기기에 지오코더가 없으면 기존 위치 유지
        assertEquals(1.0 to 2.0, Geocoding.resolve("새 주소", null, 1.0, 2.0, Geocoding.Result.Unavailable))
    }
}
