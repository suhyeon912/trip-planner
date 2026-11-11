package com.tripplanner.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.tripplanner.data.PlaceCategory

/** 앱 설정 (기기에만 저장): 테마 · 숨긴 장소 종류 */
class AppPrefs(context: Context) {
    enum class Theme(val mode: Int) {
        SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES)
    }

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var theme: Theme
        get() = runCatching { Theme.valueOf(prefs.getString("theme", null) ?: "SYSTEM") }.getOrDefault(Theme.SYSTEM)
        set(v) { prefs.edit().putString("theme", v.name).apply(); AppCompatDelegate.setDefaultNightMode(v.mode) }

    /** 장소 추가·수정에서 숨긴 기본 종류 (저장된 장소의 종류는 그대로) */
    var hiddenCategories: Set<PlaceCategory>
        get() = prefs.getStringSet("hidden_categories", emptySet()).orEmpty()
            .mapNotNull { n -> PlaceCategory.entries.firstOrNull { it.name == n } }.toSet()
        set(v) = prefs.edit().putStringSet("hidden_categories", v.map { it.name }.toSet()).apply()

    companion object {
        /** 앱 시작 시 저장된 테마 적용 */
        fun applyTheme(context: Context) = AppCompatDelegate.setDefaultNightMode(AppPrefs(context).theme.mode)

        /**
         * 장소 추가·수정 칩에 보일 종류: 숨기지 않은 것 + (수정 중인 장소의 종류는 숨겼어도 보임).
         * 모두 숨기면 빈 목록 → 칩 줄을 숨기고 '기타'로 저장 (안드로이드와 무관 → 단위 테스트)
         */
        fun visibleCategories(hidden: Set<PlaceCategory>, current: PlaceCategory?): List<PlaceCategory> =
            PlaceCategory.entries.filter { it !in hidden || it == current }
    }
}
