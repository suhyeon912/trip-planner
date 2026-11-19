package com.tripplanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tripplanner.TripPlannerApp
import com.tripplanner.data.Trip
import kotlinx.coroutines.launch

/** 여행 목록 화면의 데이터 담당 — 화면을 돌려도 사라지지 않음 */
class TripListViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as TripPlannerApp).repository

    /** DB 가 바뀌면 자동으로 새 목록이 들어옴 */
    val trips = repo.observeTripsWithCounts()

    init {
        // 예시 여행은 개발 버전에서만 (실사용 버전은 빈 목록으로 시작, 여행을 다 지워도 다시 생기지 않음)
        if (com.tripplanner.BuildConfig.SAMPLE_DATA) viewModelScope.launch {
            repo.seedIfEmpty { asset -> Photos.importAsset(getApplication(), asset) }
        }
    }

    fun deleteTrip(trip: Trip) = viewModelScope.launch {
        repo.deleteTrip(trip)
        Photos.delete(getApplication(), trip.coverPhoto)   // 표지 사진 파일도 정리
    }
}
