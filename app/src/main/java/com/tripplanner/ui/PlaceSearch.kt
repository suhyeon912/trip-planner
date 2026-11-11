package com.tripplanner.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.tripplanner.BuildConfig
import com.tripplanner.R
import com.tripplanner.data.PlaceCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** 검색 결과 한 곳 */
data class SearchHit(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val category: PlaceCategory,
    /** "11:00 ~ 21:00" 형식 (구글만, 없으면 null) */
    val hours: String? = null
)

/**
 * 장소 검색 → 이름·주소·위도·경도·종류(·운영시간) 자동 입력.
 *  - 국내: 카카오 로컬 '키워드로 장소 검색' (REST API 키, 하루 10만 건 무료)
 *  - 해외: Google Places API (New) Text Search (지도와 같은 API 키, Places API (New) 사용 설정 필요)
 * 키는 local.properties → BuildConfig 로 들어옵니다 (깃에 올라가지 않음).
 */
object PlaceSearch {

    class NoKeyException(val messageRes: Int) : Exception()

    suspend fun search(context: Context, query: String, domestic: Boolean): List<SearchHit> =
        withContext(Dispatchers.IO) { if (domestic) kakao(query) else google(context, query) }

    // ---------------- 카카오 로컬 ----------------
    private fun kakao(query: String): List<SearchHit> {
        val key = BuildConfig.KAKAO_REST_API_KEY
        if (key.isBlank()) throw NoKeyException(R.string.search_no_kakao_key)
        val url = URL("https://dapi.kakao.com/v2/local/search/keyword.json?size=10&query=" + URLEncoder.encode(query, "UTF-8"))
        val json = http(url, "GET", mapOf("Authorization" to "KakaoAK $key"), null)
        val docs = JSONObject(json).getJSONArray("documents")
        return (0 until docs.length()).map { i ->
            val d = docs.getJSONObject(i)
            SearchHit(
                name = d.optString("place_name"),
                address = d.optString("road_address_name").ifBlank { d.optString("address_name") },
                lat = d.optString("y").toDoubleOrNull() ?: 0.0,
                lng = d.optString("x").toDoubleOrNull() ?: 0.0,
                category = kakaoCategory(d.optString("category_group_code"), d.optString("category_name"))
            )
        }
    }

    /** 카카오 분류 코드 → 장소 종류 (FD6 음식점, CE7 카페, AT4 관광명소, CT1 문화시설, MT1 마트 …) */
    internal fun kakaoCategory(code: String, name: String): PlaceCategory = when {
        code == "FD6" -> PlaceCategory.FOOD
        code == "CE7" -> PlaceCategory.CAFE
        code == "AT4" || code == "CT1" -> PlaceCategory.SIGHT
        code == "MT1" || code == "CS2" || name.contains("쇼핑") -> PlaceCategory.SHOPPING
        name.contains("테마파크") || name.contains("체험") || name.contains("레저") -> PlaceCategory.ACTIVITY
        else -> PlaceCategory.ETC
    }

    // ---------------- Google Places (New) ----------------
    private fun google(context: Context, query: String): List<SearchHit> {
        val key = BuildConfig.MAPS_API_KEY
        if (key.isBlank()) throw NoKeyException(R.string.search_no_google_key)
        val body = JSONObject().put("textQuery", query).put("languageCode", "ko").put("maxResultCount", 10).toString()
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "X-Goog-Api-Key" to key,
            "X-Goog-FieldMask" to "places.displayName,places.formattedAddress,places.location,places.primaryType,places.types,places.regularOpeningHours.periods",
            // 키를 'Android 앱'으로 제한했을 때 필요한 앱 정보
            "X-Android-Package" to context.packageName
        )
        certSha1(context)?.let { headers["X-Android-Cert"] = it }
        val json = http(URL("https://places.googleapis.com/v1/places:searchText"), "POST", headers, body)
        val arr = JSONObject(json).optJSONArray("places") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            val loc = p.optJSONObject("location")
            val types = buildList {
                p.optString("primaryType").takeIf { it.isNotBlank() }?.let { add(it) }
                p.optJSONArray("types")?.let { t -> for (j in 0 until t.length()) add(t.getString(j)) }
            }
            SearchHit(
                name = p.optJSONObject("displayName")?.optString("text").orEmpty(),
                address = p.optString("formattedAddress"),
                lat = loc?.optDouble("latitude") ?: 0.0,
                lng = loc?.optDouble("longitude") ?: 0.0,
                category = googleCategory(types),
                hours = googleHours(p.optJSONObject("regularOpeningHours"))
            )
        }
    }

    internal fun googleCategory(types: List<String>): PlaceCategory = when {
        types.any { it == "cafe" || it == "coffee_shop" || it == "bakery" } -> PlaceCategory.CAFE
        types.any { it.endsWith("restaurant") || it == "food" || it == "meal_takeaway" || it == "bar" } -> PlaceCategory.FOOD
        types.any { it in setOf("amusement_park", "zoo", "aquarium", "water_park", "bowling_alley", "spa") } -> PlaceCategory.ACTIVITY
        types.any { it.contains("store") || it == "shopping_mall" || it == "market" || it == "department_store" } -> PlaceCategory.SHOPPING
        types.any { it in setOf("tourist_attraction", "museum", "park", "historical_landmark", "art_gallery",
            "church", "place_of_worship", "point_of_interest", "natural_feature") } -> PlaceCategory.SIGHT
        else -> PlaceCategory.ETC
    }

    /** 여는 시각 중 가장 이른 것 ~ 닫는 시각 중 가장 늦은 것 (요일별로 다르면 대표값) */
    internal fun googleHours(h: JSONObject?): String? {
        val periods = h?.optJSONArray("periods") ?: return null
        var open = Int.MAX_VALUE; var close = -1
        for (i in 0 until periods.length()) {
            val p = periods.getJSONObject(i)
            val o = p.optJSONObject("open") ?: continue
            val c = p.optJSONObject("close") ?: return "00:00 ~ 24:00"   // 닫는 시각 없음 = 24시간
            open = minOf(open, o.optInt("hour") * 60 + o.optInt("minute"))
            var cm = c.optInt("hour") * 60 + c.optInt("minute")
            if (c.optInt("day") != o.optInt("day") && cm <= 6 * 60) cm += 24 * 60   // 자정 넘김
            close = maxOf(close, cm)
        }
        if (open == Int.MAX_VALUE || close < 0) return null
        fun f(m: Int) = "%02d:%02d".format(minOf(m, 24 * 60) / 60, minOf(m, 24 * 60) % 60)
        return "${f(open)} ~ ${f(close)}"
    }

    // ---------------- 공통 ----------------
    private fun http(url: URL, method: String, headers: Map<String, String>, body: String?): String {
        val c = url.openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 8000; c.readTimeout = 8000
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val msg = runCatching {
                val o = JSONObject(text)
                o.optJSONObject("error")?.optString("message") ?: o.optString("message")
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $code"
            throw IllegalStateException(msg)
        }
        return text
    }

    /** 앱 서명 SHA-1 (대문자 16진수, 콜론 없음) — 구글 키의 Android 앱 제한 통과용 */
    @Suppress("DEPRECATION")
    private fun certSha1(context: Context): String? = runCatching {
        val pm = context.packageManager
        val sig = if (Build.VERSION.SDK_INT >= 28)
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
        else pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        val d = MessageDigest.getInstance("SHA-1").digest(sig!!.toByteArray())
        d.joinToString("") { "%02X".format(it) }
    }.getOrNull()
}
