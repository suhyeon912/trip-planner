package com.tripplanner.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/** 인터넷 연결 여부 (앱 전체에서 하나). 오프라인이면 여행 상세 부제에 '오프라인 · 마지막 동기화' 표시 */
object NetworkState {
    private val _online = MutableLiveData(true)
    val online: LiveData<Boolean> get() = _online

    fun init(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        fun current() = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _online.postValue(current())
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { _online.postValue(true) }
                override fun onLost(network: Network) { _online.postValue(current()) }
            })
        }
    }
}
