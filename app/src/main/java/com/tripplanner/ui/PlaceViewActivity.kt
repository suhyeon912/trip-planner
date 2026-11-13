package com.tripplanner.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tripplanner.ui.widget.TpButton
import com.tripplanner.R
import com.tripplanner.TripPlannerApp
import com.tripplanner.data.Place
import com.tripplanner.data.PlaceStatus
import com.tripplanner.route.Region
import kotlinx.coroutines.launch

/**
 * 장소 정보 (시안 PlaceView, 읽기 전용) — 여행 상세에서 장소 카드를 한 번 누르면 열림.
 * ⋮ → 수정(장소 수정 화면) / 삭제(확인 팝업). 지도 앱에서 보기 · 여행 지도에서.
 * 예약·티켓: 파일 · 사진 올리기 (이 기기에만) · 관련 링크: 추가 · 수정 · 삭제 (함께 보는 사람과 공유).
 */
class PlaceViewActivity : AppCompatActivity() {

    companion object {
        /** '여행 지도에서'를 누르면 여행 상세로 돌려주는 장소 id */
        const val RESULT_SHOW_ON_MAP = "show_on_map"
    }

    private val repo by lazy { (application as TripPlannerApp).repository }
    private val tripId by lazy { intent.getLongExtra(TripDetailActivity.EXTRA_TRIP_ID, -1L) }
    private val placeId by lazy { intent.getLongExtra(AddPlaceActivity.EXTRA_PLACE_ID, -1L) }
    private var place: Place? = null
    private val files = AttachmentsUi(this, { Triple(tripId, placeId, com.tripplanner.data.AttachmentKind.PLACE) }) { refreshFiles() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_place_view)
        val toolbar = setupToolbar()
        toolbar.menu.add(0, R.id.action_more, 0, R.string.more).apply {
            setIcon(R.drawable.ic_more_vert)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_more) showMenu(toolbar.findViewById(R.id.action_more) ?: toolbar)
            true
        }
        limitScrollContentWidth(R.id.scroll)
        findViewById<View>(R.id.btnUpload).setOnClickListener { files.showUploadMenu(it) }
        findViewById<View>(R.id.btnAddLink).setOnClickListener { editLink(null) }
    }

    private fun refreshFiles() {
        lifecycleScope.launch { files.bindRows(findViewById(R.id.fileRows), repo.getPlaceAttachments(placeId), showKind = false) }
    }

    // ---------------- 관련 링크 ----------------
    private fun links() = com.tripplanner.data.PlaceLinks.parse(place?.links.orEmpty())

    private fun bindLinks() {
        bindLinkRows(findViewById(R.id.linkRows), links(), onOpen = { openLink(it.url) }) { anchor, i ->
            showSelect(anchor, listOf(
                SelectRow.Option(getString(R.string.action_edit)) { editLink(i) },
                SelectRow.Option(getString(R.string.action_delete), danger = true) {
                    val l = links().getOrNull(i) ?: return@Option
                    confirmDelete(this, l.name.ifBlank { l.url }, getString(R.string.link_delete_confirm)) {
                        saveLinks(links().filterIndexed { k, _ -> k != i })
                    }
                }
            ), widthDp = 168)
        }
    }

    /** 링크 추가(index = null) · 수정 팝업: 이름 · 주소 */
    private fun editLink(index: Int?) {
        val cur = index?.let { links().getOrNull(it) }
        showFormDialog(this, getString(if (cur == null) R.string.link_add else R.string.link_edit), listOf(
            Field(getString(R.string.link_name), cur?.name.orEmpty()),
            Field(getString(R.string.link_url), cur?.url ?: "https://", android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI)
        ), positive = if (cur == null) R.string.action_add else R.string.action_save) { (name, rawUrl) ->
            val url = normalizeUrl(rawUrl)
            if (url == null) { showSnack(getString(R.string.error_link_url)); false }
            else {
                val link = com.tripplanner.data.PlaceLink(name.ifBlank { url.removePrefix("https://").removePrefix("http://").substringBefore('/') }, url)
                saveLinks(if (index == null) links() + link else links().mapIndexed { k, l -> if (k == index) link else l })
                true
            }
        }
    }

    private fun normalizeUrl(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty() || t == "https://" || t == "http://") return null
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
    }

    private fun saveLinks(list: List<com.tripplanner.data.PlaceLink>) {
        val p = place ?: return
        val updated = p.copy(links = com.tripplanner.data.PlaceLinks.encode(list))
        place = updated
        bindLinks()
        lifecycleScope.launch { repo.savePlace(updated) }
    }

    private fun openLink(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
            .onFailure { showSnack(getString(R.string.no_app_to_open)) }
    }

    /** 수정하고 돌아오면 바뀐 내용으로 다시 그림 */
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val p = repo.getPlace(placeId) ?: run { finish(); return@launch }
            val all = repo.getPlaces(tripId)
            val lodgings = repo.getLodgings(tripId)
            place = p
            bind(p, all, lodgings.filter { it.lat != 0.0 || it.lng != 0.0 }.map { it.lat to it.lng })
        }
    }

    private fun bind(p: Place, all: List<Place>, lodgingCoords: List<Pair<Double, Double>>) {
        Thumbs.bind(findViewById(R.id.ivThumb), p)
        (findViewById<ImageView>(R.id.ivThumb).background as? android.graphics.drawable.GradientDrawable)
            ?.cornerRadius = 16 * resources.displayMetrics.density
        findViewById<TextView>(R.id.tvName).text = p.name

        // 종류 · 상태(확정 · n일차 n번째 / 후보) · 예약필수
        val chips = findViewById<android.view.ViewGroup>(R.id.chips)
        chips.removeAllViews()
        fun badge(text: String, bg: Int?, fg: Int) {
            val tv = LayoutInflater.from(this).inflate(R.layout.view_badge, chips, false) as TextView
            tv.text = text
            if (bg == null) { tv.background = null; tv.setPadding(0, tv.paddingTop, 0, tv.paddingBottom) }
            else tv.backgroundTintList = ColorStateList.valueOf(color(bg))
            tv.setTextColor(color(fg))
            chips.addView(tv)
        }
        badge(PlaceKinds.label(p), R.color.surface_container, R.color.on_surface)
        badge(statusText(p, all), if (p.status == PlaceStatus.CONFIRMED) R.color.primary else R.color.surface_container,
            if (p.status == PlaceStatus.CONFIRMED) R.color.on_primary else R.color.text_sub)
        if (p.isReservationRequired) badge(getString(R.string.reservation_required), R.color.error_container, R.color.on_error_container)

        // 지도 버튼: 국내 = 카카오맵 공식 아이콘 + 글자, 해외 = 글자만
        val domestic = isDomestic(p, all, lodgingCoords)
        findViewById<TpButton>(R.id.btnOpenMap).apply {
            text = getString(R.string.open_in_map, MapLauncher.mapName(this@PlaceViewActivity, domestic))
            icon = if (domestic) ContextCompat.getDrawable(this@PlaceViewActivity, R.drawable.ic_kakaomap) else null
            setOnClickListener {
                val point = if (p.hasLocation) MapPoint(p.name, p.lat, p.lng) else null
                MapLauncher.openPlace(this@PlaceViewActivity, p.name, point, domestic)
            }
        }
        findViewById<View>(R.id.btnTripMap).apply {
            visibility = if (p.hasLocation) View.VISIBLE else View.GONE
            setOnClickListener {
                setResult(RESULT_OK, Intent().putExtra(RESULT_SHOW_ON_MAP, p.id))
                finish()
            }
        }

        // 운영 정보
        val rows = findViewById<LinearLayout>(R.id.infoRows)
        rows.removeAllViews()
        fun row(icon: Int, label: Int, value: String) {
            val v = LayoutInflater.from(this).inflate(R.layout.item_info_row, rows, false)
            v.findViewById<View>(R.id.divider).visibility = if (rows.childCount == 0) View.GONE else View.VISIBLE
            v.findViewById<ImageView>(R.id.ivIcon).setImageResource(icon)
            v.findViewById<TextView>(R.id.tvLabel).setText(label)
            v.findViewById<TextView>(R.id.tvValue).text = value
            rows.addView(v)
        }
        row(R.drawable.ic_schedule, R.string.info_hours, p.openingHours.replace("~", "–"))
        p.breakTime?.let { row(R.drawable.ic_schedule, R.string.info_break, it.replace("~", "–")) }
        row(R.drawable.ic_event, R.string.info_closed, p.closedDays ?: getString(R.string.closed_none))
        row(R.drawable.ic_schedule, R.string.info_stay, getString(R.string.stay_about, p.stayMinutes))
        val addr = listOf(p.address, p.addressDetail).filter { it.isNotBlank() }.joinToString(" ")
        if (addr.isNotBlank()) row(R.drawable.ic_place, R.string.label_address, addr)
        if (p.locationMemo.isNotBlank()) row(R.drawable.ic_note, R.string.label_location_memo, p.locationMemo)
        if (!p.hasLocation) row(R.drawable.ic_place, R.string.info_location, getString(R.string.no_location_info))
        if (p.memo.isNotBlank()) row(R.drawable.ic_note, R.string.memo_label, p.memo.trim())

        bindLinks()
        refreshFiles()
    }

    /** "확정 · 1일차 2번째" / "확정 · 일차 미정" / "후보" */
    private fun statusText(p: Place, all: List<Place>): String {
        if (p.status != PlaceStatus.CONFIRMED) return getString(R.string.place_status_candidate)
        val day = p.day ?: return getString(R.string.place_status_undated)
        // 여행 상세 타임라인과 같은 번호: 저장된 순서, 없으면 같은 정렬의 몇 번째
        val sameDay = all.filter { it.status == PlaceStatus.CONFIRMED && it.day == day }
            .sortedWith(compareBy<Place>({ it.orderInDay == null }, { it.orderInDay }, { it.id }))
        val order = p.orderInDay ?: (sameDay.indexOfFirst { it.id == p.id } + 1)
        return getString(R.string.place_status_confirmed_order, dayLabel(day), order)
    }

    /** 여행 상세와 같은 기준: 위치가 없으면 같은 여행의 다른 장소로 짐작 */
    private fun isDomestic(p: Place, all: List<Place>, lodgingCoords: List<Pair<Double, Double>>): Boolean {
        if (p.hasLocation) return Region.isKorea(p.lat, p.lng)
        val coords = all.filter { it.hasLocation }.map { it.lat to it.lng } + lodgingCoords
        if (coords.isEmpty()) return true
        return coords.count { (lat, lng) -> Region.isKorea(lat, lng) } * 2 >= coords.size
    }

    /** ⋮ → 수정 / 삭제(빨강) */
    private fun showMenu(anchor: View) {
        val p = place ?: return
        showSelect(anchor, listOf(
            SelectRow.Option(getString(R.string.action_edit)) {
                startActivity(Intent(this, AddPlaceActivity::class.java)
                    .putExtra(TripDetailActivity.EXTRA_TRIP_ID, tripId)
                    .putExtra(AddPlaceActivity.EXTRA_PLACE_ID, p.id)
                    .putExtra(AddPlaceActivity.EXTRA_DAY_COUNT, intent.getIntExtra(AddPlaceActivity.EXTRA_DAY_COUNT, 7)))
            },
            SelectRow.Option(getString(R.string.action_delete), danger = true) {
                confirmDelete(this, p.name, getString(R.string.place_delete_confirm)) {
                    lifecycleScope.launch { repo.deletePlace(p); finish() }
                }
            }
        ), widthDp = 168)
    }
}
