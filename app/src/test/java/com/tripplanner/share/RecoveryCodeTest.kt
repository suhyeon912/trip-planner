package com.tripplanner.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodeTest {
    @Test fun newCode_isValidAndRandom() {
        val a = RecoveryCode.newCode(); val b = RecoveryCode.newCode()
        assertEquals(12, a.length)
        assertEquals(a, RecoveryCode.normalize(a))
        assertNotEquals(a, b)
    }

    @Test fun normalize_ignoresCaseDashesSpaces() {
        assertEquals("ABCDEFGHJKLM", RecoveryCode.normalize("abcd-efgh jklm"))
        assertEquals("ABCD-EFGH-JKLM", RecoveryCode.display("ABCDEFGHJKLM"))
        assertNull(RecoveryCode.normalize("ABCD-EFGH"))          // 짧음
        assertNull(RecoveryCode.normalize("ABCD-EFGH-JKL0"))     // 쓰지 않는 글자 0
    }

    @Test fun email_isStableAndNotTheCode() {
        val e = RecoveryCode.email("ABCDEFGHJKLM")
        assertEquals(e, RecoveryCode.email("ABCDEFGHJKLM"))
        assertNotEquals(e, RecoveryCode.email("ABCDEFGHJKLN"))
        assertTrue(e.endsWith("@restore.tripplanner.app") && !e.contains("ABCD"))
        assertTrue(RecoveryCode.password("ABCDEFGHJKLM").length >= 6)
    }
}
