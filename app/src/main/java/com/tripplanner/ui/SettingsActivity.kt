package com.tripplanner.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.tripplanner.BuildConfig
import com.tripplanner.R
import com.tripplanner.data.PlaceCategory

/**
 * 설정 (시안 AppSettings) — EXTRA_PAGE 가 "categories" 면 장소 종류 (시안 Settings).
 * 여행 보기(기본 정렬 · 지난 여행 숨기기) · 장소 종류 · 테마 · 공유 코드로 참여 · 지도 서비스 안내 · 앱 정보
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PAGE = "page"
        const val PAGE_CATEGORIES = "categories"
        /** 공유 코드로 참여한 여행 id 를 메인으로 돌려줌 */
        const val RESULT_JOINED = "joined"
    }

    private lateinit var content: LinearLayout
    private val viewPrefs by lazy { TripViewPrefs(this) }
    private val appPrefs by lazy { AppPrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        content = findViewById(R.id.content)
        limitScrollContentWidth(R.id.scroll)
        val categories = intent.getStringExtra(EXTRA_PAGE) == PAGE_CATEGORIES
        val toolbar = setupToolbar(getString(if (categories) R.string.settings_categories else R.string.settings_title))
        if (categories) { toolbar.subtitle = getString(R.string.categories_subtitle); buildCategories() } else buildMain()
    }

    override fun onResume() {
        super.onResume()
        // 다른 화면에서 바꾼 값(정렬 등)을 다시 보여 줌
        if (intent.getStringExtra(EXTRA_PAGE) != PAGE_CATEGORIES) { content.removeAllViews(); buildMain() }
    }

    // ---------------- 화면 조립 도우미 ----------------
    private fun section(title: Int): LinearLayout {
        content.addView(TextView(this).apply {
            setText(title)
            setTextAppearance(R.style.TextAppearance_TripPlanner_LabelLarge)
            setTextColor(color(R.color.text_sub))
            val d = resources.displayMetrics.density
            setPadding((4 * d).toInt(), (24 * d).toInt(), 0, (8 * d).toInt())
            androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        })
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            clipToOutline = true   // 눌림 덧칠이 카드 모서리 밖으로 나오지 않게
            content.addView(this)
        }
    }

    private fun row(card: LinearLayout, icon: Int?, title: String, value: String? = null, chevron: Boolean = true,
                    onClick: ((View) -> Unit)? = null): View {
        val v = LayoutInflater.from(this).inflate(R.layout.item_setting_row, card, false)
        v.findViewById<View>(R.id.divider).visibility = if (card.childCount == 0) View.GONE else View.VISIBLE
        v.findViewById<ImageView>(R.id.ivIcon).apply { visibility = if (icon == null) View.GONE else View.VISIBLE; icon?.let { setImageResource(it) } }
        v.findViewById<TextView>(R.id.tvTitle).text = title
        v.findViewById<TextView>(R.id.tvValue).apply { text = value; visibility = if (value == null) View.GONE else View.VISIBLE }
        v.findViewById<View>(R.id.ivEnd).visibility = if (chevron) View.VISIBLE else View.GONE
        val r = v.findViewById<View>(R.id.row)
        if (onClick != null) r.setOnClickListener(onClick) else { r.isClickable = false; r.background = null }
        r.contentDescription = listOfNotNull(title, value).joinToString(", ")
        card.addView(v)
        return v
    }

    /** 스위치 줄: 줄 전체를 누르면 켜고 끔 (TalkBack 은 '이름, 켜짐/꺼짐') */
    private fun switchRow(card: LinearLayout, icon: Int?, title: String, checked: Boolean, onChange: (Boolean) -> Unit): MaterialSwitch {
        val v = row(card, icon, title, chevron = false) {}
        val sw = v.findViewById<MaterialSwitch>(R.id.switchEnd)
        sw.visibility = View.VISIBLE
        sw.isChecked = checked
        val r = v.findViewById<View>(R.id.row)
        r.contentDescription = title
        r.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Switch::class.java.name
                info.isCheckable = true; info.isChecked = sw.isChecked
            }
        }
        r.setOnClickListener { sw.toggle() }
        sw.setOnCheckedChangeListener { _, c -> onChange(c) }
        return sw
    }

    // ---------------- 설정 ----------------
    private fun buildMain() {
        val view = section(R.string.settings_trip_view)
        val sortLabel = { s: TripSort -> getString(when (s) {
            TripSort.NAME -> R.string.sort_name; TripSort.RECENT -> R.string.sort_recent; TripSort.DATE -> R.string.sort_date }) }
        lateinit var sortRow: View
        sortRow = row(view, R.drawable.ic_sort_lines, getString(R.string.settings_default_sort), sortLabel(viewPrefs.sort)) { anchor ->
            showSelect(anchor, TripSort.entries.map { s ->
                SelectRow.Option(sortLabel(s), selected = viewPrefs.sort == s) {
                    viewPrefs.sort = s
                    sortRow.findViewById<TextView>(R.id.tvValue).text = sortLabel(s)
                }
            }, widthDp = 200)
        }
        switchRow(view, R.drawable.ic_visibility_off, getString(R.string.past_hide), !viewPrefs.showPast) { viewPrefs.showPast = !it }

        val place = section(R.string.settings_place)
        row(place, R.drawable.ic_category, getString(R.string.settings_categories)) {
            startActivity(Intent(this, SettingsActivity::class.java).putExtra(EXTRA_PAGE, PAGE_CATEGORIES))
        }

        // 테마: 세그먼트 (기기 설정 / 밝게 / 어둡게)
        val screen = section(R.string.settings_screen)
        val themeBox = LayoutInflater.from(this).inflate(R.layout.item_setting_theme, screen, false)
        val group = themeBox.findViewById<SegmentedGroup>(R.id.toggleTheme)
        group.check(when (appPrefs.theme) { AppPrefs.Theme.SYSTEM -> R.id.btnThemeSystem; AppPrefs.Theme.LIGHT -> R.id.btnThemeLight; AppPrefs.Theme.DARK -> R.id.btnThemeDark })
        group.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            appPrefs.theme = when (id) { R.id.btnThemeLight -> AppPrefs.Theme.LIGHT; R.id.btnThemeDark -> AppPrefs.Theme.DARK; else -> AppPrefs.Theme.SYSTEM }
        }
        screen.addView(themeBox)

        val share = section(R.string.settings_share)
        row(share, R.drawable.ic_group_add, getString(R.string.join_with_code)) {
            openJoinDialog { id ->
                setResult(RESULT_OK, Intent().putExtra(RESULT_JOINED, id))
                finish()
            }
        }

        val map = section(R.string.settings_map)
        val mapRow = row(map, R.drawable.ic_map, getString(R.string.settings_map_service), chevron = false) { showMapInfo(it) }
        mapRow.findViewById<ImageView>(R.id.ivEnd).apply {
            visibility = View.VISIBLE; setImageResource(R.drawable.ic_info)
        }

        val app = section(R.string.settings_app)
        row(app, null, getString(R.string.settings_version), BuildConfig.VERSION_NAME, chevron = false)
        row(app, null, getString(R.string.settings_licenses)) { showLicenses() }
    }

    /** 지도 서비스 ⓘ: 누른 줄 아래에 어두운 안내 상자 */
    private fun showMapInfo(anchor: View) {
        val d = resources.displayMetrics.density
        val box = LayoutInflater.from(this).inflate(R.layout.view_tooltip, null)
        box.findViewById<TextView>(R.id.tvTipTitle).setText(R.string.settings_map_info_title)
        box.findViewById<TextView>(R.id.tvTipBody).setText(R.string.settings_map_info_body)
        val popup = PopupWindow(box, (anchor.width - 32 * d).toInt().coerceAtLeast((280 * d).toInt()),
            ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.elevation = 8 * d
        popup.showAsDropDown(anchor, (16 * d).toInt(), (-4 * d).toInt(), Gravity.END)
    }

    private fun showLicenses() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_licenses)
            .setMessage(R.string.licenses_body)
            .setPositiveButton(R.string.action_close, null)
            .setNeutralButton(R.string.licenses_ofl) { _, _ ->
                val text = runCatching { assets.open("licenses/Pretendard-OFL.txt").bufferedReader().readText() }.getOrDefault("")
                MaterialAlertDialogBuilder(this).setTitle("Pretendard").setMessage(text)
                    .setPositiveButton(R.string.action_close, null).show()
            }
            .show()
    }

    // ---------------- 장소 종류 ----------------
    private fun buildCategories() {
        val card = section(R.string.categories_default)
        PlaceCategory.entries.forEach { c ->
            val hidden = appPrefs.hiddenCategories
            val sw = switchRow(card, null, c.label, c !in hidden) { on ->
                appPrefs.hiddenCategories = if (on) appPrefs.hiddenCategories - c else appPrefs.hiddenCategories + c
            }
            // 이름 앞 종류 타일 (바탕 = 종류 색)
            val icon = (sw.parent as View).findViewById<ImageView>(R.id.ivIcon)
            icon.visibility = View.VISIBLE
            val d = resources.displayMetrics.density
            icon.layoutParams = icon.layoutParams.apply { width = (36 * d).toInt(); height = (36 * d).toInt() }
            Thumbs.bind(icon, null, c)
            icon.setPadding((7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt(), (7 * d).toInt())
            sw.contentDescription = getString(R.string.category_switch_a11y, c.label)
        }
    }
}
