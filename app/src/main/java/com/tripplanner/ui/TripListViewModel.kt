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
        viewModelScope.launch { repo.seedIfEmpty() }
    }

    fun saveTrip(trip: Trip) = viewModelScope.launch {
        if (trip.id == 0L) repo.addTrip(trip) else repo.updateTrip(trip)
    }

    fun deleteTrip(trip: Trip) = viewModelScope.launch { repo.deleteTrip(trip) }
}
