package com.tripplanner.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.tripplanner.R
import com.tripplanner.route.RouteOption
import com.tripplanner.route.RoutePlanner
import com.tripplanner.route.TimeUtil
import android.widget.FrameLayout

/**
 * 경로 제안 (4단계 구조)
 * 일차 칩 → A/B/C 요약 카드 중 하나 선택 → 그 안의 시간표 → 하단 고정 바에서 길찾기·확정
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
        setupToolbar()
        limitScrollContentWidth(R.id.scroll)
        findViewById<View>(R.id.bottomBar).limitContentWidth()
        findViewById<View>(R.id.rvDays).limitContentWidth()

        val tvDate = findViewById<TextView>(R.id.tvDate)
        val tvNotes = findViewById<TextView>(R.id.tvNotes)
        val progress = findViewById<View>(R.id.progress)
        val selected = findViewById<View>(R.id.selected)
        val tvOptionTitle = findViewById<TextView>(R.id.tvOptionTitle)
        val tvOptionDesc = findViewById<TextView>(R.id.tvOptionDesc)
        val tvOptionSummary = findViewById<TextView>(R.id.tvOptionSummary)
        val emptyState = findViewById<View>(R.id.emptyState)
        val bottomBar = findViewById<View>(R.id.bottomBar)
        val btnMap = findViewById<Button>(R.id.btnRouteMap)
        val btnApply = findViewById<Button>(R.id.btnApply)
        val btnStartTime = findViewById<Button>(R.id.btnStartTime)
        val mapCard = findViewById<View>(R.id.mapCard)
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        // 출발 시각 바꾸기 (시계 선택 창)
        btnStartTime.setOnClickListener {
            val cur = viewModel.ui.value?.startMinutes ?: RoutePlanner.DEFAULT_START
            val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
                .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
                .setHour(cur / 60).setMinute(cur % 60)
                .setTitleText(R.string.pick_start_time)
                .build()
            picker.addOnPositiveButtonClickListener { viewModel.setStartTime(picker.hour * 60 + picker.minute) }
            picker.show(supportFragmentManager, "start_time")
        }

        // 선택한 안 지도 (점을 누르면 이름·도착 시각)
        mapLabel = findViewById(R.id.routeMapLabel)
        mapLegend = findViewById(R.id.routeMapLegend)
        mapLegend.setCompoundDrawablesRelativeWithIntrinsicBounds(GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(this@RouteActivity, R.color.tertiary))
            val s = (10 * resources.displayMetrics.density).toInt(); setSize(s, s)
        }, null, null, null)
        // 국내면 카카오맵, 해외면 구글맵 (키가 없으면 OpenStreetMap)
        routeMap = MapHost(findViewById<FrameLayout>(R.id.routeMap), this) { key -> showMapLabel(key) }

        // 일차 칩
        val dayAdapter = DayChipAdapter { day -> viewModel.selectDay(day) }
        findViewById<RecyclerView>(R.id.rvDays).apply {
            layoutManager = LinearLayoutManager(this@RouteActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = dayAdapter
        }
        viewModel.dayChips.observe(this) { dayAdapter.submit(it) }

        // A/B/C 요약 카드: 칸을 나눠 나란히. 한 칸이 너무 좁으면(좁은 화면·큰 글자) 고정 폭 + 가로 스크롤
        val optionAdapter = RouteOptionAdapter { key -> viewModel.selectOption(key) }
        val rvOptions = findViewById<RecyclerView>(R.id.rvOptions).apply {
            adapter = optionAdapter
            itemAnimator = null
        }
        val cfg = resources.configuration
        val density = resources.displayMetrics.density
        // 글자 크기를 반영한 '한 칸에 필요한 최소 폭'
        val minCardDp = 112 * cfg.fontScale
        // 넓은 화면에서는 내용 폭이 640dp 로 제한되므로 그 폭 기준
        val contentDp = minOf(cfg.screenWidthDp - 32, 640)
        fun cardsFit(n: Int) = contentDp / n.coerceAtLeast(1) >= minCardDp
        // 하단 버튼도 같은 기준으로 짧은 라벨 사용
        val compactButtons = cfg.screenWidthDp < 360 || cfg.fontScale > 1.15f

        // 선택한 안의 시간표
        val stopAdapter = RouteStopAdapter()
        findViewById<RecyclerView>(R.id.rvStops).apply {
            layoutManager = LinearLayoutManager(this@RouteActivity)
            adapter = stopAdapter
        }

        fun render() {
            val s = viewModel.ui.value ?: return
            tvDate.text = s.dateLabel + getString(R.string.list_separator) +
                (s.anchor?.let { getString(R.string.route_anchor, it.name) } ?: getString(R.string.route_no_anchor))
            tvNotes.visibility = if (s.notes.isEmpty()) View.GONE else View.VISIBLE
            tvNotes.text = s.notes.joinToString("\n") { getString(R.string.route_note_bullet, it) }

            bindEmptyState(emptyState, s.empty) { finish() }
            val has = s.options.isNotEmpty()
            selected.visibility = if (has) View.VISIBLE else View.GONE
            bottomBar.visibility = if (has) View.VISIBLE else View.GONE

            val option = viewModel.selectedOption()
            val n = s.options.size.coerceAtLeast(1)
            if (cardsFit(n)) {
                if ((rvOptions.layoutManager as? GridLayoutManager)?.spanCount != n)
                    rvOptions.layoutManager = GridLayoutManager(this, n)
                optionAdapter.submit(s.options, option?.key)
            } else {
                if (rvOptions.layoutManager !is LinearLayoutManager || rvOptions.layoutManager is GridLayoutManager)
                    rvOptions.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                optionAdapter.submit(s.options, option?.key, fixedWidth = (minCardDp * 1.15f * density).toInt())
            }
            stopAdapter.submit(option, s.anchor, s.startMinutes)
            val startLabel = TimeUtil.format(s.startMinutes)
            btnStartTime.text = getString(R.string.start_time_btn, startLabel)
            btnStartTime.contentDescription = getString(R.string.start_time_a11y, startLabel)
            toolbar.subtitle = getString(R.string.route_subtitle_fmt, startLabel)
            mapCard.visibility = if (has) View.VISIBLE else View.GONE
            if (option != null) renderRouteMap(option)
            if (option != null) {
                tvOptionTitle.text = getString(R.string.option_title, option.key, option.title)
                tvOptionDesc.text = option.description
                tvOptionSummary.text = statsLine(this, option)
                val mapName = MapLauncher.mapName(this, MapLauncher.isDomestic(pointsOf(option)))
                btnApply.text = getString(if (compactButtons) R.string.apply_option_short else R.string.apply_option, option.key)
                btnApply.contentDescription = getString(R.string.apply_option, option.key)
                btnMap.text = if (compactButtons) getString(R.string.open_directions_short) else getString(R.string.open_directions, mapName)
                btnMap.contentDescription = getString(R.string.open_directions, mapName)
                btnMap.setOnClickListener { MapLauncher.openRoute(this, pointsOf(option)) }
                btnApply.setOnClickListener { viewModel.apply(option) }
            }
        }
        viewModel.ui.observe(this) { render() }
        viewModel.selectedKey.observe(this) { render() }
        viewModel.loading.observe(this) { progress.visibility = if (it == true) View.VISIBLE else View.INVISIBLE }

        // 확정 후: 스낵바 + 상세로 돌아가기
        viewModel.applied.observe(this) { msg ->
            if (msg != null) {
                Snackbar.make(findViewById(R.id.root), msg, Snackbar.LENGTH_LONG)
                    .setAnchorView(bottomBar)
                    .setAction(R.string.go_to_detail) { finish() }
                    .show()
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
