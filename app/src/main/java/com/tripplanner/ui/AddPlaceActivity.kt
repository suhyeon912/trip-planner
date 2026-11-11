package com.tripplanner.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.tripplanner.data.PlaceCategory
import com.tripplanner.route.Region
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tripplanner.R
import com.tripplanner.TripPlannerApp
import com.tripplanner.data.Place
import com.tripplanner.data.PlaceStatus
import com.tripplanner.route.TimeUtil
import kotlinx.coroutines.launch

/**
 * 장소 추가 / 수정 화면 (6단계). EXTRA_PLACE_ID 가 있으면 수정 모드.
 * 저장되는 값과 검사 조건은 이전과 같고, 오류는 해당 입력칸 아래에 표시합니다.
 */
class AddPlaceActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLACE_ID = "place_id"
        const val EXTRA_DAY_COUNT = "day_count"
    }

    private val repo by lazy { (application as TripPlannerApp).repository }
    private var editing: Place? = null
    private val noDay by lazy { getString(R.string.day_none) }

    private lateinit var tilName: TextInputLayout
    private lateinit var tilLat: TextInputLayout
    private lateinit var tilLng: TextInputLayout
    private lateinit var tilDay: TextInputLayout
    private lateinit var actDay: MaterialAutoCompleteTextView
    private lateinit var toggle: MaterialButtonToggleGroup

    private fun field(id: Int) = findViewById<TextInputEditText>(id)

    // 새 기능: 장소 종류 · 사진 · 검색
    private var category = PlaceCategory.SIGHT
    private var photoPath: String? = null
    private var tripDomestic = true
    private lateinit var chips: ChipGroup
    private lateinit var ivPhoto: ImageView
    private lateinit var btnPhoto: Button
    private lateinit var btnRemovePhoto: Button

    /** 사진 고르기 (Android 사진 선택기 — 저장소 권한 필요 없음) */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) { Photos.importPhoto(this@AddPlaceActivity, uri) }
            if (path == null) Toast.makeText(this@AddPlaceActivity, R.string.photo_failed, Toast.LENGTH_SHORT).show()
            else { photoPath = path; refreshPhoto() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("category", category.name)
        outState.putString("photo", photoPath)
    }

    private fun refreshPhoto() {
        Thumbs.bind(ivPhoto, photoPath, category)
        btnPhoto.setText(if (photoPath != null) R.string.change_photo else R.string.pick_photo)
        btnRemovePhoto.visibility = if (photoPath != null) View.VISIBLE else View.GONE
    }

    private fun selectCategory(c: PlaceCategory) {
        category = c
        chips.check(1000 + c.ordinal)
        refreshPhoto()
    }

    /** 검색 결과로 칸 채우기: 이름·위치·종류는 덮어쓰고, 운영시간은 비어 있을 때만 */
    private fun applySearchHit(hit: SearchHit) {
        field(R.id.etName).setText(hit.name)
        field(R.id.etLat).setText(hit.lat.toString())
        field(R.id.etLng).setText(hit.lng.toString())
        selectCategory(hit.category)
        val hours = field(R.id.etHours)
        val cur = hours.text?.toString()?.trim().orEmpty()
        if (hit.hours != null && (cur.isEmpty() || cur == getString(R.string.hours_unknown))) hours.setText(hit.hours)
        Toast.makeText(this, getString(R.string.search_filled, hit.name), Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_place)

        val tripId = intent.getLongExtra(TripDetailActivity.EXTRA_TRIP_ID, -1)
        val placeId = intent.getLongExtra(EXTRA_PLACE_ID, -1)
        val dayCount = intent.getIntExtra(EXTRA_DAY_COUNT, 7)

        val toolbar = setupToolbar()
        limitScrollContentWidth(R.id.scroll)

        // ---------- 장소 종류 칩 ----------
        chips = findViewById(R.id.chipsCategory)
        PlaceCategory.entries.forEach { c ->
            chips.addView(Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                id = 1000 + c.ordinal
                text = c.label
                isCheckable = true
                setChipIconResource(c.icon)
                isChipIconVisible = true
                chipIconTint = android.content.res.ColorStateList.valueOf(color(c.fgColor))
                setEnsureMinTouchTargetSize(true)
            })
        }
        chips.setOnCheckedStateChangeListener { _, ids ->
            ids.firstOrNull()?.let { id -> PlaceCategory.entries.getOrNull(id - 1000)?.let { category = it; refreshPhoto() } }
        }
        // ---------- 사진 ----------
        ivPhoto = findViewById(R.id.ivPhoto)
        btnPhoto = findViewById(R.id.btnPhoto)
        btnRemovePhoto = findViewById(R.id.btnRemovePhoto)
        btnPhoto.setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        btnRemovePhoto.setOnClickListener { photoPath = null; refreshPhoto() }
        savedInstanceState?.let { st ->
            category = st.getString("category")?.let { runCatching { PlaceCategory.valueOf(it) }.getOrNull() } ?: category
            photoPath = st.getString("photo")
        }
        selectCategory(category)
        // ---------- 장소 검색 (여행 장소·숙소가 모두 국내면 카카오 먼저) ----------
        lifecycleScope.launch {
            val coords = repo.getPlaces(tripId).filter { it.hasLocation }.map { it.lat to it.lng } +
                repo.getLodgings(tripId).filter { it.lat != 0.0 || it.lng != 0.0 }.map { it.lat to it.lng }
            tripDomestic = coords.isEmpty() || coords.all { (la, ln) -> Region.isKorea(la, ln) }
        }
        val openSearch = { showPlaceSearch(this, field(R.id.etName).text?.toString().orEmpty(), tripDomestic) { applySearchHit(it) } }
        findViewById<Button>(R.id.btnSearchPlace).setOnClickListener { openSearch() }
        findViewById<TextInputLayout>(R.id.tilName).setEndIconOnClickListener { openSearch() }
        tilName = findViewById(R.id.tilName)
        tilLat = findViewById(R.id.tilLat)
        tilLng = findViewById(R.id.tilLng)
        tilDay = findViewById(R.id.tilDay)
        actDay = findViewById(R.id.actDay)
        toggle = findViewById(R.id.toggleStatus)
        val switchReservation = findViewById<MaterialSwitch>(R.id.switchReservation)

        // 오류 표시는 다시 입력하면 지움
        listOf(tilName to R.id.etName, tilLat to R.id.etLat, tilLng to R.id.etLng).forEach { (til, id) ->
            field(id).doAfterTextChanged { til.error = null }
        }

        // 며칠째 드롭다운: "일차 미정", "1일차 · 2/12(금)" … (날짜는 여행 시작일로 계산)
        setDayOptions((1..dayCount).map { dayLabel(it) })
        lifecycleScope.launch {
            val start = repo.getTrip(tripId)?.startDate ?: return@launch
            setDayOptions((1..dayCount).map { d -> dayWithDate(d, TimeUtil.dateOfDay(start, d)) })
            // 수정 모드에서 미리 고른 일차 라벨을 날짜 포함 라벨로 맞춤
            editing?.day?.let { if (savedInstanceState == null) selectDay(it) }
        }

        // 후보면 며칠째 비활성
        fun updateDayEnabled() {
            val confirmed = toggle.checkedButtonId == R.id.btnConfirmed
            tilDay.isEnabled = confirmed
            tilDay.helperText = if (confirmed) null else getString(R.string.helper_candidate_day)
        }
        toggle.addOnButtonCheckedListener { _, _, _ -> updateDayEnabled() }
        updateDayEnabled()

        // 수정 모드면 기존 값 채우기 (회전 후 재생성 때는 입력칸이 스스로 값을 복원하므로 건너뜀)
        if (placeId != -1L) {
            toolbar.setTitle(R.string.edit_place)
            lifecycleScope.launch {
                val p = repo.getPlace(placeId) ?: return@launch
                editing = p
                if (savedInstanceState != null) return@launch
                field(R.id.etName).setText(p.name)
                field(R.id.etHours).setText(p.openingHours)
                field(R.id.etBreak).setText(p.breakTime ?: "")
                field(R.id.etClosed).setText(p.closedDays ?: "")
                switchReservation.isChecked = p.isReservationRequired
                field(R.id.etStay).setText(p.stayMinutes.toString())
                field(R.id.etMemo).setText(p.memo)
                photoPath = p.photoUri
                selectCategory(p.category)
                if (p.hasLocation) { field(R.id.etLat).setText(p.lat.toString()); field(R.id.etLng).setText(p.lng.toString()) }
                toggle.check(if (p.status == PlaceStatus.CONFIRMED) R.id.btnConfirmed else R.id.btnCandidate)
                if (p.day != null) selectDay(p.day) else actDay.setText(noDay, false)
            }
        }

        val save = { save(tripId, dayCount, switchReservation.isChecked) }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        toolbar.setOnMenuItemClickListener { if (it.itemId == R.id.action_save) save(); true }
    }

    private var dayLabels: List<String> = emptyList()

    private fun setDayOptions(labels: List<String>) {
        dayLabels = labels
        val current = currentDay()
        actDay.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf(noDay) + labels))
        if (current != null) selectDay(current) else if (actDay.text.isNullOrEmpty()) actDay.setText(noDay, false)
    }

    private fun selectDay(day: Int) {
        dayLabels.getOrNull(day - 1)?.let { actDay.setText(it, false) }
    }

    /** 드롭다운 글자 "3일차 · …" → 3, "일차 미정" → null (회전 후에도 글자로 복원 가능). 앞쪽 숫자만 읽음 */
    private fun currentDay(): Int? = actDay.text?.toString()?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()

    private fun save(tripId: Long, dayCount: Int, reservation: Boolean) {
        val name = field(R.id.etName).text.toString().trim()
        val latText = field(R.id.etLat).text.toString()
        val lngText = field(R.id.etLng).text.toString()
        val lat = latText.toDoubleOrNull()
        val lng = lngText.toDoubleOrNull()
        val day = currentDay()?.takeIf { it in 1..dayCount }

        // 검사 (조건은 이전과 같음) → 해당 칸에 오류 표시, 첫 오류 칸으로 이동
        val errors = mutableListOf<TextInputLayout>()
        if (name.isBlank()) { tilName.error = getString(R.string.error_place_name); errors += tilName }
        if (lat != null && lat !in -90.0..90.0) { tilLat.error = getString(R.string.error_lat); errors += tilLat }
        if (lng != null && lng !in -180.0..180.0) { tilLng.error = getString(R.string.error_lng); errors += tilLng }
        if (errors.isNotEmpty()) {
            val first = errors.first()
            findViewById<NestedScrollView>(R.id.scroll).smoothScrollTo(0, first.top)
            first.editText?.requestFocus()
            return
        }

        val confirmed = toggle.checkedButtonId == R.id.btnConfirmed
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
            lat = lat ?: 0.0,
            lng = lng ?: 0.0,
            day = if (confirmed) day else null,
            // 일차가 그대로면 기존 방문 순서 유지
            orderInDay = if (confirmed && old?.day == day) old?.orderInDay else null,
            stayMinutes = field(R.id.etStay).text.toString().toIntOrNull()?.coerceIn(10, 720) ?: 60,
            category = category,
            photoUri = photoPath
        )
        // 사진을 바꾸거나 뺐으면 예전에 복사해 둔 사진 파일 정리
        if (old?.photoUri != null && old.photoUri != photoPath) Photos.delete(this, old.photoUri)
        lifecycleScope.launch {
            repo.savePlace(place)
            finish()
        }
    }
}
