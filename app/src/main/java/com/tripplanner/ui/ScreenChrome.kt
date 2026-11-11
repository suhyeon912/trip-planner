package com.tripplanner.ui

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.tripplanner.R

/**
 * 화면 위 툴바 공통 설정.
 * 레이아웃에 id 가 toolbar 인 MaterialToolbar 가 있어야 합니다.
 * 뒤로 가기 버튼은 시스템 뒤로 가기와 똑같이 동작합니다.
 */
fun AppCompatActivity.setupToolbar(title: CharSequence? = null, showBack: Boolean = true): MaterialToolbar {
    val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
    if (title != null) toolbar.title = title
    if (showBack) {
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.setNavigationContentDescription(R.string.nav_back)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }
    return toolbar
}

/**
 * 넓은 화면(태블릿·가로)에서 내용이 너무 넓게 퍼지지 않도록 가운데 최대 640dp 로 모읍니다.
 * 좌우 padding 만 늘리므로 스크롤·터치 영역과 기존 동작은 그대로입니다.
 * 좁은 화면에서는 아무것도 바꾸지 않습니다.
 */
fun android.view.View.limitContentWidth() {
    val res = resources
    val density = res.displayMetrics.density
    val maxPx = res.getDimensionPixelSize(R.dimen.content_max_width)
    val screenPx = (res.configuration.screenWidthDp * density).toInt()
    val extra = (screenPx - paddingStart - paddingEnd - maxPx) / 2
    if (extra > 0) setPaddingRelative(paddingStart + extra, paddingTop, paddingEnd + extra, paddingBottom)
}

/** NestedScrollView 등 스크롤 뷰 안의 첫 내용물에 limitContentWidth 적용 */
fun AppCompatActivity.limitScrollContentWidth(scrollId: Int) {
    (findViewById<android.view.ViewGroup>(scrollId)).getChildAt(0)?.limitContentWidth()
}
