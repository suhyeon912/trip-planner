package com.tripplanner.ui

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R

/**
 * 앱 전체 공통 모션 (1단계)
 *  · 스크롤 끝 효과: Android 11 이하의 파란 빛번짐(글로우)은 끔. 12 이상의 늘어나는 효과는 그대로
 *  · (상단 바 구분선은 TopBarLayout 이 맡음)
 * 화면마다 코드를 넣지 않도록 Activity 가 시작될 때 한 번 적용합니다.
 */
object Motion {

    fun register(app: Application) = app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) = apply(activity)
        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivityResumed(a: Activity) {
            // 화면이 뜬 뒤에 만들어진 카드까지 (예: 설정 목록)
            a.findViewById<ViewGroup>(android.R.id.content)?.let { r -> walk(r) { clipRoundedCard(it) } }
        }
        override fun onActivityPaused(a: Activity) {}
        override fun onActivityStopped(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(a: Activity) {}
    })

    private fun apply(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) walk(root) { v ->
            if (v is RecyclerView || v is NestedScrollView || v is ScrollView || v is HorizontalScrollView)
                v.overScrollMode = View.OVER_SCROLL_NEVER
        }
        walk(root) { v -> clipRoundedCard(v) }
    }

    /**
     * 둥근 카드 안의 누를 수 있는 줄: 눌림 덧칠이 카드 모서리 밖으로 삐져나오지 않게 카드 모양으로 자름.
     * (XML 의 clipToOutline 은 Android 12 이상에서만 적용되어 코드로 켬)
     */
    fun clipRoundedCard(v: View) {
        if (v !is android.widget.LinearLayout || v.clipToOutline) return
        val bg = v.background as? android.graphics.drawable.GradientDrawable ?: return
        // getCornerRadii() 는 Android 11 이하에서 값이 없으면 앱이 멈춤 → 감싸서 확인
        val rounded = bg.cornerRadius > 0f || runCatching { bg.cornerRadii?.any { it > 0f } == true }.getOrDefault(false)
        if (!rounded) return
        v.clipToOutline = true
    }

    private fun walk(v: View, f: (View) -> Unit) {
        f(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), f)
    }

}
