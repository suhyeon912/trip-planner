package com.tripplanner.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.tripplanner.BuildConfig
import com.tripplanner.R
import com.tripplanner.route.Region
import org.osmdroid.config.Configuration
import java.io.File

/*
 * 앱 안 지도.
 *  - 국내 여행: 카카오맵 SDK   (local.properties 의 KAKAO_NATIVE_APP_KEY 필요)
 *  - 해외 여행: Google Maps SDK (local.properties 의 MAPS_API_KEY 필요)
 *  - 키가 없거나, 기기가 지원하지 않거나, 인증에 실패하면 OpenStreetMap 으로 대신 보여 줌
 *    → 키를 아직 안 받았어도, 에뮬레이터에서도 지도가 나옵니다.
 *
 * 화면(Activity)은 MapHost 하나만 만들고 render() 만 부르면 됩니다.
 * 어느 지도를 쓸지, 지도별 생명주기(resume/pause …)는 MapHost 가 알아서 처리합니다.
 */
object TripMap {

    /** 앱 시작 시 한 번 (TripPlannerApp.onCreate) */
    fun init(context: Context) {
        // OpenStreetMap 타일 이용 규칙: 앱 이름 User-Agent, 앱 캐시 폴더에 저장
        val conf = Configuration.getInstance()
        conf.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        conf.userAgentValue = "TripPlanner/1.0 (Android; ${context.packageName}; student project)"
        val base = File(context.cacheDir, "osmdroid")
        conf.osmdroidBasePath = base
        conf.osmdroidTileCache = File(base, "tiles")

        KakaoMapEngine.initSdk(context)
    }
}

/** 지도에 찍을 점 하나 */
data class MapPin(
    val key: Long,              // 누르면 돌려줄 값 (장소 id 등). 숙소는 -1
    val lat: Double,
    val lng: Double,
    val label: String,          // 동그라미 안 글자 ("1", "2" …). 빈 글자면 점만
    val color: Int,             // 채우기 색 (ARGB)
    val style: Style = Style.FILLED,
    val a11y: String = ""
) {
    enum class Style { FILLED, HOLLOW, LODGING }
}

/** 점들을 잇는 선 하나 */
data class MapPath(val points: List<Pair<Double, Double>>, val color: Int)

/** 지도 하나(카카오·구글·OSM)가 지켜야 할 약속 */
interface MapEngine {
    val view: View
    /** 점·선 그리기. fit 이면 모든 점이 보이게 카메라 이동 */
    fun render(pins: List<MapPin>, paths: List<MapPath>, fit: Boolean)
    fun onStart() {}
    fun onResume() {}
    fun onPause() {}
    fun onStop() {}
    fun onDestroy() {}
}

enum class MapProvider { KAKAO, GOOGLE, OSM }

/**
 * 지도 칸(FrameLayout) 하나를 맡아 알맞은 지도를 골라 넣는 도우미.
 * 점들이 대부분 국내면 카카오, 해외면 구글. 사용할 수 없으면 OpenStreetMap.
 */
