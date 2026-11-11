package com.tripplanner.ui

import android.content.Context
import android.os.Build
import android.util.Log
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapAuthException
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles
import com.tripplanner.BuildConfig
import com.tripplanner.R

/**
 * 카카오맵 SDK v2 (국내).
 * 필요한 것: 카카오 개발자 콘솔의 '네이티브 앱 키' + 앱 패키지명 · 키 해시 등록 + 카카오맵 사용 설정 ON.
 * 키는 local.properties 의 KAKAO_NATIVE_APP_KEY 로 넣습니다 (깃에 올라가지 않음).
 * 인증에 실패하면 onFail 로 알려서 OpenStreetMap 으로 바뀝니다.
 */
class KakaoMapEngine(
    private val ctx: Context,
    private val onTap: (Long?) -> Unit,
    private val onFail: (String) -> Unit
) : MapEngine {

    companion object {
        private const val TAG = "KakaoMapEngine"
        private val KEY = BuildConfig.KAKAO_NATIVE_APP_KEY

        /** 카카오맵 SDK 는 ARM 기기(armeabi-v7a, arm64-v8a)만 지원 — x86 에뮬레이터는 제외 */
        private fun armDevice() = Build.SUPPORTED_ABIS.any { it.startsWith("arm") }

        fun isAvailable(context: Context) = unavailableReason() == null

        /** 카카오맵을 못 쓰는 이유 (개발 중 화면에 표시) — 쓸 수 있으면 null */
        fun unavailableReason(): String? = when {
            KEY.isBlank() -> "카카오 키 없음 (local.properties 의 KAKAO_NATIVE_APP_KEY 확인 후 Sync)"
            !armDevice() -> "이 기기는 카카오맵 미지원 (ABI: ${Build.SUPPORTED_ABIS.joinToString()})"
            !KakaoMapSdk.isInitialized() -> "카카오맵 SDK 초기화 실패: ${initError ?: "원인 미상"} (Logcat: KakaoMapEngine)"
            else -> null
        }

        /** 초기화 실패 이유 (예: 이 기기가 카카오 엔진 파일을 못 읽음) */
        private var initError: String? = null

        fun initSdk(context: Context) {
            if (KEY.isBlank() || !armDevice()) return
            try {
                KakaoMapSdk.init(context, KEY)
            } catch (t: Throwable) {
                Log.w(TAG, "카카오맵 SDK 초기화 실패", t)
                initError = t.javaClass.simpleName + (t.message?.let { " - " + it.take(80) } ?: "")
            }
        }
    }

    private val map = MapView(ctx).apply { id = R.id.kakaoMap }
    override val view get() = map
    private val density = density(ctx)
    private var kakao: KakaoMap? = null
    private var pending: Triple<List<MapPin>, List<MapPath>, Boolean>? = null

    /** 이 시간 안에 지도가 준비되지 않으면 기본 지도로 (에뮬레이터 등에서 화면이 하얗게 멈추는 것 방지) */
    private val readyTimeout = Runnable {
        if (kakao == null) {
            Log.w(TAG, "카카오맵 준비 시간 초과")
            onFail(ctx.getString(R.string.kakao_timeout))
        }
    }

    init {
        // 카카오 지도는 화면에 붙은 뒤 시작해야 안정적이라 attach 시점에 start
        if (map.isAttachedToWindow) startMap() else map.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) { map.removeOnAttachStateChangeListener(this); startMap() }
            override fun onViewDetachedFromWindow(v: android.view.View) {}
        })
    }

    private fun startMap() {
        Log.d(TAG, "start (SDK ${MapView.getApiVersion()})")
        map.postDelayed(readyTimeout, 10_000)
        map.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {}
            override fun onMapError(error: Exception) {
                Log.w(TAG, "카카오맵 오류", error)
                map.removeCallbacks(readyTimeout)
                val reason = if (error is MapAuthException)
                    ctx.getString(R.string.kakao_auth_failed, runCatching { KakaoMapSdk.INSTANCE.hashKey }.getOrNull() ?: "?")
                else ctx.getString(R.string.kakao_failed) + " (" + error.javaClass.simpleName + ")"
                onFail(reason)
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(k: KakaoMap) {
                Log.d(TAG, "ready")
                map.removeCallbacks(readyTimeout)
                kakao = k
                k.setOnLabelClickListener { _, _, label -> onTap(label.tag as? Long); true }
                k.setOnMapClickListener { _, _, _, _ -> onTap(null) }
                pending?.let { (p, l, f) -> draw(k, p, l, f) }
                pending = null
            }
            override fun getPosition(): LatLng = LatLng.from(36.5, 127.8)
            override fun getZoomLevel() = 7
        })
    }

    override fun render(pins: List<MapPin>, paths: List<MapPath>, fit: Boolean) {
        val k = kakao
        if (k == null) {
            // 지도가 아직 준비 중이면 준비되면 그림 (fit 은 한 번이라도 요청됐으면 유지)
            pending = Triple(pins, paths, fit || pending?.third == true)
            return
        }
        draw(k, pins, paths, fit)
    }

    private fun draw(k: KakaoMap, pins: List<MapPin>, paths: List<MapPath>, fit: Boolean) {
        val routes = k.routeLineManager?.layer
        routes?.removeAll()
        paths.filter { it.points.size >= 2 }.forEach { path ->
            val style = RouteLineStyles.from(RouteLineStyle.from(4 * density, path.color))
            val seg = RouteLineSegment.from(path.points.map { (la, ln) -> LatLng.from(la, ln) }, style)
            routes?.addRouteLine(RouteLineOptions.from(seg))
        }
        val labels = k.labelManager?.layer
        labels?.removeAll()
        pins.forEach { pin ->
            val style = LabelStyle.from(PinIcons.bitmap(ctx, pin)).setAnchorPoint(0.5f, 0.5f).setApplyDpScale(false)
            labels?.addLabel(LabelOptions.from(LatLng.from(pin.lat, pin.lng))
                .setStyles(LabelStyles.from(style))
                .setClickable(true)
                .setTag(pin.key))
        }
        if (!fit) return
        val pts = pins.distinctCoords()
        when {
            pts.isEmpty() -> Unit
            pts.size == 1 -> k.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(pts[0].first, pts[0].second), 16))
            else -> k.moveCamera(CameraUpdateFactory.fitMapPoints(
                pts.map { LatLng.from(it.first, it.second) }.toTypedArray(), (64 * density).toInt()))
        }
    }

    override fun onResume() = map.resume()
    override fun onPause() = map.pause()
    override fun onDestroy() { map.removeCallbacks(readyTimeout); map.finish() }
}
