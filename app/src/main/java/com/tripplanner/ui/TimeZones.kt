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

    /** "UTC+9" (써머타임이면 지금 기준) */
    fun offset(id: String): String {
        val secs = runCatching { ZoneId.of(id).rules.getOffset(Instant.now()).totalSeconds }.getOrDefault(0)
        val h = secs / 3600; val m = Math.abs(secs % 3600) / 60
        return "UTC" + (if (secs >= 0) "+" else "-") + Math.abs(h) + (if (m != 0) ":%02d".format(m) else "")
    }

    fun label(ctx: Context, id: String): String =
        if (id.isBlank()) ctx.getString(R.string.zone_device, offset(ZoneId.systemDefault().id))
        else (LIST.firstOrNull { it.first == id }?.second ?: id) + " · " + offset(id)
}
