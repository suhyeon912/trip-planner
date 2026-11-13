package com.tripplanner.ui

import android.content.Context
import com.tripplanner.route.LegMode
import com.tripplanner.route.LegTime
import com.tripplanner.route.RoutePlanner

/**
 * 경로 구간마다 고른 이동 수단 (기기에 여행마다 저장, 공유하지 않음).
 * 경로 제안 시간표와 지도 시간 이름표가 같은 값을 씁니다.
 */
class LegModes(context: Context, private val tripId: Long) {
    private val prefs = context.applicationContext.getSharedPreferences("leg_modes", Context.MODE_PRIVATE)
    private var modes: Map<String, LegMode> = load()

    private fun key(from: Long, to: Long) = "$from>$to"

    private fun load(): Map<String, LegMode> = prefs.getString("trip_$tripId", "").orEmpty().split(";").mapNotNull { e ->
        val parts = e.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
        LegMode.entries.firstOrNull { it.name == parts[1] }?.let { parts[0] to it }
    }.toMap()

    /** 이 구간의 수단 (고르지 않았으면 거리로 정함) */
    fun mode(from: Long, to: Long, km: Double): LegMode = modes[key(from, to)] ?: RoutePlanner.defaultMode(km)

    val legTime: LegTime = { f, t, km -> RoutePlanner.modeMinutes(mode(f, t, km), km) }

    fun set(legs: List<Pair<Long, Long>>, mode: LegMode) {
        modes = modes + legs.associate { (f, t) -> key(f, t) to mode }
        prefs.edit().putString("trip_$tripId", modes.entries.joinToString(";") { "${it.key}=${it.value.name}" }).apply()
    }

    /** 다른 화면에서 바꿨을 수 있으니 다시 읽기 */
    fun reload() { modes = load() }
}
