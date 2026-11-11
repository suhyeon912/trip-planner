package com.tripplanner.ui

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 직접 쓴 주소 → 위도·경도 (Android 기본 Geocoder, API 키 필요 없음).
 * 결과: Found(위치) / NotFound(못 찾음 → 위치 없음으로) / Unavailable(기기에 기능이 없거나 오류 → 기존 위치 유지)
 */
object Geocoding {
    sealed class Result {
        data class Found(val lat: Double, val lng: Double) : Result()
        object NotFound : Result()
        object Unavailable : Result()
    }

    suspend fun find(context: Context, address: String): Result = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext Result.Unavailable
        runCatching {
            @Suppress("DEPRECATION")
            val list = Geocoder(context, Locale.KOREAN).getFromLocationName(address, 1)
            list?.firstOrNull()?.let { Result.Found(it.latitude, it.longitude) } ?: Result.NotFound
        }.getOrDefault(Result.Unavailable)
    }

    /**
     * 저장할 위치 결정 (안드로이드와 무관 → 단위 테스트).
     * 주소를 바꾸지 않았으면(coordsAddress 와 같음) 기존 위치 그대로, 바꿨으면 지오코딩 결과를 따름.
     */
    fun resolve(address: String, coordsAddress: String?, lat: Double, lng: Double, result: Result?): Pair<Double, Double> {
        val a = address.trim()
        if (a.isEmpty() || a == coordsAddress?.trim()) return lat to lng
        return when (result) {
            is Result.Found -> result.lat to result.lng
            Result.NotFound -> 0.0 to 0.0
            Result.Unavailable, null -> lat to lng
        }
    }
}
