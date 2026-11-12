package com.tripplanner.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R
import com.tripplanner.route.RouteOption
import com.tripplanner.route.RoutePlanner
import com.tripplanner.route.TimeUtil
import android.widget.FrameLayout

/**
 * 경로 제안 (시안 Route): 여행 상세에서 고른 그날의 경로.
 * 출발 시각 칩 → 지도 → 선택지 카드(세로) → 선택한 경로 시간표 → 하단 바에서 길찾기 · 적용
 * 선택한 안은 앱 안 지도에도 그려 줍니다 (숙소 → 1 → 2 → … → 숙소).
 * 계산(RoutePlanner)과 저장(RouteViewModel.apply)은 그대로입니다.
 */
class RouteActivity : AppCompatActivity() {

    private val viewModel: RouteViewModel by viewModels()
    private lateinit var routeMap: MapHost
    private lateinit var mapLabel: TextView
    private lateinit var mapLegend: TextView
    private var mapOption: RouteOption? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route)
        val toolbar = setupToolbar()
        limitScrollContentWidth(R.id.scroll)
        findViewById<View>(R.id.bottomBar).limitContentWidth()

        val tvNotes = findViewById<TextView>(R.id.tvNotes)
        val progress = findViewById<View>(R.id.progress)
        val selected = findViewById<View>(R.id.selected)
        val tvOptionTitle = findViewById<TextView>(R.id.tvOptionTitle)
        val emptyState = findViewById<View>(R.id.emptyState)
        val bottomBar = findViewById<View>(R.id.bottomBar)
        val alertPanel = findViewById<View>(R.id.alertPanel)
        val tvAlert = findViewById<TextView>(R.id.tvAlert)
        val btnMap = findViewById<Button>(R.id.btnRouteMap)
        val btnApply = findViewById<Button>(R.id.btnApply)
        val btnStartTime = findViewById<View>(R.id.btnStartTime)
        val tvStartTime = findViewById<TextView>(R.id.tvStartTime)
        val timeChips = findViewById<View>(R.id.timeChips)
        val btnEndTime = findViewById<View>(R.id.btnEndTime)
        val tvEndTime = findViewById<TextView>(R.id.tvEndTime)
        val mapCard = findViewById<View>(R.id.mapCard)
        // 출발 시각: 칩 아래 휠 (적용 전까지 안 바뀜)
        btnStartTime.setOnClickListener { chip ->
            val cur = viewModel.ui.value?.startMinutes ?: RoutePlanner.DEFAULT_START
            WheelPopup.time(chip, getString(R.string.title_start_time), cur) { m -> m?.let { viewModel.setStartTime(it) } }
        }
        // 숙소 도착 목표: 시간 자유 / 시각 정하기 + 휠 (늦으면 경로에 알림)
        btnEndTime.setOnClickListener { chip ->
            val cur = viewModel.ui.value?.endMinutes
            WheelPopup.time(chip, getString(R.string.title_end_time), cur, allowFree = true, defaultMinutes = 19 * 60) {
                viewModel.setEndTime(it)
            }
        }

        // 선택한 경로 지도 (점을 누르면 이름·도착 시각)
        mapLabel = findViewById(R.id.routeMapLabel)
        mapLegend = findViewById(R.id.routeMapLegend)
        mapLegend.setCompoundDrawablesRelativeWithIntrinsicBounds(GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(this@RouteActivity, R.color.tertiary))
            val s = (10 * resources.displayMetrics.density).toInt(); setSize(s, s)
        }, null, null, null)
        // 국내면 카카오맵, 해외면 Google Maps (키가 없으면 OpenStreetMap)
        routeMap = MapHost(findViewById<FrameLayout>(R.id.routeMap), this) { key -> showMapLabel(key) }

        // 선택지 카드: 세로로 하나씩
        val optionAdapter = RouteOptionAdapter { key -> viewModel.selectOption(key) }
        findViewById<RecyclerView>(R.id.rvOptions).apply {
            layoutManager = LinearLayoutManager(this@RouteActivity)
            adapter = optionAdapter
            itemAnimator = null
        }
        // 좁은 화면·큰 글자에서는 하단 버튼에 짧은 라벨
        val cfg = resources.configuration
        val compactButtons = cfg.screenWidthDp < 360 || cfg.fontScale > 1.15f
        (findViewById<View>(R.id.btnRouteMap) as com.tripplanner.ui.widget.TpButton).apply {
            setIconResource(R.drawable.ic_map); iconGravity = com.tripplanner.ui.widget.TpButton.ICON_GRAVITY_TEXT_START
            iconSize = (18 * resources.displayMetrics.density).toInt()
        }

        // 선택한 경로 시간표
        // 떠나는 시각을 고르면 머무는 시간으로 저장
        val stopAdapter = RouteStopAdapter({ id -> viewModel.placeOf(id) }) { visit, box ->
            WheelPopup.time(box, getString(R.string.pick_leave_time, visit.stop.name), visit.leave % (24 * 60)) { m ->
                if (m == null) return@time
                var leave = visit.arrive / (24 * 60) * (24 * 60) + m
                if (leave < visit.arrive) leave += 24 * 60
                viewModel.setLeaveTime(visit.stop.id, visit.arrive, leave)
            }
        }
        findViewById<RecyclerView>(R.id.rvStops).apply {
            layoutManager = LinearLayoutManager(this@RouteActivity)
            adapter = stopAdapter
        }

        fun render() {
            val s = viewModel.ui.value ?: return
            toolbar.title = getString(R.string.route_title, s.day)
            // 시각·거리는 직선거리로 어림한 값이라는 것을 늘 보이게
            toolbar.subtitle = getString(R.string.route_subtitle_fmt, TimeUtil.format(s.startMinutes))
            tvNotes.visibility = if (s.notes.isEmpty()) View.GONE else View.VISIBLE
            tvNotes.text = s.notes.joinToString("\n") { getString(R.string.route_note_bullet, it) }

            bindEmptyState(emptyState, s.empty) { finish() }
            val has = s.options.isNotEmpty()
            selected.visibility = if (has) View.VISIBLE else View.GONE
            bottomBar.visibility = if (has) View.VISIBLE else View.GONE
            timeChips.visibility = if (has) View.VISIBLE else View.GONE
            mapCard.visibility = if (has) View.VISIBLE else View.GONE

            val option = viewModel.selectedOption()
            optionAdapter.submit(s.options, option?.key, hasHome = s.anchor != null)
            stopAdapter.submit(option, s.anchor, s.startMinutes)
            val startLabel = TimeUtil.format(s.startMinutes)
            tvStartTime.text = startLabel
            btnStartTime.contentDescription = getString(R.string.start_time_a11y, startLabel)
            val endLabel = s.endMinutes?.let { TimeUtil.format(it) } ?: getString(R.string.end_free)
            tvEndTime.text = endLabel
            btnEndTime.contentDescription = getString(R.string.end_time_a11y, endLabel)
            if (option != null) {
                renderRouteMap(option)
                tvOptionTitle.text = getString(R.string.timetable_title, option.key)
                // 확인할 것이 있는 경로: 노란 알림 (첫 이유 + 나머지 수)
                val warnings = option.warnings
                alertPanel.visibility = if (warnings.isEmpty()) View.GONE else View.VISIBLE
                tvAlert.text = if (warnings.size <= 1) warnings.firstOrNull()
                               else getString(R.string.alert_more, warnings.first(), warnings.size - 1)
                val mapName = MapLauncher.mapName(this, MapLauncher.isDomestic(pointsOf(option)))
                btnApply.text = getString(if (compactButtons) R.string.apply_option_short else R.string.apply_option, option.key)
                btnApply.contentDescription = getString(R.string.apply_option, option.key)
                // 1:2 하단 바에서 한 줄로 보이게 '길찾기' + 지도 아이콘 (낭독은 '카카오맵 길찾기')
                btnMap.text = getString(R.string.open_directions_short)
                btnMap.contentDescription = getString(R.string.open_directions, mapName)
                btnMap.setOnClickListener { MapLauncher.openRoute(this, pointsOf(option)) }
                btnApply.setOnClickListener { viewModel.apply(option) }
            } else alertPanel.visibility = View.GONE
        }
        viewModel.ui.observe(this) { render() }
        viewModel.selectedKey.observe(this) { render() }
        viewModel.loading.observe(this) { progress.visibility = if (it == true) View.VISIBLE else View.INVISIBLE }

        // 적용 후: 스낵바 + 상세로 돌아가기
        viewModel.applied.observe(this) { msg ->
            if (msg != null) {
                com.tripplanner.ui.widget.TpSnackbar.show(this, msg, above = bottomBar,
                    action = getString(R.string.go_to_detail)) { finish() }
                viewModel.appliedShown()
            }
        }
    }

    /** 선택한 안을 지도에: 숙소(집 모양) → 번호 점 → 숙소, C안에서 추가된 후보는 청록색 */
    private fun renderRouteMap(option: RouteOption) {
        val anchor = viewModel.ui.value?.anchor
        val primary = ContextCompat.getColor(this, R.color.primary)
        val added = ContextCompat.getColor(this, R.color.tertiary)
        val pins = mutableListOf<MapPin>()
        if (anchor != null) pins += MapPin(-1, anchor.lat, anchor.lng, "", ContextCompat.getColor(this, R.color.on_surface),
            MapPin.Style.LODGING, anchor.name)
        option.visits.forEachIndexed { i, v ->
            pins += MapPin(i.toLong(), v.stop.lat, v.stop.lng, (i + 1).toString(),
                if (v.stop.isCandidate) added else primary, MapPin.Style.FILLED, v.stop.name)
        }
        val line = pointsOf(option).map { it.lat to it.lng }
        // 다른 안으로 바꾸면 범위를 다시 맞추고 이름표는 닫음
        val changed = mapOption?.key != option.key
        mapOption = option
        routeMap.render(pins, listOf(MapPath(line, primary)), forceFit = changed, tripPoints = viewModel.tripCoords())
        if (changed) mapLabel.visibility = View.GONE
        mapLegend.visibility = if (option.visits.any { it.stop.isCandidate }) View.VISIBLE else View.GONE
    }

    /** 지도에서 누른 점 이름표: "2. 예시 야키니쿠 · 17:00 도착" / 숙소 이름 */
    private fun showMapLabel(key: Long?) {
        val option = mapOption
        val text = when {
            key == null || option == null -> null
            key < 0 -> viewModel.ui.value?.anchor?.name
            else -> option.visits.getOrNull(key.toInt())?.let { v ->
                "${key + 1}. ${v.stop.name}" + getString(R.string.list_separator) +
                    getString(R.string.stop_arrive_end, TimeUtil.format(v.arrive))
            }
        }
        mapLabel.text = text
        mapLabel.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    /** 숙소 → 방문 순서 → 숙소 를 지도에 넘길 지점 목록으로 */
    private fun pointsOf(option: RouteOption): List<MapPoint> {
        val anchor = viewModel.ui.value?.anchor
        val stops = option.visits.map { MapPoint(it.stop.name, it.stop.lat, it.stop.lng) }
        return if (anchor != null) {
            val home = MapPoint(anchor.name, anchor.lat, anchor.lng)
            listOf(home) + stops + home
        } else stops
    }
}
