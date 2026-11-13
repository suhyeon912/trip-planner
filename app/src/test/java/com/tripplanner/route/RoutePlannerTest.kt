package com.tripplanner.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RoutePlannerTest {

    private val hotel = Anchor("호텔", 34.6660, 135.5010)

    // 오사카 예시 1일차: 오사카성 · 도톤보리 · 라멘집(브레이크 15:00~17:00)
    private val castle = Stop(1, "오사카성", 34.6873, 135.5262, open = 540..1020, stayMinutes = 120)
    private val dotonbori = Stop(2, "도톤보리", 34.6687, 135.5013, stayMinutes = 90)
    private val ramen = Stop(3, "라멘집", 34.6700, 135.5000, open = 660..1260, breakTime = 900..1020, stayMinutes = 60)

    @Test fun parseRange() {
        assertEquals(660..1260, TimeUtil.parseRange("11:00 ~ 21:00"))
        assertEquals(960..1440, TimeUtil.parseRange("16:00 ~ 24:00"))
        assertEquals(1080..1560, TimeUtil.parseRange("18:00 ~ 02:00"))
        assertNull(TimeUtil.parseRange("상시"))
        assertNull(TimeUtil.parseRange(null))
    }

    @Test fun closedDay() {
        val tue = LocalDate.of(2026, 12, 22)   // 화요일
        assertTrue(TimeUtil.isClosedOn("화요일", tue))
        assertFalse(TimeUtil.isClosedOn("월요일", tue))
        assertFalse(TimeUtil.isClosedOn("일부 가게 화요일", tue))
        assertEquals(4, TimeUtil.dayCount("2026-12-20", "2026-12-23"))
    }

    @Test fun shortestIsReallyShortest() {
        val stops = listOf(castle, dotonbori, ramen,
            Stop(4, "우메다", 34.7053, 135.4896), Stop(5, "신사이바시", 34.6748, 135.5012))
        val best = RoutePlanner.shortestOrder(hotel, stops)
        val bestKm = RoutePlanner.totalKm(hotel, best)
        RoutePlanner.permutations(stops).forEach {
            assertTrue(bestKm <= RoutePlanner.totalKm(hotel, it) + 1e-9)
        }
    }

    @Test fun optionBAvoidsBreakTime() {
        val options = RoutePlanner.plan(hotel, listOf(castle, dotonbori, ramen), emptyList())
        val b = options.first { it.key == "B" }
        assertTrue("B안은 경고가 없어야 함: ${b.warnings}", b.warnings.isEmpty())
        val a = options.first { it.key == "A" }
        assertTrue(a.totalKm <= b.totalKm + 1e-9)
        // 모든 장소를 한 번씩 방문
        assertEquals(setOf(1L, 2L, 3L), b.visits.map { it.stop.id }.toSet())
    }

    @Test fun waitsUntilOpening() {
        val night = Stop(9, "야시장", 34.6687, 135.5013, open = 960..1440, stayMinutes = 60)
        val sim = RoutePlanner.simulate(hotel, listOf(night), 600)
        assertEquals(960, sim.visits.first().arrive)
        assertNull(sim.visits.first().warning)
    }

    @Test fun optionCAddsOnlyNearbyCandidates() {
        val near = Stop(20, "신사이바시", 34.6748, 135.5012, isCandidate = true)
        val far = Stop(21, "USJ", 34.6654, 135.4323, isCandidate = true)
        val options = RoutePlanner.plan(hotel, listOf(castle, dotonbori), listOf(near, far))
        val c = options.first { it.key == "C" }
        assertEquals(listOf(20L), c.added.map { it.id })
        assertEquals(3, c.visits.size)
    }

    @Test fun noCandidateNoOptionC() {
        val options = RoutePlanner.plan(hotel, listOf(castle), emptyList())
        assertEquals(listOf("A", "B"), options.map { it.key })
    }

    @Test fun manyStopsUsesHeuristic() {
        val stops = (1..10).map { Stop(it.toLong(), "p$it", 34.66 + it * 0.003, 135.50 + (it % 3) * 0.004) }
        val order = RoutePlanner.shortestOrder(hotel, stops)
        assertEquals(10, order.toSet().size)
    }

    @Test fun optionDKeepsGivenOrder_onlyWhenDifferent() {
        // 숙소와 세 곳이 네모 꼭짓점 → 둘레를 도는 순서가 최단, 대각선으로 오가는 순서는 더 김
        val home = Anchor("숙소", 35.0, 135.0)
        val a = Stop(1, "a", 35.01, 135.0)
        val b = Stop(2, "b", 35.01, 135.02)
        val c = Stop(3, "c", 35.0, 135.02)
        val given = listOf(b, a, c)
        val d = RoutePlanner.plan(home, given, emptyList()).single { it.key == "D" }
        assertEquals(listOf(2L, 1L, 3L), d.visits.map { it.stop.id })
        // 이미 최단(A) 순서면 D 는 없음
        val orderA = RoutePlanner.plan(home, given, emptyList()).first { it.key == "A" }.visits.map { it.stop }
        assertTrue(RoutePlanner.plan(home, orderA, emptyList()).none { it.key == "D" })
    }

    @Test
    fun endLimit_warnsWhenLate() {
        val home = Anchor("숙소", 35.0, 135.0)
        val far = Stop(1, "먼 곳", 35.2, 135.0, stayMinutes = 600)
        val late = RoutePlanner.plan(home, listOf(far), emptyList(), startTime = 600, endLimit = 18 * 60).first()
        assertTrue(late.lateMinutes > 0)
        assertTrue(late.warnings.any { it.startsWith("숙소 도착") })
        val free = RoutePlanner.plan(home, listOf(far), emptyList(), startTime = 600).first()
        assertEquals(0, free.lateMinutes)
        assertTrue(free.warnings.isEmpty())
    }

    @Test fun legModes_minutesAndCustomLegTime() {
        assertEquals(LegMode.WALK, RoutePlanner.defaultMode(1.0))
        assertEquals(LegMode.TRANSIT, RoutePlanner.defaultMode(2.6))
        assertEquals(19, RoutePlanner.modeMinutes(LegMode.WALK, 1.4))      // 1.4km 도보 ≈ 19분
        assertEquals(12, RoutePlanner.modeMinutes(LegMode.TRANSIT, 2.6))   // 5 + 7
        assertEquals(10, RoutePlanner.modeMinutes(LegMode.TAXI, 2.6))      // 3 + 7
        assertEquals(0, RoutePlanner.modeMinutes(LegMode.WALK, 0.01))
        // 구간마다 다른 시간: 숙소 → 첫 장소만 30분, 나머지 0분
        val hotel = Anchor("호텔", 35.0, 135.0)
        val a = Stop(1, "A", 35.01, 135.0, stayMinutes = 60)
        val sim = RoutePlanner.simulate(hotel, listOf(a), 600) { from, to, _ -> if (from == RoutePlanner.ANCHOR_ID && to == 1L) 30 else 0 }
        assertEquals(630, sim.visits.single().arrive)
        assertEquals(690, sim.endTime)
    }
}
