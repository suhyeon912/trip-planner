package com.tripplanner.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * 카드 틀 (MaterialCardView 대신): 바탕(bg_card 류의 둥근 모양)으로 안의 내용(사진 등)을 잘라 줌.
 * 눌림 덧칠은 android:foreground, 눌림 축소는 android:stateListAnimator 로.
 */
class CardFrame @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    init { clipToOutline = true }
}
