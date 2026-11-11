package com.tripplanner.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import com.tripplanner.R

/** 표지 위치 조정: 틀을 위아래로 끌고 확대 → 완료하면 (위치, 확대)를 돌려줌 */
class CoverCropActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_TITLE = "title"
        const val EXTRA_Y = "y"
        const val EXTRA_SCALE = "scale"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cover_crop)
        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        val y = savedInstanceState?.getFloat(EXTRA_Y) ?: intent.getFloatExtra(EXTRA_Y, 0.5f)
        val scale = savedInstanceState?.getFloat(EXTRA_SCALE) ?: intent.getFloatExtra(EXTRA_SCALE, 1f)

        val crop = findViewById<CoverCropView>(R.id.cropView)
        val preview = findViewById<ImageView>(R.id.ivPreview)
        val slider = findViewById<Slider>(R.id.sliderScale)
        findViewById<TextView>(R.id.tvPreviewTitle).text = intent.getStringExtra(EXTRA_TITLE)

        // 미리보기 칸과 같은 비율로 틀을 그림
        preview.post { if (preview.height > 0) { crop.aspect = preview.width.toFloat() / preview.height; crop.invalidate() } }
        crop.bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 })
        crop.onChange = { fy, s -> CoverPhoto.bind(preview, path, fy, s) }
        crop.set(y, scale)
        slider.value = crop.scale
        slider.addOnChangeListener { _, v, fromUser -> if (fromUser) crop.set(crop.focusY, v) }

        findViewById<android.view.View>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.btnDone).setOnClickListener {
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_Y, crop.focusY).putExtra(EXTRA_SCALE, crop.scale))
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val crop = findViewById<CoverCropView>(R.id.cropView)
        outState.putFloat(EXTRA_Y, crop.focusY); outState.putFloat(EXTRA_SCALE, crop.scale)
    }
}
