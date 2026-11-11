package com.tripplanner.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import com.tripplanner.R
import com.tripplanner.data.PlaceCategory
import com.tripplanner.data.TransportMode

/*
 * 화면에서 쓰는 아이콘·색 짝 (데이터 → 모양).
 * 데이터(enum)는 data 패키지에, 어떻게 보일지는 여기 한 곳에 모아 둡니다.
 */

/** 장소 종류 아이콘 */
val PlaceCategory.icon: Int get() = when (this) {
    PlaceCategory.SIGHT -> R.drawable.ic_sight
    PlaceCategory.FOOD -> R.drawable.ic_food
    PlaceCategory.CAFE -> R.drawable.ic_cafe
    PlaceCategory.SHOPPING -> R.drawable.ic_shopping
    PlaceCategory.ACTIVITY -> R.drawable.ic_activity
    PlaceCategory.ETC -> R.drawable.ic_place
}

/** 장소 종류 색 (아이콘 배경 / 글자) — 라이트·다크 모두 colors.xml 에서 정의 */
val PlaceCategory.bgColor: Int get() = when (this) {
    PlaceCategory.SIGHT -> R.color.cat_sight_bg
    PlaceCategory.FOOD -> R.color.cat_food_bg
    PlaceCategory.CAFE -> R.color.cat_cafe_bg
    PlaceCategory.SHOPPING -> R.color.cat_shopping_bg
    PlaceCategory.ACTIVITY -> R.color.cat_activity_bg
    PlaceCategory.ETC -> R.color.surface_container
}
val PlaceCategory.fgColor: Int get() = when (this) {
    PlaceCategory.SIGHT -> R.color.cat_sight_fg
    PlaceCategory.FOOD -> R.color.cat_food_fg
    PlaceCategory.CAFE -> R.color.cat_cafe_fg
    PlaceCategory.SHOPPING -> R.color.cat_shopping_fg
    PlaceCategory.ACTIVITY -> R.color.cat_activity_fg
    PlaceCategory.ETC -> R.color.text_sub
}

/** 교통 종류 아이콘 */
val TransportMode.icon: Int get() = when (this) {
    TransportMode.FLIGHT -> R.drawable.ic_flight
    TransportMode.TRAIN -> R.drawable.ic_train
    TransportMode.BUS -> R.drawable.ic_bus
    TransportMode.SHIP -> R.drawable.ic_ship
    TransportMode.CAR -> R.drawable.ic_car
    TransportMode.ETC -> R.drawable.ic_route
}

object Covers {
    /** 여행 카드 커버 그라데이션 (진한 색 → 밝은 색). 흰 글자는 진한 쪽(왼쪽 아래)에 놓임 */
    private val PAIRS = arrayOf(
        0xFF1D4ED8.toInt() to 0xFF60A5FA.toInt(),   // 파랑
        0xFF0E7490.toInt() to 0xFF2DD4BF.toInt(),   // 청록
        0xFF6D28D9.toInt() to 0xFFA78BFA.toInt(),   // 보라
        0xFFC2410C.toInt() to 0xFFFB923C.toInt(),   // 주황
        0xFF047857.toInt() to 0xFF34D399.toInt(),   // 초록
        0xFFBE185D.toInt() to 0xFFF472B6.toInt(),   // 분홍
    )
    private val PAST = 0xFF4B5563.toInt() to 0xFF9CA3AF.toInt()

    fun gradient(context: Context, key: Long, past: Boolean): GradientDrawable {
        val (dark, light) = if (past) PAST else PAIRS[(key % PAIRS.size).toInt().let { if (it < 0) -it else it }]
        val r = 20 * context.resources.displayMetrics.density   // 카드 모서리와 같게 (위쪽만 둥글게)
        return GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(dark, light)).apply {
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
    }
}

/** 장소 썸네일: 사진 또는 종류 아이콘 (상세 목록·지도 카드·편집 화면 공용) */
object Thumbs {
    fun bind(v: android.widget.ImageView, photoPath: String?, category: PlaceCategory) {
        val ctx = v.context
        val d = ctx.resources.displayMetrics.density
        val photo = photoPath?.let { java.io.File(it) }?.takeIf { it.exists() }
        val bg = GradientDrawable().apply {
            cornerRadius = 12 * d
            setColor(ctx.color(if (photo != null) R.color.surface_container else category.bgColor))
        }
        v.background = bg
        v.clipToOutline = true
        if (photo != null) {
            v.setPadding(0, 0, 0, 0)
            v.imageTintList = null
            v.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            v.setImageURI(null)
            v.setImageURI(android.net.Uri.fromFile(photo))
        } else {
            val pad = (10 * d).toInt()
            v.setPadding(pad, pad, pad, pad)
            v.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            v.setImageResource(category.icon)
            v.imageTintList = android.content.res.ColorStateList.valueOf(ctx.color(category.fgColor))
        }
    }
}

internal fun Context.color(res: Int) = ContextCompat.getColor(this, res)
