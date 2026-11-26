package com.tripplanner.ui

import android.content.Context
import com.tripplanner.R
import java.time.Instant
import java.time.ZoneId

/** 여행지 시간대 고르기 (자주 가는 곳 위주). 저장은 IANA 이름, 빈 글자 = 기기 시간대 */
object TimeZones {
    /** (IANA, 이름) */
    val LIST = listOf(
        "Asia/Seoul" to "한국",
        "Asia/Tokyo" to "일본",
        "Asia/Taipei" to "대만 · 중국 · 홍콩",
        "Asia/Singapore" to "싱가포르 · 말레이시아",
        "Asia/Manila" to "필리핀",
        "Asia/Bangkok" to "태국 · 베트남",
        "Asia/Makassar" to "인도네시아 발리",
        "Pacific/Guam" to "괌 · 사이판",
        "Australia/Sydney" to "호주 시드니",
        "Pacific/Honolulu" to "하와이",
        "America/Los_Angeles" to "미국 서부",
        "America/New_York" to "미국 동부",
        "Europe/London" to "영국",
        "Europe/Paris" to "프랑스 · 독일 · 이탈리아 · 스페인",
        "Europe/Istanbul" to "튀르키예",
        "Asia/Dubai" to "두바이"
    )

    /** 여행 이름의 나라 · 도시 낱말 → (시간대, 보일 이름). 먼저 맞는 것 (도시를 나라보다 앞에) */
    private val KEYWORDS: List<Triple<List<String>, String, String>> = listOf(
        Triple(listOf("서울", "부산", "제주", "강릉", "경주", "여수", "전주", "대구", "인천", "속초", "대전", "광주", "통영", "포항", "한국", "국내"), "Asia/Seoul", "한국"),
        Triple(listOf("도쿄", "동경", "오사카", "교토", "후쿠오카", "삿포로", "홋카이도", "오키나와", "나고야", "고베", "하코네", "요코하마", "일본"), "Asia/Tokyo", "일본"),
        Triple(listOf("타이베이", "대만", "타이완", "가오슝", "타이중", "타이난"), "Asia/Taipei", "대만"),
        Triple(listOf("홍콩"), "Asia/Hong_Kong", "홍콩"),
        Triple(listOf("마카오"), "Asia/Macau", "마카오"),
        Triple(listOf("상하이", "베이징", "칭다오", "청두", "중국"), "Asia/Shanghai", "중국"),
        Triple(listOf("싱가포르"), "Asia/Singapore", "싱가포르"),
        Triple(listOf("쿠알라룸푸르", "코타키나발루", "말레이시아"), "Asia/Kuala_Lumpur", "말레이시아"),
        Triple(listOf("세부섬", "막탄", "보라카이", "마닐라", "보홀", "필리핀"), "Asia/Manila", "필리핀"),
        Triple(listOf("방콕", "치앙마이", "푸켓", "파타야", "태국"), "Asia/Bangkok", "태국"),
        Triple(listOf("다낭", "하노이", "호치민", "호찌민", "나트랑", "냐짱", "푸꾸옥", "베트남"), "Asia/Ho_Chi_Minh", "베트남"),
        Triple(listOf("발리"), "Asia/Makassar", "인도네시아 발리"),
        Triple(listOf("자카르타", "인도네시아"), "Asia/Jakarta", "인도네시아"),
        Triple(listOf("울란바토르", "몽골"), "Asia/Ulaanbaatar", "몽골"),
        Triple(listOf("두바이", "아부다비"), "Asia/Dubai", "아랍에미리트"),
        Triple(listOf("괌"), "Pacific/Guam", "괌"),
        Triple(listOf("사이판"), "Pacific/Saipan", "사이판"),
        Triple(listOf("시드니", "멜버른", "호주"), "Australia/Sydney", "호주"),
        Triple(listOf("오클랜드", "퀸스타운", "뉴질랜드"), "Pacific/Auckland", "뉴질랜드"),
        Triple(listOf("하와이", "호놀룰루"), "Pacific/Honolulu", "하와이"),
        Triple(listOf("로스앤젤레스", "LA", "샌프란시스코", "라스베이거스", "시애틀", "미국 서부"), "America/Los_Angeles", "미국 서부"),
        Triple(listOf("뉴욕", "보스턴", "워싱턴", "마이애미", "미국 동부"), "America/New_York", "미국 동부"),
        Triple(listOf("밴쿠버"), "America/Vancouver", "캐나다 밴쿠버"),
        Triple(listOf("토론토"), "America/Toronto", "캐나다 토론토"),
        Triple(listOf("런던", "영국"), "Europe/London", "영국"),
        Triple(listOf("파리", "프랑스"), "Europe/Paris", "프랑스"),
        Triple(listOf("로마", "밀라노", "베네치아", "피렌체", "이탈리아"), "Europe/Rome", "이탈리아"),
        Triple(listOf("바르셀로나", "마드리드", "스페인"), "Europe/Madrid", "스페인"),
        Triple(listOf("베를린", "뮌헨", "프랑크푸르트", "독일"), "Europe/Berlin", "독일"),
        Triple(listOf("프라하", "체코"), "Europe/Prague", "체코"),
        Triple(listOf("비엔나", "잘츠부르크", "오스트리아"), "Europe/Vienna", "오스트리아"),
        Triple(listOf("취리히", "인터라켄", "스위스"), "Europe/Zurich", "스위스"),
        Triple(listOf("암스테르담", "네덜란드"), "Europe/Amsterdam", "네덜란드"),
        Triple(listOf("이스탄불", "튀르키예", "터키"), "Europe/Istanbul", "튀르키예")
    )

