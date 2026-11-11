package com.tripplanner.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverPhotoTest {
    // 600×930 세로 사진을 343×116 표지에
    @Test fun fillsWidth_andCentersFocus() {
        val l = CoverPhoto.layout(600, 930, 343, 116, y = 0.5f, scale = 1f)
        assertEquals(343f / 600f, l.scale, 1e-4f)
        assertEquals(0f, l.dx, 1e-3f)
        val (top, bottom) = CoverPhoto.visibleRange(600, 930, 343, 116, 0.5f, 1f)
        assertEquals(0.5f, (top + bottom) / 2, 1e-3f)
    }

    @Test fun clampsAtEdges_andZoomNarrowsRange() {
        val (top, _) = CoverPhoto.visibleRange(600, 930, 343, 116, y = 0f, scale = 1f)
        assertEquals(0f, top, 1e-4f)                                   // 맨 위를 넘지 않음
        val (_, bottom) = CoverPhoto.visibleRange(600, 930, 343, 116, y = 1f, scale = 1f)
        assertEquals(1f, bottom, 1e-4f)                                // 맨 아래를 넘지 않음
        val r1 = CoverPhoto.visibleRange(600, 930, 343, 116, 0.5f, 1f)
        val r2 = CoverPhoto.visibleRange(600, 930, 343, 116, 0.5f, 2f)
        assertEquals((r1.second - r1.first) / 2, r2.second - r2.first, 1e-4f)   // 2배 확대 → 보이는 높이 절반
    }
}
