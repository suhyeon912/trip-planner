package com.tripplanner.ui

import com.tripplanner.data.PlaceCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPrefsTest {
    @Test fun hiddenCategoriesAreFilteredButCurrentStays() {
        val hidden = setOf(PlaceCategory.SHOPPING, PlaceCategory.CAFE)
        assertEquals(listOf(PlaceCategory.SIGHT, PlaceCategory.FOOD, PlaceCategory.ACTIVITY, PlaceCategory.ETC),
            AppPrefs.visibleCategories(hidden, null))
        assertEquals(listOf(PlaceCategory.SIGHT, PlaceCategory.FOOD, PlaceCategory.CAFE, PlaceCategory.ACTIVITY, PlaceCategory.ETC),
            AppPrefs.visibleCategories(hidden, PlaceCategory.CAFE))
        assertEquals(emptyList<PlaceCategory>(), AppPrefs.visibleCategories(PlaceCategory.entries.toSet(), null))
    }
}