    /** 여행 이름으로 시간대 추천 (모르면 null). 예) "도쿄 3박 4일" → Asia/Tokyo · 일본 */
    fun detect(title: String): Pair<String, String>? {
        val t = title.trim()
        if (t.isEmpty()) return null
        for ((words, id, name) in KEYWORDS) if (words.any { w ->
                // 영문 약어(LA)는 낱말로만, 한글은 포함이면 맞음
                if (w.all { it.isLetter() && it.code < 128 }) Regex("\\b$w\\b").containsMatchIn(t) else t.contains(w) }) return id to name
        return null
    }

    /** "UTC+9" (써머타임이면 지금 기준) */
    fun offset(id: String): String {
        val secs = runCatching { ZoneId.of(id).rules.getOffset(Instant.now()).totalSeconds }.getOrDefault(0)
        val h = secs / 3600; val m = Math.abs(secs % 3600) / 60
        return "UTC" + (if (secs >= 0) "+" else "-") + Math.abs(h) + (if (m != 0) ":%02d".format(m) else "")
    }

    /** 시간대 이름: 목록에 있으면 그 이름(한국 · 일본 …), 없으면 대표 도시 이름(예: 런던), 그것도 없으면 IANA 이름 */
    fun placeName(id: String): String =
        LIST.firstOrNull { it.first == id }?.second
            ?: runCatching { android.icu.text.TimeZoneNames.getInstance(java.util.Locale.KOREAN).getExemplarLocationName(id) }.getOrNull()
            ?: if (id == "UTC" || id.startsWith("Etc/") || id == "GMT") "세계 표준시" else id.substringAfterLast('/').replace('_', ' ')

    /**
     * "일본 · UTC+9". 기본값(빈 글자)은 자동 설정: 여행 이름으로 추천한 곳, 없으면 휴대폰 시간대
     * → "자동 설정 (일본 · UTC+9)"
     */
    fun label(ctx: Context, id: String, title: String = ""): String =
        if (id.isBlank()) {
            val (zid, name) = detect(title) ?: ZoneId.systemDefault().id.let { it to placeName(it) }
            ctx.getString(R.string.zone_auto, name + " · " + offset(zid))
        } else placeName(id) + " · " + offset(id)

    /** 실제로 쓸 시간대: 정한 것 → 여행 이름 추천 → 휴대폰 */
    fun effective(id: String, title: String): ZoneId =
        id.takeIf { it.isNotBlank() }?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: detect(title)?.let { runCatching { ZoneId.of(it.first) }.getOrNull() }
            ?: ZoneId.systemDefault()
}
