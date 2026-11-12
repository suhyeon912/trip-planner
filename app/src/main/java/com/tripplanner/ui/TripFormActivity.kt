package com.tripplanner.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import com.tripplanner.ui.widget.TpButton
import android.widget.EditText
import com.tripplanner.ui.widget.TpTextField
import com.tripplanner.R
import com.tripplanner.TripPlannerApp
import com.tripplanner.data.Trip
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 새 여행 · 여행 수정 (시안 NewTrip · TripEdit, 전체 화면).
 * EXTRA_TRIP_ID 가 있으면 수정. 저장 규칙은 기존 팝업과 같음:
 * 이름 필수, 기간은 달력으로 고르고 종료일이 시작일보다 빠르면 안 됨.
 * 오류는 토스트 대신 칸 아래 빨간 글자로 보여 줌.
 */
class TripFormActivity : AppCompatActivity() {

    private val repo by lazy { (application as TripPlannerApp).repository }
    private var trip: Trip? = null
    private var start = ""
    private var end = ""
    // 표지 사진 (기기에만 저장)
    private var cover: String? = null
    private var coverY = 0.5f
    private var coverScale = 1f
    private var cameraFile: java.io.File? = null

    private val pickCover = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) importCover(uri)
    }
    private val takeCover = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) importCover(android.net.Uri.fromFile(f))
    }
    private val adjustCover = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val d = r.data ?: return@registerForActivityResult
        if (r.resultCode != RESULT_OK) return@registerForActivityResult
        coverY = d.getFloatExtra(CoverCropActivity.EXTRA_Y, coverY)
        coverScale = d.getFloatExtra(CoverCropActivity.EXTRA_SCALE, coverScale)
        showCover()
    }

    private lateinit var layoutName: TpTextField
    private lateinit var inputName: EditText
    private lateinit var layoutPeriod: TpTextField
    private lateinit var inputPeriod: EditText
    private lateinit var tvNights: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_form)
        val tripId = intent.getLongExtra(TripDetailActivity.EXTRA_TRIP_ID, 0L)
        val toolbar = setupToolbar(getString(if (tripId == 0L) R.string.new_trip else R.string.edit_trip), showBack = false)
        // 입력 화면은 닫기(×)
        toolbar.setNavigationIcon(R.drawable.ic_close)
        toolbar.setNavigationContentDescription(R.string.action_close)
        toolbar.setNavigationOnClickListener { finish() }
        limitScrollContentWidth(R.id.scroll)

        layoutName = findViewById(R.id.layoutName)
        inputName = findViewById(R.id.inputName)
        layoutPeriod = findViewById(R.id.layoutPeriod)
        inputPeriod = findViewById(R.id.inputPeriod)
        tvNights = findViewById(R.id.tvNights)

        inputName.doAfterTextChanged { if (!it.isNullOrBlank()) layoutName.error = null }
        inputPeriod.setOnClickListener { pickPeriod() }

        findViewById<View>(R.id.btnCoverUpload).setOnClickListener { chooseCover(it) }
        findViewById<View>(R.id.btnCoverChange).setOnClickListener { chooseCover(it) }
        findViewById<View>(R.id.btnCoverRemove).setOnClickListener { cover = null; coverY = 0.5f; coverScale = 1f; showCover() }
        findViewById<View>(R.id.btnCoverAdjust).setOnClickListener { openAdjust() }

        findViewById<TpButton>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<TpButton>(R.id.btnSave).apply {
            setText(if (tripId == 0L) R.string.action_add else R.string.action_save)
            setOnClickListener { save() }
        }

        if (savedInstanceState != null) {
            start = savedInstanceState.getString("start", "")
            end = savedInstanceState.getString("end", "")
            cover = savedInstanceState.getString("cover")
            coverY = savedInstanceState.getFloat("coverY", 0.5f)
            coverScale = savedInstanceState.getFloat("coverScale", 1f)
            cameraFile = savedInstanceState.getString("camera")?.let { java.io.File(it) }
            showPeriod()
        }
        showCover()
        if (tripId != 0L) lifecycleScope.launch {
            trip = repo.getTrip(tripId) ?: run { finish(); return@launch }
            if (savedInstanceState == null) {
                trip!!.let {
                    inputName.setText(it.title); start = it.startDate; end = it.endDate
                    cover = it.coverPhoto; coverY = it.coverY; coverScale = it.coverScale
                }
                showPeriod(); showCover()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("start", start); outState.putString("end", end)
        outState.putString("cover", cover); outState.putFloat("coverY", coverY); outState.putFloat("coverScale", coverScale)
        outState.putString("camera", cameraFile?.absolutePath)
    }

    /** 사진 업로드 · 바꾸기: 사진 보관함 / 카메라 (카메라가 없는 기기는 보관함만) */
    private fun chooseCover(anchor: View) {
        val rows = mutableListOf(SelectRow.Option(getString(R.string.cover_from_gallery)) {
            pickCover.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY))
            rows += SelectRow.Option(getString(R.string.cover_from_camera)) {
                val dir = java.io.File(cacheDir, "camera").apply { mkdirs() }
                val f = java.io.File(dir, "cover.jpg").also { cameraFile = it }
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", f)
                runCatching { takeCover.launch(uri) }.onFailure { showSnack(getString(R.string.photo_failed)) }
            }
        showSelect(anchor, rows, widthDp = 220)
    }

    /** 고른 사진을 앱 안에 줄여서 복사 → 위치 조정 화면 */
    private fun importCover(uri: android.net.Uri) {
        lifecycleScope.launch {
            val path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Photos.importPhoto(this@TripFormActivity, uri) }
            cameraFile?.delete(); cameraFile = null
            if (path == null) { showSnack(getString(R.string.photo_failed)); return@launch }
            // 이번 화면에서 고른 뒤 다시 바꾼 사진은 정리 (저장된 예전 표지는 저장할 때 정리)
            if (cover != null && cover != trip?.coverPhoto) Photos.delete(this@TripFormActivity, cover)
            cover = path; coverY = 0.5f; coverScale = 1f
            showCover()
            openAdjust()
        }
    }

    private fun openAdjust() {
        val path = cover ?: return
        adjustCover.launch(android.content.Intent(this, CoverCropActivity::class.java)
            .putExtra(CoverCropActivity.EXTRA_PATH, path)
            .putExtra(CoverCropActivity.EXTRA_TITLE, inputName.text?.toString().orEmpty())
            .putExtra(CoverCropActivity.EXTRA_Y, coverY)
            .putExtra(CoverCropActivity.EXTRA_SCALE, coverScale))
    }

    private fun showCover() {
        val has = cover != null
        findViewById<View>(R.id.btnCoverUpload).visibility = if (has) View.GONE else View.VISIBLE
        findViewById<View>(R.id.coverBox).visibility = if (has) View.VISIBLE else View.GONE
        findViewById<View>(R.id.coverBox).clipToOutline = true  // XML 속성은 API 31 이상에서만 적용
        cover?.let { CoverPhoto.bind(findViewById(R.id.ivCover), it, coverY, coverScale) }
    }

    private fun pickPeriod() {
        Pickers.dateRange(this, getString(R.string.pick_period_title), start, end) { s, e ->
            start = s; end = e
            layoutPeriod.error = null
            showPeriod()
        }
    }

    private fun showPeriod() {
        val t = Trip(title = "", startDate = start, endDate = end)
        val nights = TripFormat.nightsDays(start, end)
        inputPeriod.setText(if (start.isBlank()) "" else TripFormat.fullRange(t))
        tvNights.text = nights
        tvNights.visibility = if (nights == null) View.GONE else View.VISIBLE
        inputPeriod.contentDescription = listOfNotNull(inputPeriod.text?.toString()?.takeIf { it.isNotBlank() }, nights)
            .joinToString(", ").ifBlank { null }
    }

    private fun save() {
        val title = inputName.text.toString().trim()
        val datesOk = runCatching { !LocalDate.parse(end).isBefore(LocalDate.parse(start)) }.getOrDefault(false)
        layoutName.error = if (title.isBlank()) getString(R.string.error_trip_title) else null
        layoutPeriod.error = if (!datesOk) getString(R.string.error_trip_period) else null
        if (title.isBlank()) { inputName.requestFocus(); return }
        if (!datesOk) return
        lifecycleScope.launch {
            // 저장 직전에 다시 읽어서 그사이 바뀐 메모·공유 정보 등을 덮어쓰지 않게
            val old = trip?.let { repo.getTrip(it.id) ?: it }
            if (old == null) repo.addTrip(Trip(title = title, startDate = start, endDate = end,
                coverPhoto = cover, coverY = coverY, coverScale = coverScale))
            else {
                // 표지를 바꾸거나 뺐으면 예전 사진 파일 정리
                if (old.coverPhoto != null && old.coverPhoto != cover) Photos.delete(this@TripFormActivity, old.coverPhoto)
                repo.updateTrip(old.copy(title = title, startDate = start, endDate = end,
                    coverPhoto = cover, coverY = coverY, coverScale = coverScale))
            }
            finish()
        }
    }
}
