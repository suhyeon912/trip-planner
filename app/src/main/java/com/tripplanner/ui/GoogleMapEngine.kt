package com.tripplanner.ui

import android.content.Context
import android.os.Bundle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.tripplanner.BuildConfig
import com.tripplanner.R

/**
 * Google Maps SDK (해외).
 * 필요한 것: Google Cloud 프로젝트 + 결제 계정 + 'Maps SDK for Android' 사용 설정 + API 키.
 * 키는 local.properties 의 MAPS_API_KEY 로 넣습니다 (매니페스트에 자동으로 들어감, 깃에 올라가지 않음).
 * Google Play 서비스가 없는 기기에서는 OpenStreetMap 으로 대신 표시.
 * 키가 거부되거나 기기에서 지도를 그리지 못하면 SDK 가 알려 주지 않고 빈 화면(로고만)으로 남아서,
 * 화면에 보이는 동안 늦어지면 onSlow 로 '불러오는 중' 안내를 띄우고(지도는 그대로 기다림),
 * 그래도 오래 그려지지 않으면 onFail 로 알려 OpenStreetMap 으로 바꿉니다. 다 그려지면 onLoaded.
 * (인터넷이나 기기가 느리면 Google 지도 앱도 한참 걸리므로 너무 일찍 포기하지 않음)
 */
class GoogleMapEngine(
    private val ctx: Context,
    private val onTap: (Long?) -> Unit,
    private val onFail: (String) -> Unit = {},
    private val onSlow: (String) -> Unit = {},
    private val onLoaded: () -> Unit = {}
) : MapEngine {

    companion object {
        private const val CHECK_MS = 1_000L
        /** 화면에 보인 채로 이만큼 지나면 '불러오는 중' 안내 */
        private const val SLOW_MS = 8_000L
        /** 이만큼 지나도 다 그려지지 않으면 기본 지도로 */
        private const val LOAD_TIMEOUT_MS = 45_000L

        fun isAvailable(context: Context) = unavailableReason(context) == null

        /** 구글 지도를 못 쓰는 이유 (개발 중 화면에 표시) — 쓸 수 있으면 null */
        fun unavailableReason(context: Context): String? = when {
            BuildConfig.MAPS_API_KEY.isBlank() -> "구글 키 없음 (local.properties 의 MAPS_API_KEY 확인 후 Sync)"
            runCatching {
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS
            }.getOrDefault(true) -> "이 기기에 Google Play 서비스 없음"
            else -> null
        }
    }

    private val map = MapView(ctx).apply { id = R.id.googleMap; onCreate(null as Bundle?) }
    override val view get() = map
    private val density = density(ctx)
    private var google: GoogleMap? = null
    private var pending: Triple<List<MapPin>, List<MapPath>, Boolean>? = null
    /** 지도가 한 번이라도 다 그려졌는지 (OnMapLoadedCallback) */
    private var loaded = false
    /** 화면에 보이는 시간만 셈 (목록 보기 등으로 숨어 있는 동안은 실패로 보지 않음) */
    private var waitedMs = 0L
    private var resumed = false
    private val watchdog = object : Runnable {
        override fun run() {
            if (loaded) return
            if (resumed && map.isShown && map.width > 0 && map.height > 0) waitedMs += CHECK_MS
            if (waitedMs == SLOW_MS) onSlow(ctx.getString(R.string.google_slow))
            if (waitedMs >= LOAD_TIMEOUT_MS) {
                android.util.Log.w("GoogleMapEngine", "지도 그리기 시간 초과 (ready=${google != null})")
                onFail(ctx.getString(if (google == null) R.string.google_not_ready else R.string.google_not_loaded))
                return
            }
            map.postDelayed(this, CHECK_MS)
        }
    }

    /** 다 그려짐 (콜백은 하나만 걸리므로 카메라 맞춤 콜백에서도 부름) */
    private fun markLoaded() {
        if (loaded) return
        loaded = true; map.removeCallbacks(watchdog)
        if (waitedMs >= SLOW_MS) onLoaded()
    }

    init {
        map.postDelayed(watchdog, CHECK_MS)
        map.getMapAsync { g ->
            google = g
            g.setOnMapLoadedCallback { markLoaded() }
            g.uiSettings.isZoomControlsEnabled = true
            g.uiSettings.isMapToolbarEnabled = false
            g.setOnMarkerClickListener { m -> onTap(m.tag as? Long); true }
            g.setOnMapClickListener { onTap(null) }
            pending?.let { (p, l, f) -> draw(g, p, l, f) }
            pending = null
        }
    }

    override fun render(pins: List<MapPin>, paths: List<MapPath>, fit: Boolean) {
        val g = google
        if (g == null) { pending = Triple(pins, paths, fit || pending?.third == true); return }
        draw(g, pins, paths, fit)
    }

    private fun draw(g: GoogleMap, pins: List<MapPin>, paths: List<MapPath>, fit: Boolean) {
        g.clear()
        paths.filter { it.points.size >= 2 }.forEach { path ->
            g.addPolyline(PolylineOptions()
                .addAll(path.points.map { (la, ln) -> LatLng(la, ln) })
                .color(path.color)
                .width(4 * density))
        }
        pins.forEach { pin ->
            g.addMarker(MarkerOptions()
                .position(LatLng(pin.lat, pin.lng))
                .icon(BitmapDescriptorFactory.fromBitmap(PinIcons.bitmap(ctx, pin)))
                .anchor(0.5f, 0.5f)
                .title(pin.a11y))?.tag = pin.key
        }
        if (!fit) return
        val pts = pins.distinctCoords()
        if (pts.isEmpty()) return
        val move = {
            if (pts.size == 1) g.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pts[0].first, pts[0].second), 15f))
            else {
                val b = LatLngBounds.builder().apply { pts.forEach { include(LatLng(it.first, it.second)) } }.build()
                g.moveCamera(CameraUpdateFactory.newLatLngBounds(b, (64 * density).toInt()))
            }
        }
        // 지도 크기가 아직 0이면 newLatLngBounds 가 실패하므로 그려진 뒤에
        if (map.width > 0 && map.height > 0) move() else g.setOnMapLoadedCallback { markLoaded(); move() }
    }

    override fun onStart() = map.onStart()
    override fun onResume() { resumed = true; map.onResume() }
    override fun onPause() { resumed = false; map.onPause() }
    override fun onStop() = map.onStop()
    override fun onDestroy() { map.removeCallbacks(watchdog); map.onDestroy() }
}
