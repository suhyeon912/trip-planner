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

/**
 * 장소 종류 보이기 (기본 종류 또는 내가 만든 종류).
 * 내가 만든 종류는 앱이 DB 를 지켜보며 types 를 늘 최신으로 맞춤 (TripPlannerApp).
 */
object PlaceKinds {
    /** 바탕색 8가지 (라이트·다크 모두 글자·아이콘과 대비가 맞는 색) */
    val COLORS = intArrayOf(R.color.cat_sight_bg, R.color.cat_food_bg, R.color.cat_cafe_bg, R.color.cat_shopping_bg,
        R.color.cat_activity_bg, R.color.tertiary_container, R.color.cat_yellow_bg, R.color.surface_container)
    fun colorRes(index: Int) = COLORS[index.mod(COLORS.size)]

    @Volatile var types: Map<Long, com.tripplanner.data.PlaceType> = emptyMap()

    fun type(place: com.tripplanner.data.Place) = place.typeId?.let { types[it] }
    fun label(place: com.tripplanner.data.Place) = type(place)?.name ?: place.category.label
    fun icon(place: com.tripplanner.data.Place) = type(place)?.let { TypeIcons.res(it.icon) } ?: place.category.icon
    fun bg(place: com.tripplanner.data.Place) = type(place)?.let { colorRes(it.color) } ?: place.category.bgColor
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

    /** 지난 여행 작은 줄의 회색 타일 (모서리 12) */
    fun tile(context: Context): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(PAST.first, PAST.second)).apply {
            cornerRadius = 12 * context.resources.displayMetrics.density
        }
}

/** 장소 썸네일: 사진 또는 종류 아이콘 (상세 목록·지도 카드·편집 화면 공용) */
object Thumbs {
    /** 장소: 사진 또는 종류(기본·내가 만든) 아이콘 */
    fun bind(v: android.widget.ImageView, place: com.tripplanner.data.Place) =
        bind(v, place.photoUri, PlaceKinds.icon(place), PlaceKinds.bg(place))

    fun bind(v: android.widget.ImageView, photoPath: String?, category: PlaceCategory) =
        bind(v, photoPath, category.icon, category.bgColor)

    fun bind(v: android.widget.ImageView, photoPath: String?, iconRes: Int, bgRes: Int) {
        val ctx = v.context
        val d = ctx.resources.displayMetrics.density
        val photo = photoPath?.let { java.io.File(it) }?.takeIf { it.exists() }
        val bg = GradientDrawable().apply {
            cornerRadius = 12 * d
            setColor(ctx.color(if (photo != null) R.color.surface_container else bgRes))
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
            v.setImageResource(iconRes)
            v.imageTintList = android.content.res.ColorStateList.valueOf(ctx.color(R.color.on_surface))  // 시안: 종류 아이콘은 바탕색만 다르고 아이콘은 진한 글자색
        }
    }
}

internal fun Context.color(res: Int) = ContextCompat.getColor(this, res)
