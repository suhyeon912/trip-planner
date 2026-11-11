package com.tripplanner.route

/**
 * 좌표가 대한민국(제주·울릉도·독도 포함) 안인지 판별.
 * 단순 사각형으로 하면 후쿠오카·쓰시마까지 들어가서, 대략적인 다각형으로 판별합니다.
 */
object Region {

    // (경도, 위도) 순서로 남한을 감싸는 대략적인 다각형
    private val korea = listOf(
        124.5 to 38.1, 126.1 to 37.8, 127.0 to 38.3, 128.4 to 38.7, 129.0 to 38.6,
        130.5 to 38.0, 132.0 to 37.6, 132.0 to 36.9, 129.8 to 36.0, 129.6 to 35.4,
        129.35 to 35.05, 128.7 to 34.6, 128.0 to 34.4, 127.2 to 33.9, 127.1 to 33.0,
        126.0 to 33.0, 125.8 to 33.6, 124.9 to 34.0, 124.5 to 35.0
    )

    fun isKorea(lat: Double, lng: Double): Boolean {
        // 광선 투사법: 점에서 오른쪽으로 선을 그어 다각형 변과 몇 번 만나는지 셈 (홀수면 안쪽)
        var inside = false
        var j = korea.size - 1
        for (i in korea.indices) {
            val (xi, yi) = korea[i]
            val (xj, yj) = korea[j]
            if ((yi > lat) != (yj > lat) && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}