class MapHost(
    private val container: FrameLayout,
    private val owner: LifecycleOwner,
    private val onTap: (Long?) -> Unit
) {
    private val ctx = container.context
    private var engine: MapEngine? = null
    var provider: MapProvider? = null
        private set
    /** 이번 실행에서 실패한 지도 (다시 시도하지 않음) */
    private val failed = mutableSetOf<MapProvider>()
    private var last: Triple<List<MapPin>, List<MapPath>, String>? = null
    private val notice = TextView(ctx).apply {
        setBackgroundResource(R.drawable.bg_map_overlay)
        setTextAppearance(R.style.TextAppearance_TripPlanner_LabelMedium)
        setTextColor(ContextCompat.getColor(ctx, R.color.text_sub))
        val p = (8 * ctx.resources.displayMetrics.density).toInt()
        setPadding(p, p / 2, p, p / 2)
        visibility = View.GONE
    }

    init {
        owner.lifecycle.addObserver(LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> engine?.onStart()
                Lifecycle.Event.ON_RESUME -> engine?.onResume()
                Lifecycle.Event.ON_PAUSE -> engine?.onPause()
                Lifecycle.Event.ON_STOP -> engine?.onStop()
                Lifecycle.Event.ON_DESTROY -> { engine?.onDestroy(); engine = null }
                else -> Unit
            }
        })
    }

    /**
     * @param tripPoints 여행 전체의 장소·숙소 좌표. 지도 종류(카카오/구글)는 이걸로 한 번에 정해서
     *        어느 탭·일차를 보든 같은 지도가 나오게 함. null 이면 지금 찍는 점들로 판단
     */
    fun render(pins: List<MapPin>, paths: List<MapPath>, forceFit: Boolean = false,
               tripPoints: List<Pair<Double, Double>>? = null) {
        val points = tripPoints ?: pins.map { it.lat to it.lng }
        val want = choose(points)
        val switched = want != provider
        if (switched) {
            switchTo(want)
            // 개발 중(디버그 빌드)에는 카카오/구글 대신 기본 지도가 나온 이유를 작게 표시
            if (want == MapProvider.OSM && BuildConfig.DEBUG && notice.visibility != View.VISIBLE) {
                val reason = when (pickProvider(points, null, kakaoOk = true, googleOk = true)) {
                    MapProvider.KAKAO -> if (MapProvider.KAKAO in failed) null else KakaoMapEngine.unavailableReason()
                    MapProvider.GOOGLE -> GoogleMapEngine.unavailableReason(ctx)
                    MapProvider.OSM -> null
                }
                if (reason != null) { notice.text = reason; notice.visibility = View.VISIBLE }
            }
        }
        val key = pins.joinToString("|") { "${it.lat},${it.lng}" }
        val fit = forceFit || switched || key != last?.third
        last = Triple(pins, paths, key)
        engine?.render(pins, paths, fit)
    }

    private fun choose(points: List<Pair<Double, Double>>): MapProvider = pickProvider(
        points, provider,
        kakaoOk = MapProvider.KAKAO !in failed && KakaoMapEngine.isAvailable(ctx),
        googleOk = MapProvider.GOOGLE !in failed && GoogleMapEngine.isAvailable(ctx))

    private fun switchTo(p: MapProvider) {
        engine?.let { old ->
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) old.onPause()
            old.onStop(); old.onDestroy()
        }
        container.removeAllViews()
        provider = p
        val e: MapEngine = when (p) {
            MapProvider.KAKAO -> KakaoMapEngine(ctx, onTap) { reason -> fallback(MapProvider.KAKAO, reason) }
            MapProvider.GOOGLE -> GoogleMapEngine(ctx, onTap)
            MapProvider.OSM -> OsmMapEngine(ctx, onTap)
        }
        engine = e
        container.addView(TouchGuardFrame(ctx).apply { addView(e.view) },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        container.addView(notice, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            val m = (8 * ctx.resources.displayMetrics.density).toInt(); setMargins(m, m, m, m)
        })
        // 이미 화면이 떠 있는 중이면 새 지도도 같은 상태로
        val state = owner.lifecycle.currentState
        if (state.isAtLeast(Lifecycle.State.STARTED)) e.onStart()
        if (state.isAtLeast(Lifecycle.State.RESUMED)) e.onResume()
    }

    /** 카카오 인증 실패 등 → OpenStreetMap 으로 바꾸고 이유를 작게 표시 */
    private fun fallback(from: MapProvider, reason: String) {
        container.post {
            if (provider != from) return@post
            failed += from
            switchTo(MapProvider.OSM)
            notice.text = reason
            notice.visibility = View.VISIBLE
            last?.let { (pins, paths, _) -> engine?.render(pins, paths, true) }
        }
    }
}

/**
 * 어느 지도를 쓸지 (단위 테스트 가능하도록 분리).
 * 여행 전체 좌표가 모두 국내면 카카오, 해외가 한 곳이라도 있으면 구글 (국내 출발 해외여행 = 구글).
 * 좌표가 하나도 없으면 지금 지도 유지(처음이면 국내로 봄). 쓸 수 없으면 OpenStreetMap.
 */
