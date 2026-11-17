package com.tripplanner.ui

import android.content.Context
import com.tripplanner.BuildConfig
import com.tripplanner.route.LegMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId

/**
 * 실제 길찾기 (Google Routes API · computeRoutes).
 *  · 구간 하나 = 출발 좌표 → 도착 좌표 + 수단(도보 · 대중교통 · 차) → 걸리는 분 · 거리 · 노선 이름 · 타는 횟수
 *  · 결과는 기기에 30일 저장해서 같은 구간을 다시 묻지 않음 (요금 · 속도)
 *  · 길이 없거나 지원하지 않는 곳(예: 한국의 도보 · 차)은 1일 동안 '없음'으로 저장 → 어림값으로 계산
 * 키는 장소 검색 · 지도와 같은 MAPS_API_KEY, Google Cloud 에서 Routes API 를 켜야 함.
 */
object Directions {

    data class Point(val lat: Double, val lng: Double) {
        fun key() = "%.5f,%.5f".format(java.util.Locale.US, lat, lng)
    }

    /** 받은 구간 정보. lines = 탄 노선 이름들 (대중교통), boardings = 탄 횟수 */
    data class Leg(val minutes: Int, val meters: Int, val lines: List<String>, val boardings: Int)

    /** Routes API 를 쓸 수 없음 (키 없음 · API 꺼짐 · 키 제한) */
    class Unavailable(message: String) : Exception(message)

    private const val OK_TTL = 30L * 24 * 3600 * 1000
    private const val FAIL_TTL = 24L * 3600 * 1000
    private const val PREFS = "directions_cache"

    fun available() = BuildConfig.MAPS_API_KEY.isNotBlank()

    private fun cacheKey(mode: LegMode, a: Point, b: Point) = "${mode.name}|${a.key()}|${b.key()}"

    /** 저장된 결과: Leg / null(길 없음으로 저장됨) / 묻지 않음은 NotCached */
    sealed class Cached { data class Hit(val leg: Leg?) : Cached(); data object Miss : Cached() }

    fun cached(context: Context, mode: LegMode, a: Point, b: Point): Cached {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(cacheKey(mode, a, b), null) ?: return Cached.Miss
        val p = raw.split('\t')
        val at = p.getOrNull(0)?.toLongOrNull() ?: return Cached.Miss
        val now = System.currentTimeMillis()
        if (p.getOrNull(1) == "X") return if (now - at < FAIL_TTL) Cached.Hit(null) else Cached.Miss
        if (now - at > OK_TTL) return Cached.Miss
        val min = p.getOrNull(1)?.toIntOrNull() ?: return Cached.Miss
        val m = p.getOrNull(2)?.toIntOrNull() ?: 0
        val b = p.getOrNull(3)?.toIntOrNull() ?: 0
        val lines = p.getOrNull(4).orEmpty().split('|').filter { it.isNotBlank() }
        return Cached.Hit(Leg(min, m, lines, b))
    }

    private fun store(context: Context, mode: LegMode, a: Point, b: Point, leg: Leg?) {
        val v = if (leg == null) "${System.currentTimeMillis()}\tX"
                else "${System.currentTimeMillis()}\t${leg.minutes}\t${leg.meters}\t${leg.boardings}\t" +
                    leg.lines.joinToString("|") { it.replace('|', ' ').replace('\t', ' ') }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(cacheKey(mode, a, b), v).apply()
    }

    /** 대중교통 시간표 기준 날짜: 여행 날이 30일 안이면 그날, 아니면 가장 가까운 같은 요일 (먼 미래는 시간표가 없어서) */
    fun departureDate(tripDate: LocalDate?, today: LocalDate = LocalDate.now()): LocalDate {
        if (tripDate != null && !tripDate.isBefore(today.plusDays(1)) && !tripDate.isAfter(today.plusDays(30))) return tripDate
        var d = today.plusDays(1)
        if (tripDate != null) while (d.dayOfWeek != tripDate.dayOfWeek) d = d.plusDays(1)
        return d
    }

