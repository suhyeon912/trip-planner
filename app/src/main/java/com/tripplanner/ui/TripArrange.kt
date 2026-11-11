package com.tripplanner.ui

import android.content.Context
import com.tripplanner.data.TripWithCounts
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

/** 메인 탭: 전체 / 내 여행(내가 만든) / 초대받은 */
enum class TripFilter { ALL, MINE, INVITED }

/** 메인 정렬 기준 (보기 설정) */
enum class TripSort { NAME, RECENT, DATE }

/** 메인 목록 한 줄: 여행 카드 / '지난 여행 n' 제목 / 지난 여행 작은 줄 */
sealed class TripRow {
    data class Card(val item: TripWithCounts) : TripRow()
    data class PastHeader(val count: Int) : TripRow()
    data class Past(val item: TripWithCounts) : TripRow()
}

/**
 * 메인 목록 배치 (안드로이드와 무관 → 단위 테스트).
 * 다가오는·진행 중 여행은 정렬 기준대로 카드, 지난 여행은 아래에 최근 것부터 작은 줄.
 */
object TripArrange {
    private val collator: Collator = Collator.getInstance(Locale.KOREAN)

    fun rows(list: List<TripWithCounts>, sort: TripSort, showPast: Boolean, today: LocalDate = LocalDate.now(),
             filter: TripFilter = TripFilter.ALL): List<TripRow> {
        val shown = list.filter { matches(it, filter) }
        val (past, active) = shown.partition { TripFormat.dday(it.trip, today)?.phase == TripFormat.Phase.PAST }
        val sorted = when (sort) {
            TripSort.NAME -> active.sortedWith { a, b -> collator.compare(a.trip.title, b.trip.title) }
            TripSort.RECENT -> active.sortedByDescending { it.trip.id }
            TripSort.DATE -> active.sortedBy { it.trip.startDate }
        }
        val rows = sorted.map { TripRow.Card(it) }.toMutableList<TripRow>()
        if (showPast && past.isNotEmpty()) {
            rows += TripRow.PastHeader(past.size)
            rows += past.sortedByDescending { it.trip.startDate }.map { TripRow.Past(it) }
        }
        return rows
    }

    fun matches(item: TripWithCounts, filter: TripFilter) = when (filter) {
        TripFilter.ALL -> true
        TripFilter.MINE -> item.trip.ownedByMe
        TripFilter.INVITED -> !item.trip.ownedByMe
    }
}

/** 보기 설정 저장 (기기에만, 화면 표시용) */
class TripViewPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("main_view", Context.MODE_PRIVATE)
    var sort: TripSort
        get() = runCatching { TripSort.valueOf(prefs.getString("sort", null) ?: "DATE") }.getOrDefault(TripSort.DATE)
        set(v) = prefs.edit().putString("sort", v.name).apply()
    var showPast: Boolean
        get() = prefs.getBoolean("show_past", true)
        set(v) = prefs.edit().putBoolean("show_past", v).apply()
}