fun pickProvider(points: List<Pair<Double, Double>>, current: MapProvider?, kakaoOk: Boolean, googleOk: Boolean): MapProvider {
    val domestic = if (points.isEmpty()) current == null || current == MapProvider.KAKAO
                   else points.all { (la, ln) -> Region.isKorea(la, ln) }
    return when {
        domestic && kakaoOk -> MapProvider.KAKAO
        !domestic && googleOk -> MapProvider.GOOGLE
        else -> MapProvider.OSM
    }
}

/** 스크롤 화면 안의 지도: 지도를 만지는 동안 바깥 스크롤이 손가락을 가로채지 않게 */
private class TouchGuardFrame(context: Context) : FrameLayout(context) {
    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(e)
    }
}

/** 세 지도가 같이 쓰는 점 그림 (번호 동그라미 · 속 빈 동그라미 · 숙소) */
object PinIcons {
    /** 일차별 선·점 색 (흰 글자와 대비 3:1 이상) */
    val DAY_COLORS = intArrayOf(
        0xFF2563EB.toInt(), // 파랑
        0xFFC2410C.toInt(), // 주황
        0xFF047857.toInt(), // 초록
        0xFF7E22CE.toInt(), // 보라
        0xFFBE185D.toInt(), // 분홍
        0xFF0E7490.toInt(), // 청록
    )
    fun dayColor(day: Int) = DAY_COLORS[(day - 1).mod(DAY_COLORS.size)]

    private val cache = HashMap<String, Bitmap>()

    fun bitmap(ctx: Context, pin: MapPin): Bitmap {
        val density = ctx.resources.displayMetrics.density
        return cache.getOrPut("${pin.style}|${pin.color}|${pin.label}|$density") {
            if (pin.style == MapPin.Style.LODGING) lodging(ctx, pin.color, density)
            else circle(ctx, pin.label, pin.color, pin.style == MapPin.Style.HOLLOW, density)
        }
    }

    private fun circle(ctx: Context, label: String, color: Int, hollow: Boolean, density: Float): Bitmap {
        val d = if (label.isEmpty()) 18 * density else 28 * density
        val stroke = 2.5f * density
        val size = (d + stroke * 2).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val r = d / 2f
        c.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = if (hollow) 0xFFFFFFFF.toInt() else color })
        c.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke
            this.color = if (hollow) color else 0xFFFFFFFF.toInt()
        })
        if (label.isNotEmpty()) {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = if (hollow) color else 0xFFFFFFFF.toInt()
                textSize = (if (label.length > 1) 11 else 13) * density
                textAlign = Paint.Align.CENTER
                typeface = ResourcesCompat.getFont(ctx, R.font.pretendard_semibold) ?: Typeface.DEFAULT_BOLD
            }
            c.drawText(label, cx, cx - (text.descent() + text.ascent()) / 2, text)
        }
        return bmp
    }

    private fun lodging(ctx: Context, color: Int, density: Float): Bitmap {
        val d = 32 * density
        val stroke = 2.5f * density
        val size = (d + stroke * 2).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        c.drawCircle(cx, cx, d / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() })
        c.drawCircle(cx, cx, d / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; this.color = color
        })
        ContextCompat.getDrawable(ctx, R.drawable.ic_hotel)?.mutate()?.let { icon ->
            val s = (18 * density).toInt()
            val o = ((size - s) / 2f).toInt()
            icon.setTint(color)
            icon.setBounds(o, o, o + s, o + s)
            icon.draw(c)
        }
        return bmp
    }

    fun drawable(ctx: Context, pin: MapPin) = BitmapDrawable(ctx.resources, bitmap(ctx, pin))
}

/** 점 여러 개 → 범위 계산용 (가장자리 점이 버튼에 가리지 않게 여유를 둠) */
internal fun List<MapPin>.distinctCoords() = map { it.lat to it.lng }.distinct()

internal fun density(ctx: Context) = ctx.resources.displayMetrics.density
