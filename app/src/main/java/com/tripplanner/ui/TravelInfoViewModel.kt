package com.tripplanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tripplanner.TripPlannerApp
import com.tripplanner.data.Flight
import com.tripplanner.data.Lodging
import kotlinx.coroutines.launch

/** 비행기 · 숙소 관리 화면의 데이터 담당 */
class TravelInfoViewModel(app: Application, state: SavedStateHandle) : AndroidViewModel(app) {

    private val repo = (app as TripPlannerApp).repository
    val tripId: Long = state.get<Long>(TripDetailActivity.EXTRA_TRIP_ID) ?: -1L

    val flights = repo.observeFlights(tripId)
    val lodgings = repo.observeLodgings(tripId)

    fun saveFlight(f: Flight) = viewModelScope.launch { repo.saveFlight(f) }
    fun deleteFlight(f: Flight) = viewModelScope.launch { repo.deleteFlight(f) }
    fun saveLodging(l: Lodging) = viewModelScope.launch { repo.saveLodging(l) }
    fun deleteLodging(l: Lodging) = viewModelScope.launch { repo.deleteLodging(l) }
}
