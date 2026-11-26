package com.tripplanner.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareMergeTest {
    private fun place(sid: String, name: String, memo: String = "", day: Int? = null) =
        mapOf("sid" to sid, "name" to name, "memo" to memo, "day" to day, "stay" to 60)
    private fun doc(vararg places: Map<String, Any?>, title: String = "도쿄", note: String = "") =
        mapOf("title" to title, "note" to note, "places" to places.toList(), "flights" to emptyList<Any>(), "lodgings" to emptyList<Any>())
    @Suppress("UNCHECKED_CAST")
    private fun places(m: Map<String, Any?>) = (m["places"] as List<Map<String, Any?>>).associateBy { it["name"] }

    @Test fun eachSideEditsDifferentPlace_bothKept() {
        val base = doc(place("a", "센소지"), place("b", "스카이트리"))
        val local = doc(place("a", "센소지", memo = "아침 일찍"), place("b", "스카이트리"))
        val server = doc(place("a", "센소지"), place("b", "스카이트리", day = 2))
        val m = places(ShareMerge.merge(base, local, server))
        assertEquals("아침 일찍", m["센소지"]!!["memo"])
        assertEquals(2, m["스카이트리"]!!["day"])
    }

    @Test fun addsFromBothSides_andServerLongNumbers() {
        val base = doc(place("a", "센소지"))
        val local = doc(place("a", "센소지"), place("c", "긴자"))
        val server = mapOf("title" to "도쿄", "note" to "", "flights" to emptyList<Any>(), "lodgings" to emptyList<Any>(),
            "places" to listOf(mapOf("sid" to "a", "name" to "센소지", "memo" to "", "stay" to 60L), place("d", "우에노")))
        val m = places(ShareMerge.merge(base, local, server))
        assertEquals(setOf("센소지", "긴자", "우에노"), m.keys)
    }

    @Test fun deleteWins_onlyWhenOtherSideUnchanged() {
        val base = doc(place("a", "센소지"), place("b", "스카이트리"))
        val local = doc(place("b", "스카이트리"))                                   // 내가 센소지 지움
        val server = doc(place("a", "센소지"), place("b", "스카이트리", memo = "x")) // 서버는 스카이트리만 고침
        val m = places(ShareMerge.merge(base, local, server))
        assertEquals(setOf("스카이트리"), m.keys)
        assertEquals("x", m["스카이트리"]!!["memo"])
        // 서버가 지웠지만 내가 고친 장소는 남음
        val m2 = places(ShareMerge.merge(base, doc(place("a", "센소지", memo = "고침"), place("b", "스카이트리")), doc(place("b", "스카이트리"))))
        assertTrue("센소지" in m2.keys)
    }

    @Test fun tripFields_threeWay_and_oldDocsWithoutSid() {
        val base = doc(place("a", "센소지") - "sid", title = "도쿄", note = "")
        val local = doc(place("a", "센소지"), title = "도쿄 4일", note = "")
        val server = doc(place("a", "센소지", memo = "서버") - "sid", title = "도쿄", note = "메모")
        val out = ShareMerge.merge(base, local, server)
        assertEquals("도쿄 4일", out["title"]); assertEquals("메모", out["note"])
        val m = places(out)
        assertEquals(1, m.size)                       // 이름으로 짝지어 하나로
        assertEquals("서버", m["센소지"]!!["memo"])
        assertEquals("a", m["센소지"]!!["sid"])       // 내 sid 를 붙여 둠
    }
}
