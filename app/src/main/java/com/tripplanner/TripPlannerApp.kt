package com.tripplanner

import android.app.Application
import com.tripplanner.data.AppDatabase
import com.tripplanner.data.TripRepository
import com.tripplanner.share.ShareManager
import com.tripplanner.ui.TripMap

/** 앱이 켜질 때 가장 먼저 만들어지는 객체 — DB 와 Repository 를 한 번만 만들어 둡니다 */
class TripPlannerApp : Application() {
    val repository: TripRepository by lazy { TripRepository(AppDatabase.get(this)) }

    /** 공유·초대 (Firebase 설정 파일이 없으면 isConfigured = false) */
    val share: ShareManager by lazy { ShareManager(this, repository) }

    override fun onCreate() {
        super.onCreate()
        com.tripplanner.ui.AppPrefs.applyTheme(this)   // 설정의 테마 (기기 설정 / 밝게 / 어둡게)
        com.tripplanner.ui.Motion.register(this)   // 공통 모션 (스크롤 끝 효과 · 상단 바 구분선)
        TripMap.init(this)   // 앱 안 지도 설정
        share.start()        // 공유 중인 여행 실시간 동기화
        // 내가 만든 장소 종류: 늘 최신으로 (목록·지도·편집 화면이 함께 씀)
        repository.observeTypes().observeForever { list -> com.tripplanner.ui.PlaceKinds.types = list.associateBy { it.id } }
    }
}
