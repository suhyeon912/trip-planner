package com.tripplanner.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeUtilTest {
    @Test fun displayRange_normalizesLooseRanges() {
        assertEquals("11:00 ~ 13:00", TimeUtil.displayRange("11 13"))
        assertEquals("11:00 ~ 13:00", TimeUtil.displayRange("11-13"))
        assertEquals("11:00 ~ 13:30", TimeUtil.displayRange("11시~13:30"))
        assertEquals("11:00 ~ 13:00", TimeUtil.displayRange("1100 1300"))
        assertEquals("15:00 ~ 17:00", TimeUtil.displayRange("15:00-17:00"))
        assertEquals("상점마다 다름", TimeUtil.displayRange("상점마다 다름"))
    }

    @Test fun parseRange_readsLooseRanges() {
        assertEquals(660..780, TimeUtil.parseRange("11 13"))
        assertNull(TimeUtil.parseRange("상시"))
    }
}
