package com.tripplanner.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.tripplanner.R

/**
 * 표지 위치 조정 칸: 사진 전체를 보여 주고, 표지로 보일 틀(3등분 선)을 위아래로 끌어 옮김.
 * 틀 밖은 어둡게. 확대는 setScale 로 (틀이 작아짐).
 */
class CoverCropView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var bitmap: Bitmap? = null
        set(v) { field = v; invalidate() }
    /** 표지 칸 가로 : 세로 */
    var aspect = 343f / 116f
    var focusY = 0.5f
        private set
    var scale = 1f
        private set
    var onChange: ((Float, Float) -> Unit)? = null

    private val dim = Paint().apply { color = 0x99000000.toInt() }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = ContextCompat.getColor(context, R.color.white)
        strokeWidth = 2 * resources.displayMetrics.density
    }
    private val grid = Paint(frame).apply { strokeWidth = 1 * resources.displayMetrics.density; alpha = 160 }
    private val img = RectF()
    private val box = RectF()
    private var lastY = 0f

    fun set(y: Float, s: Float) { focusY = y.coerceIn(0f, 1f); scale = s.coerceIn(1f, 3f); invalidate(); onChange?.invoke(focusY, scale) }

    override fun onDraw(c: Canvas) {
        val b = bitmap ?: return
        // 사진을 칸 안에 맞춤 (가로 기준, 세로가 넘치면 세로 기준)
        val fit = minOf(width.toFloat() / b.width, height.toFloat() / b.height)
        val w = b.width * fit; val h = b.height * fit
        img.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        c.drawBitmap(b, null, img, null)
        // 표지 틀 (표지 계산과 같은 규칙: 가로는 가운데, 세로는 focusY)
        val coverW = 1000; val coverH = (1000 / aspect).toInt()
        val (top, bottom) = CoverPhoto.visibleRange(b.width, b.height, coverW, coverH, focusY, scale)
        val l = CoverPhoto.layout(b.width, b.height, coverW, coverH, focusY, scale)
        val visW = coverW / (b.width * l.scale)
        box.set(img.left + img.width() * (1 - visW) / 2, img.top + img.height() * top,
            img.left + img.width() * (1 + visW) / 2, img.top + img.height() * bottom)
        c.drawRect(img.left, img.top, img.right, box.top, dim)
        c.drawRect(img.left, box.bottom, img.right, img.bottom, dim)
        c.drawRect(img.left, box.top, box.left, box.bottom, dim)
        c.drawRect(box.right, box.top, img.right, box.bottom, dim)
        for (i in 1..2) {
            val x = box.left + box.width() * i / 3
            c.drawLine(x, box.top, x, box.bottom, grid)
        }
        val r = 12 * resources.displayMetrics.density
        c.drawRoundRect(box, r, r, frame)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val b = bitmap ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastY = e.y; parent?.requestDisallowInterceptTouchEvent(true); return true }
            MotionEvent.ACTION_MOVE -> {
                val dy = (e.y - lastY) / img.height().coerceAtLeast(1f)
                lastY = e.y
                // 끝까지 가면 더 안 움직이도록 보이는 범위 기준으로 맞춤
                val (top, bottom) = CoverPhoto.visibleRange(b.width, b.height, 1000, (1000 / aspect).toInt(), focusY + dy, scale)
                set(((top + bottom) / 2), scale)
                return true
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