    /**
     * 여러 구간을 한꺼번에 (이미 저장된 것은 건너뜀, 동시에 4개씩).
     * @return 새로 받은 구간 수. Routes API 를 못 쓰면 Unavailable
     */
    suspend fun fetchAll(context: Context, legs: List<Triple<LegMode, Point, Point>>, date: LocalDate, minutes: Int): Int =
        withContext(Dispatchers.IO) {
            if (!available()) throw Unavailable("no key")
            val todo = legs.distinct().filter { (m, a, b) -> cached(context, m, a, b) is Cached.Miss && a != b }
            if (todo.isEmpty()) return@withContext 0
            val gate = Semaphore(4)
            var unavailable: Unavailable? = null
            coroutineScope {
                todo.map { (m, a, b) ->
                    async {
                        gate.withPermit {
                            if (unavailable != null) return@withPermit 0
                            try {
                                store(context, m, a, b, route(context, m, a, b, date, minutes)); 1
                            } catch (e: Unavailable) { unavailable = e; 0 }
                            catch (e: Exception) { 0 }   // 일시 오류(인터넷 등)는 저장하지 않고 다음에 다시
                        }
                    }
                }.awaitAll().sum()
            }.also { unavailable?.let { throw it } }
        }

    /** 구간 하나 묻기. 길이 없으면 null */
    private fun route(context: Context, mode: LegMode, a: Point, b: Point, date: LocalDate, minutes: Int): Leg? {
        val body = JSONObject()
            .put("origin", loc(a)).put("destination", loc(b))
            .put("travelMode", when (mode) { LegMode.WALK -> "WALK"; LegMode.TRANSIT -> "TRANSIT"; LegMode.TAXI -> "DRIVE" })
            .put("languageCode", "ko").put("units", "METRIC").put("computeAlternativeRoutes", false)
        if (mode == LegMode.TRANSIT) {
            val t = date.atTime(minutes / 60 % 24, minutes % 60).atZone(ZoneId.systemDefault()).toInstant()
            body.put("departureTime", t.toString())
        }
        if (mode == LegMode.TAXI) body.put("routingPreference", "TRAFFIC_UNAWARE")
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "X-Goog-Api-Key" to BuildConfig.MAPS_API_KEY,
            "X-Goog-FieldMask" to "routes.duration,routes.distanceMeters,routes.legs.steps.travelMode," +
                "routes.legs.steps.transitDetails.transitLine.name,routes.legs.steps.transitDetails.transitLine.nameShort",
            "X-Android-Package" to context.packageName
        )
        PlaceSearch.certSha1(context)?.let { headers["X-Android-Cert"] = it }
        val text = try {
            PlaceSearch.http(URL("https://routes.googleapis.com/directions/v2:computeRoutes"), "POST", headers, body.toString())
        } catch (e: IllegalStateException) {
            val msg = e.message.orEmpty()
            if (listOf("has not been used", "disabled", "PERMISSION_DENIED", "API key not valid", "not authorized", "blocked")
                    .any { msg.contains(it, ignoreCase = true) }) throw Unavailable(msg)
            return null   // 길이 없음 · 지원하지 않는 곳 (하루 동안 다시 묻지 않음)
        }
        return parse(JSONObject(text))
    }

    private fun loc(p: Point) = JSONObject().put("location", JSONObject().put("latLng",
        JSONObject().put("latitude", p.lat).put("longitude", p.lng)))

    /** 응답 → 구간 (길이 없으면 null) */
    internal fun parse(o: JSONObject): Leg? {
        val r = o.optJSONArray("routes")?.optJSONObject(0) ?: return null
        val sec = r.optString("duration").removeSuffix("s").toDoubleOrNull() ?: return null
        val lines = mutableListOf<String>()
        var boardings = 0
        val legs = r.optJSONArray("legs")
        for (i in 0 until (legs?.length() ?: 0)) {
            val steps = legs!!.getJSONObject(i).optJSONArray("steps") ?: continue
            for (j in 0 until steps.length()) {
                val s = steps.getJSONObject(j)
                if (s.optString("travelMode") != "TRANSIT") continue
                boardings++
                val line = s.optJSONObject("transitDetails")?.optJSONObject("transitLine")
                val name = line?.optString("nameShort").orEmpty().ifBlank { line?.optString("name").orEmpty() }
                if (name.isNotBlank() && name !in lines) lines += name
            }
        }
        return Leg(Math.round(sec / 60).toInt().coerceAtLeast(1), r.optInt("distanceMeters"), lines, boardings)
    }

    /** "도부 스카이트리선" / "한조몬선 외 1" */
    fun linesLabel(lines: List<String>): String? = when (lines.size) {
        0 -> null
        1 -> lines[0]
        else -> "${lines[0]} 외 ${lines.size - 1}"
    }
}
