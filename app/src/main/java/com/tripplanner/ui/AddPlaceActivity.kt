package com.tripplanner.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.tripplanner.ui.widget.TpSwitch
import android.widget.EditText
import com.tripplanner.ui.widget.TpTextField
import com.tripplanner.R
import com.tripplanner.TripPlannerApp
import com.tripplanner.data.Place
import com.tripplanner.data.PlaceCategory
import com.tripplanner.data.PlaceStatus
import com.tripplanner.route.Region
import com.tripplanner.route.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 장소 추가 / 수정 (시안 AddPlace, 전체 화면). EXTRA_PLACE_ID 가 있으면 수정 모드.
 * 저장되는 값과 검사 조건은 이전과 같습니다 (이름 필수, 위도 -90~90, 경도 -180~180).
 * 위치는 위도·경도 칸 대신 '검색해서 고르기', 키가 없을 때를 위해 직접 입력도 남겨 둠.
 */
class AddPlaceActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLACE_ID = "place_id"
        const val EXTRA_DAY_COUNT = "day_count"
    }

    private val repo by lazy { (application as TripPlannerApp).repository }
    private val tripId by lazy { intent.getLongExtra(TripDetailActivity.EXTRA_TRIP_ID, -1) }
    private val dayCount by lazy { intent.getIntExtra(EXTRA_DAY_COUNT, 7) }
    private var editing: Place? = null

    // 화면 상태 (회전해도 유지)
    private var category = PlaceCategory.SIGHT
    /** 내가 만든 종류를 골랐으면 그 id (기본 종류면 null) */
    private var typeId: Long? = null
    /** 검색으로 채운 Google 장소 id ('다시 가져오기'용) */
    private var sourceId = ""
    /** 칩 id 2000+n ↔ 내가 만든 종류 id */
    private var customChipTypes: List<Long> = emptyList()
    private var photoPath: String? = null
    private var day: Int? = null
    private var lat = 0.0
    private var lng = 0.0
    /** 지금 위도·경도가 가리키는 주소 (검색 결과·불러온 값). 주소 칸을 이것과 다르게 고치면 저장할 때 다시 찾음 */
    private var coordsAddress: String? = null
    private var tripDomestic = true
    private var startDate: String? = null
    private var dayCounts: Map<Int, Int> = emptyMap()

    private lateinit var chips: com.tripplanner.ui.widget.TpChipGroup
    private lateinit var ivPhoto: ImageView
    private lateinit var toggle: SegmentedGroup
    private lateinit var fieldDay: SelectField
    private lateinit var tilName: TpTextField

    private fun field(id: Int) = findViewById<EditText>(id)

    /** 사진 고르기 (Android 사진 선택기 — 저장소 권한 필요 없음) */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) { Photos.importPhoto(this@AddPlaceActivity, uri) }
            if (path == null) showSnack(getString(R.string.photo_failed))
            else { photoPath = path; refreshPhoto() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_place)
        val placeId = intent.getLongExtra(EXTRA_PLACE_ID, -1)

        val toolbar = setupToolbar(getString(if (placeId == -1L) R.string.add_place else R.string.edit_place), showBack = false)
        toolbar.setNavigationIcon(R.drawable.ic_close)
        toolbar.setNavigationContentDescription(R.string.action_close)
        toolbar.setNavigationOnClickListener { finish() }
        limitScrollContentWidth(R.id.scroll)

        tilName = findViewById(R.id.tilName)
        toggle = findViewById(R.id.toggleStatus)
        if (toggle.checkedButtonId == View.NO_ID) toggle.check(R.id.btnCandidate)
        fieldDay = findViewById(R.id.fieldDay)
        fieldDay.setCompact()
        ivPhoto = findViewById(R.id.ivPhoto)
        val switchReservation = findViewById<TpSwitch>(R.id.switchReservation)
        field(R.id.etName).doAfterTextChanged { tilName.error = null }

        // ---------- 종류 칩: 바탕색 = 종류, 고르면 테두리 2 + ✓ ----------
        chips = findViewById(R.id.chipsCategory)
        buildCategoryChips(null)
        refreshStayHint()
        findViewById<View>(R.id.btnCategoryInfo).setOnClickListener { showCategoryInfo(it) }
        chips.onCheckedChange = { id ->
            if (id >= 2000) customChipTypes.getOrNull(id - 2000)?.let { typeId = it; category = PlaceCategory.ETC; refreshPhoto(); refreshStayHint() }
            else PlaceCategory.entries.getOrNull(id - 1000)?.let { typeId = null; category = it; refreshPhoto(); refreshStayHint() }
        }
        findViewById<View>(R.id.btnRefreshHours).setOnClickListener { refreshHours(it) }


        // ---------- 사진: 비었으면 고르기, 있으면 바꾸기 / 빼기 ----------
        findViewById<View>(R.id.photoBox).setOnClickListener { v ->
            if (photoPath == null) pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            else showSelect(v, listOf(
                SelectRow.Option(getString(R.string.change_photo)) {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                SelectRow.Option(getString(R.string.remove_photo), danger = true) { photoPath = null; refreshPhoto() }
            ), widthDp = 168)
        }

        // ---------- 검색 (여행 장소·숙소가 모두 국내면 카카오 먼저) ----------
        findViewById<View>(R.id.btnSearchPlace).setOnClickListener {
            showPlaceSearch(this, field(R.id.etName).text?.toString().orEmpty(), tripDomestic) { applySearchHit(it) }
        }

        // ---------- 일차 select · 후보면 일차 칸 끔 ----------
        fieldDay.label = getString(R.string.label_day)
        fieldDay.setIcon(R.drawable.ic_event)
        fieldDay.setDropdown(true)
        fieldDay.setOnClickListener { pickDay(it) }
        toggle.addOnButtonCheckedListener { _, _, _ -> render() }

        // ---------- 위치: 주소(검색 또는 직접) · 상세 주소 · 위치 메모 ----------
        findViewById<TpTextField>(R.id.tilAddress).setEndIconOnClickListener {
            val q = field(R.id.etAddress).text?.toString()?.takeIf { it.isNotBlank() } ?: field(R.id.etName).text?.toString().orEmpty()
            showPlaceSearch(this, q, tripDomestic) { hit ->
                lat = hit.lat; lng = hit.lng
                val addr = hit.address.ifBlank { hit.name }
                coordsAddress = addr
                field(R.id.etAddress).setText(addr)
                render()
            }
        }
        field(R.id.etAddress).doAfterTextChanged { renderLocState() }
        findViewById<View>(R.id.btnLatLng).setOnClickListener { manualLatLng() }

        findViewById<View>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSave).setOnClickListener { save(switchReservation.isChecked) }

        savedInstanceState?.let { st ->
            category = st.getString("category")?.let { runCatching { PlaceCategory.valueOf(it) }.getOrNull() } ?: category
            typeId = st.getLong("typeId", 0L).takeIf { it > 0 }
            sourceId = st.getString("sourceId").orEmpty()
            photoPath = st.getString("photo")
            day = st.getInt("day", 0).takeIf { it > 0 }
            lat = st.getDouble("lat"); lng = st.getDouble("lng"); coordsAddress = st.getString("coordsAddress")
        }
        selectKind(category, typeId)
        render()

        lifecycleScope.launch {
            val places = repo.getPlaces(tripId)
            val coords = places.filter { it.hasLocation }.map { it.lat to it.lng } +
                repo.getLodgings(tripId).filter { it.lat != 0.0 || it.lng != 0.0 }.map { it.lat to it.lng }
            tripDomestic = coords.isEmpty() || coords.all { (la, ln) -> Region.isKorea(la, ln) }
            startDate = repo.getTrip(tripId)?.startDate
            dayCounts = places.filter { it.status == PlaceStatus.CONFIRMED && it.id != placeId }
                .mapNotNull { it.day }.groupingBy { it }.eachCount()
            // 수정 모드면 기존 값 채우기 (회전 후 재생성 때는 입력칸이 스스로 값을 복원하므로 건너뜀)
            if (placeId != -1L) {
                val p = repo.getPlace(placeId) ?: return@launch
                editing = p
                if (savedInstanceState == null) {
                    field(R.id.etName).setText(p.name)
                    field(R.id.etHours).setText(p.openingHours)
                    field(R.id.etBreak).setText(p.breakTime ?: "")
                    field(R.id.etClosed).setText(p.closedDays ?: "")
                    switchReservation.isChecked = p.isReservationRequired
                    field(R.id.etStay).setText(p.stayMinutes.toString())
                    field(R.id.etMemo).setText(p.memo)
                    photoPath = p.photoUri
                    lat = p.lat; lng = p.lng
                    field(R.id.etAddress).setText(p.address)
                    field(R.id.etAddressDetail).setText(p.addressDetail)
                    field(R.id.etLocationMemo).setText(p.locationMemo)
                    coordsAddress = p.address
                    day = p.day
                    sourceId = p.sourceId
                    selectKind(p.category, p.typeId)
                    toggle.check(if (p.status == PlaceStatus.CONFIRMED) R.id.btnConfirmed else R.id.btnCandidate)
                }
            }
            render()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("category", category.name)
        outState.putLong("typeId", typeId ?: 0L)
        outState.putString("sourceId", sourceId)
        outState.putString("photo", photoPath)
        outState.putInt("day", day ?: 0)
        outState.putDouble("lat", lat); outState.putDouble("lng", lng)
        outState.putString("coordsAddress", coordsAddress)
    }

    /**
     * 종류 칩 만들기: 설정에서 숨긴 종류는 빼고 (수정 중인 장소의 종류는 보임).
     * 모두 숨기면 칩 줄을 숨기고 '기타'로 저장.
     */
    private fun buildCategoryChips(current: PlaceCategory?) {
        chips.removeAllViews()
        val visible = AppPrefs.visibleCategories(AppPrefs(this).hiddenCategories, current)
        // 내가 만든 종류 (설정 › 장소 종류) 는 기본 종류 뒤에
        val custom = PlaceKinds.types.values.sortedBy { it.id }
        customChipTypes = custom.map { it.id }
        val label = (chips.parent as android.view.ViewGroup).getChildAt((chips.parent as android.view.ViewGroup).indexOfChild(chips) - 1)
        chips.visibility = if (visible.isEmpty() && custom.isEmpty()) View.GONE else View.VISIBLE
        label.visibility = chips.visibility
        if (typeId != null && typeId !in customChipTypes) typeId = null
        if (visible.isEmpty() && custom.isEmpty()) { category = PlaceCategory.ETC; return }
        if (typeId == null && category !in visible) {
            if (visible.isNotEmpty()) category = visible.first() else { typeId = custom.first().id; category = PlaceCategory.ETC }
        }
        visible.forEach { c -> chips.addView(categoryChip(1000 + c.ordinal, c.label, c.icon, c.bgColor)) }
        custom.forEachIndexed { i, t -> chips.addView(categoryChip(2000 + i, t.name, TypeIcons.res(t.icon), PlaceKinds.colorRes(t.color))) }
    }

    private fun kindChipId() = typeId?.let { t -> customChipTypes.indexOf(t).takeIf { it >= 0 }?.let { 2000 + it } } ?: (1000 + category.ordinal)

    /** 종류 칩 (시안): 높이 36 · 모서리 10 · 바탕 = 종류 색 · 고르면 테두리 2 + 아이콘 자리에 ✓ + 굵게 */
    private fun categoryChip(chipId: Int, label: String, iconRes: Int, bgRes: Int): com.tripplanner.ui.widget.TpButton {
        val d = resources.displayMetrics.density
        fun bg(checked: Boolean) = android.graphics.drawable.GradientDrawable().apply {
            setColor(color(bgRes)); cornerRadius = 10 * d
            if (checked) setStroke((2 * d).toInt(), color(R.color.on_surface))
        }
        return object : com.tripplanner.ui.widget.TpButton(this@AddPlaceActivity, null, 0) {
            override fun setChecked(checked: Boolean) {
                super.setChecked(checked)
                background = bg(checked)
                setIconResource(if (checked) R.drawable.ic_check else iconRes)
                typeface = androidx.core.content.res.ResourcesCompat.getFont(context,
                    if (checked) R.font.pretendard_bold else R.font.pretendard_semibold)
            }
        }.apply {
            id = chipId
            text = label
            isCheckable = true
            setTextAppearance(R.style.TextAppearance_TripPlanner_LabelLarge)
            setTextColor(color(R.color.on_surface))
            gravity = android.view.Gravity.CENTER
            minHeight = (36 * d).toInt(); minimumHeight = minHeight; minWidth = 0; minimumWidth = 0
            setPaddingRelative((8 * d).toInt(), 0, (12 * d).toInt(), 0)
            iconSize = (18 * d).toInt(); iconPadding = (6 * d).toInt()
            iconTint = ColorStateList.valueOf(color(R.color.on_surface))
            setIconResource(iconRes)
            background = bg(false)
            foreground = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.press_r10)
            stateListAnimator = null
            isClickable = true; isFocusable = true
        }
    }

    private fun refreshPhoto() {
        val has = photoPath != null
        ivPhoto.visibility = if (has) View.VISIBLE else View.GONE
        findViewById<View>(R.id.photoEmpty).visibility = if (has) View.GONE else View.VISIBLE
        if (has) {
            val t = typeId?.let { PlaceKinds.types[it] }
            Thumbs.bind(ivPhoto, photoPath, t?.let { TypeIcons.res(it.icon) } ?: category.icon, t?.let { PlaceKinds.colorRes(it.color) } ?: category.bgColor)
        }
        findViewById<View>(R.id.photoBox).contentDescription = getString(if (has) R.string.change_photo else R.string.pick_photo)
    }

    private fun selectCategory(c: PlaceCategory) = selectKind(c, null)

    /** 종류 고르기: 내가 만든 종류(typeId)가 있으면 그것, 없으면 기본 종류 */
    private fun selectKind(c: PlaceCategory, type: Long?) {
        typeId = type?.takeIf { it in PlaceKinds.types }
        category = c
        // 숨긴 종류여도 이 장소의 종류면 칩을 보여 줌
        if (chips.findViewById<View>(kindChipId()) == null) buildCategoryChips(if (typeId == null) c else null)
        if (chips.visibility == View.VISIBLE) chips.check(kindChipId())
        refreshPhoto()
        refreshStayHint()
    }

    /** 종류 ⓘ: 아이콘 아래 어두운 안내 상자 (시안 AddPlaceInfo) */
    private fun showCategoryInfo(anchor: View) {
        val d = resources.displayMetrics.density
        val box = layoutInflater.inflate(R.layout.view_tooltip, null)
        box.findViewById<TextView>(R.id.tvTipTitle).setText(R.string.category_info_title)
        box.findViewById<TextView>(R.id.tvTipBody).setText(R.string.category_info_body)
        val popup = android.widget.PopupWindow(box, (300 * d).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.elevation = 8 * d
        popup.showAsDropDown(anchor, 0, (-4 * d).toInt(), android.view.Gravity.END)
    }

    /** 머무는 시간을 비워 두면 종류별 기본값 (관광 90 · 맛집 60 · 카페 60 · 쇼핑 120 · 체험 120 · 기타 60) */
    private fun refreshStayHint() {
        findViewById<TpTextField>(R.id.tilStay).placeholderText = category.defaultStay.toString()
    }

    private fun confirmed() = toggle.checkedButtonId == R.id.btnConfirmed

    private fun dayText(d: Int): String {
        val date = startDate?.let { TimeUtil.dateOfDay(it, d) }
        return dayLabel(d) + (date?.let { " · ${it.monthValue}.${it.dayOfMonth} (${TimeUtil.koreanDayName(it).first()})" } ?: "")
    }

    private fun render() {
        // 후보는 일차 없이 저장 → 일차 칸 흐리게, 누를 수 없음
        val on = confirmed()
        fieldDay.alpha = if (on) 1f else 0.38f
        fieldDay.isEnabled = on
        fieldDay.findViewById<View>(R.id.selectBox).isEnabled = on
        fieldDay.setValue(if (!on) getString(R.string.day_none) else day?.let { dayLabel(it) } ?: getString(R.string.day_none))
        renderLocState()
        refreshPhoto()
    }

    /** 일차 select: ● n일차 · 11.20 (금) · n곳 … / ○ 일차 미정 */
    private fun pickDay(anchor: View) {
        if (!confirmed()) return
        val rows = (1..dayCount).map { d ->
            SelectRow.Option(dayText(d) + getString(R.string.list_separator) + getString(R.string.places_count, dayCounts[d] ?: 0),
                selected = day == d, dotColor = PinIcons.dayColor(d)) { day = d; render() }
        } + SelectRow.Option(getString(R.string.day_none), selected = day == null, hollowDot = true) { day = null; render() }
        showSelect(anchor, rows, widthDp = 240)
    }

    /** 주소 칸 아래 한 줄: 위치 있음 / 위치 없음 / 저장할 때 주소로 찾음 */
    private fun renderLocState() {
        val addr = field(R.id.etAddress).text?.toString()?.trim().orEmpty()
        val changed = addr.isNotEmpty() && addr != coordsAddress?.trim()
        findViewById<android.widget.TextView>(R.id.tvLocState).setText(when {
            changed -> R.string.loc_state_pending
            lat != 0.0 || lng != 0.0 -> R.string.loc_state_set
            else -> R.string.loc_state_none
        })
    }

    /** 검색 키 · 지오코더가 없을 때를 위해 위도·경도 직접 입력 (검사 조건은 이전과 같음) */
    private fun manualLatLng() {
        showFormDialog(this, getString(R.string.location_manual), listOf(
            Field(getString(R.string.hint_lat), lat.takeIf { it != 0.0 }?.toString() ?: "", DECIMAL_INPUT),
            Field(getString(R.string.hint_lng), lng.takeIf { it != 0.0 }?.toString() ?: "", DECIMAL_INPUT)
        )) { (a, b) ->
            val la = a.toDoubleOrNull(); val ln = b.toDoubleOrNull()
            when {
                la != null && la !in -90.0..90.0 -> { Toast.makeText(this, R.string.error_lat, Toast.LENGTH_SHORT).show(); false }
                ln != null && ln !in -180.0..180.0 -> { Toast.makeText(this, R.string.error_lng, Toast.LENGTH_SHORT).show(); false }
                else -> {
                    lat = la ?: 0.0; lng = ln ?: 0.0
                    // 직접 넣은 위치는 지금 주소 칸 값과 짝으로 봄 (저장할 때 다시 찾지 않음)
                    coordsAddress = field(R.id.etAddress).text?.toString()
                    render(); true
                }
            }
        }
    }

    /** 검색 결과로 칸 채우기: 이름·위치·종류는 덮어쓰고, 운영시간은 비어 있을 때만 */
    private fun applySearchHit(hit: SearchHit) {
        field(R.id.etName).setText(hit.name)
        lat = hit.lat; lng = hit.lng
        coordsAddress = hit.address
        field(R.id.etAddress).setText(hit.address)
        selectCategory(hit.category)
        sourceId = hit.sourceId
        val hours = field(R.id.etHours)
        val cur = hours.text?.toString()?.trim().orEmpty()
        if (hit.hours != null && (cur.isEmpty() || cur == getString(R.string.hours_unknown))) hours.setText(hit.hours)
        render()
        showSnack(getString(R.string.search_filled, hit.name))
    }

    /**
     * 운영 정보 다시 가져오기: 검색으로 채운 장소면 그 장소, 아니면 이름(+주소)으로 Google 에서 찾아
     * 운영시간 · 브레이크 타임 · 휴무일 칸을 새 값으로 바꿈 (모르는 칸은 그대로).
     */
    private fun refreshHours(button: View) {
        val name = field(R.id.etName).text?.toString()?.trim().orEmpty()
        if (name.isEmpty() && sourceId.isEmpty()) { tilName.error = getString(R.string.error_place_name); return }
        button.isEnabled = false
        lifecycleScope.launch {
            val result = runCatching {
                PlaceSearch.refreshHours(this@AddPlaceActivity, sourceId, name, field(R.id.etAddress).text?.toString()?.trim().orEmpty())
            }
            button.isEnabled = true
            val info = result.getOrNull()
            when {
                result.exceptionOrNull() is PlaceSearch.NoKeyException ->
                    showSnack(getString((result.exceptionOrNull() as PlaceSearch.NoKeyException).messageRes))
                result.isFailure -> showSnack(getString(R.string.refresh_hours_failed))
                info == null || (info.hours == null && info.breakTime == null && info.closedDays == null) ->
                    showSnack(getString(R.string.refresh_hours_none))
                else -> {
                    sourceId = info.sourceId
                    info.hours?.let { field(R.id.etHours).setText(it) }
                    field(R.id.etBreak).setText(info.breakTime ?: "")
                    field(R.id.etClosed).setText(info.closedDays ?: getString(R.string.closed_none))
                    Haptics.confirm(button)
                    showSnack(getString(R.string.refresh_hours_done))
                }
            }
        }
    }

    private fun save(reservation: Boolean) {
        val name = field(R.id.etName).text.toString().trim()
        if (name.isBlank()) {
            tilName.error = getString(R.string.error_place_name)
            findViewById<NestedScrollView>(R.id.scroll).smoothScrollTo(0, 0)
            field(R.id.etName).requestFocus()
            return
        }
        val confirmed = confirmed()
        val d = day?.takeIf { it in 1..dayCount }
        val old = editing
        val place = Place(
            id = old?.id ?: 0,
            tripId = tripId,
            name = name,
            status = if (confirmed) PlaceStatus.CONFIRMED else PlaceStatus.CANDIDATE,
            openingHours = field(R.id.etHours).text.toString().trim().ifBlank { getString(R.string.hours_unknown) },
            breakTime = field(R.id.etBreak).text.toString().trim().ifBlank { null },
            closedDays = field(R.id.etClosed).text.toString().trim().ifBlank { null },
            isReservationRequired = reservation,
            memo = field(R.id.etMemo).text.toString().trim(),
            lat = lat,
            lng = lng,
            address = field(R.id.etAddress).text.toString().trim(),
            addressDetail = field(R.id.etAddressDetail).text.toString().trim(),
            locationMemo = field(R.id.etLocationMemo).text.toString().trim(),
            day = if (confirmed) d else null,
            // 일차가 그대로면 기존 방문 순서 유지
            orderInDay = if (confirmed && old?.day == d) old?.orderInDay else null,
            stayMinutes = field(R.id.etStay).text.toString().toIntOrNull()?.coerceIn(10, 720) ?: category.defaultStay,
            category = category,
            photoUri = photoPath,
            typeId = typeId,
            sourceId = sourceId,
            links = old?.links ?: ""
        )
        // 사진을 바꾸거나 뺐으면 예전에 복사해 둔 사진 파일 정리
        if (old?.photoUri != null && old.photoUri != photoPath) Photos.delete(this, old.photoUri)
        lifecycleScope.launch {
            // 주소를 직접 바꿨으면 그 주소로 위치 찾기 (못 찾으면 위치 없음, 기기에 기능이 없으면 그대로)
            val needs = place.address.isNotEmpty() && place.address != coordsAddress?.trim()
            val result = if (needs) Geocoding.find(this@AddPlaceActivity, place.address) else null
            val (la, ln) = Geocoding.resolve(place.address, coordsAddress, place.lat, place.lng, result)
            if (result == Geocoding.Result.NotFound)
                Toast.makeText(this@AddPlaceActivity, R.string.geocode_not_found, Toast.LENGTH_LONG).show()
            repo.savePlace(place.copy(lat = la, lng = ln))
            finish()
        }
    }
}
