package com.tripplanner.ui

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.widget.ImageView
import java.io.File

/**
 * 여행 표지 사진 배치. 표지 칸(W×H)을 꽉 채우고(centerCrop), 확대(scale)한 뒤
 * 사진 높이의 y(0~1) 지점이 칸 가운데 오도록 세로 위치를 맞춤 (가로는 가운데).
 */
object CoverPhoto {
    data class Layout(val scale: Float, val dx: Float, val dy: Float)

    /** 안드로이드와 무관한 계산 (단위 테스트) */
    fun layout(iw: Int, ih: Int, w: Int, h: Int, y: Float, scale: Float): Layout {
        val s = maxOf(w.toFloat() / iw, h.toFloat() / ih) * scale.coerceIn(1f, 3f)
        val dw = iw * s; val dh = ih * s
        val dx = (w - dw) / 2f
        val dy = (h / 2f - y.coerceIn(0f, 1f) * dh).coerceIn(h - dh, 0f)
        return Layout(s, dx, dy)
    }

    /** 칸 높이 중 보이는 범위를 사진 높이 비율로 (자르기 화면 틀 그리기용): (위, 아래) */
    fun visibleRange(iw: Int, ih: Int, w: Int, h: Int, y: Float, scale: Float): Pair<Float, Float> {
        val l = layout(iw, ih, w, h, y, scale)
        val dh = ih * l.scale
        return (-l.dy / dh) to ((h - l.dy) / dh)
    }

    /** ImageView 에 사진을 표지 배치로 (칸 크기가 정해진 뒤 적용) */
    fun bind(v: ImageView, path: String, y: Float, scale: Float) {
        val file = File(path)
        if (!file.exists()) { v.setImageDrawable(null); return }
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bmp = BitmapFactory.decodeFile(path, opts) ?: return
        v.scaleType = ImageView.ScaleType.MATRIX
        v.setImageBitmap(bmp)
        fun apply() {
            if (v.width == 0 || v.height == 0) return
            val l = layout(bmp.width, bmp.height, v.width, v.height, y, scale)
            v.imageMatrix = Matrix().apply { setScale(l.scale, l.scale); postTranslate(l.dx, l.dy) }
        }
        if (v.width > 0) apply() else v.post { apply() }
        v.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> apply() }
    }
}
