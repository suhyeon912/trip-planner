package com.tripplanner.route

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionTest {
    @Test fun koreanPlacesAreDomestic() {
        listOf(
            "서울" to (37.5665 to 126.9780), "인천공항" to (37.4602 to 126.4407), "강릉" to (37.7519 to 128.8761),
            "부산 해운대" to (35.1587 to 129.1604), "부산 태종대" to (35.0532 to 129.0870), "거제" to (34.8806 to 128.6211),
            "여수" to (34.7604 to 127.6622), "목포" to (34.8118 to 126.3922), "제주시" to (33.4996 to 126.5312),
            "서귀포" to (33.2541 to 126.5600), "울릉도" to (37.4844 to 130.9057), "독도" to (37.2426 to 131.8669),
            "백령도" to (37.9670 to 124.7150), "속초" to (38.2070 to 128.5918)
        ).forEach { (name, c) -> assertTrue(name, Region.isKorea(c.first, c.second)) }
    }

    @Test fun overseasPlacesAreNot() {
        listOf(
            "후쿠오카" to (33.5904 to 130.4017), "쓰시마 이즈하라" to (34.2029 to 129.2875),
            "쓰시마 북부" to (34.6600 to 129.4500), "오사카" to (34.6937 to 135.5023), "도쿄" to (35.6762 to 139.6503),
            "타이베이" to (25.0330 to 121.5654), "상하이" to (31.2304 to 121.4737), "칭다오" to (36.0671 to 120.3826),
            "평양" to (39.0392 to 125.7625), "원산" to (39.1528 to 127.4436), "해주" to (38.0406 to 125.7147), "블라디보스토크" to (43.1155 to 131.8855)
        ).forEach { (name, c) -> assertFalse(name, Region.isKorea(c.first, c.second)) }
    }
}
