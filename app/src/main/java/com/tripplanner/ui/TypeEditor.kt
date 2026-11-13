package com.tripplanner.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R
import com.tripplanner.data.PlaceType
import com.tripplanner.ui.widget.TpButton
import com.tripplanner.ui.widget.TpTextField

/**
 * 내가 만든 장소 종류 편집 카드 (시안 Settings 아래쪽 파란 테두리 카드).
 * 미리보기 타일 + 이름 / 아이콘 120개 (검색 · 최근 · 장소 · 음식 · 활동 · 교통) / 바탕색 8가지 / 삭제 · 저장
 */
class TypeEditor(
    context: Context,
    private val original: PlaceType?,
    private val onSave: (PlaceType) -> Unit,
    private val onDelete: (PlaceType) -> Unit,
    private val onCancel: () -> Unit
) : LinearLayout(context) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private var icon = original?.icon ?: RecentIcons.get(context).first()
    private var colorIndex = original?.color ?: 0
    private var group: TypeIcons.Group? = null   // null = 최근
    private var query = ""

    private val preview = ImageView(context)
    private val nameField = EditText(context)
    private val nameBox = TpTextField(context)
    private val grid = RecyclerView(context)
    private val tabs = LinearLayout(context)
    private val colors = LinearLayout(context)
    private val emptyText = TextView(context)
    private var shown: List<TypeIcons.TypeIcon> = emptyList()

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(context, R.color.card_bg))
            cornerRadius = 20 * d
            setStroke(dp(2), ContextCompat.getColor(context, R.color.primary))
        }
        setPadding(dp(16), dp(16), dp(16), dp(16))

        // 미리보기 + 이름
        val head = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.BOTTOM }
        preview.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        head.addView(preview, LayoutParams(dp(56), dp(56)).apply { bottomMargin = dp(4) })
        val nameCol = LinearLayout(context).apply { orientation = VERTICAL }
        nameCol.addView(label(R.string.type_name).apply { labelFor = View.generateViewId().also { nameField.id = it } })
        nameField.inputType = InputType.TYPE_CLASS_TEXT
        nameField.filters = arrayOf(android.text.InputFilter.LengthFilter(10))
        nameField.setText(original?.name.orEmpty())
        nameField.hint = context.getString(R.string.type_name_hint)
        nameBox.addView(nameField)
        nameCol.addView(nameBox)
        head.addView(nameCol, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
        addView(head)
        nameField.doAfterTextChanged { nameBox.error = null }

        // 아이콘
        addView(label(R.string.type_icons, TypeIcons.ALL.size).apply { setPadding(0, dp(16), 0, dp(8)) })
        val search = TpTextField(context, null)
        val searchEdit = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = context.getString(R.string.type_icon_search)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        search.addView(searchEdit)
        searchEdit.doAfterTextChanged { query = it?.toString().orEmpty(); refreshGrid() }
        addView(search)

        tabs.orientation = HORIZONTAL
        val tabNames = listOf(null to R.string.type_group_recent, TypeIcons.Group.PLACE to R.string.type_group_place,
            TypeIcons.Group.FOOD to R.string.type_group_food, TypeIcons.Group.ACTIVITY to R.string.type_group_activity,
            TypeIcons.Group.MOVE to R.string.type_group_move)
        tabNames.forEach { (g, res) ->
            tabs.addView(TextView(context).apply {
                setText(res)
                gravity = Gravity.CENTER
                setTextAppearance(R.style.TextAppearance_TripPlanner_LabelLarge)
                minHeight = dp(40)
                isClickable = true; isFocusable = true
                tag = g ?: "recent"
                setOnClickListener { group = g; refreshTabs(); refreshGrid() }
            }, LayoutParams(0, dp(40), 1f))
        }
        addView(tabs, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })

        // 6칸 격자, 3줄 보이고 안에서 스크롤
        grid.layoutManager = GridLayoutManager(context, COLUMNS)
        grid.adapter = IconAdapter()
        grid.isVerticalScrollBarEnabled = true
        grid.overScrollMode = OVER_SCROLL_NEVER
        grid.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(out: android.graphics.Rect, v: View, parent: RecyclerView, state: RecyclerView.State) {
                out.set(dp(4), dp(4), dp(4), dp(4))
            }
        })
        val gridBox = FrameLayout(context)
        gridBox.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3 * 52)))
        emptyText.apply {
            setText(R.string.type_icon_none)
            setTextAppearance(R.style.TextAppearance_TripPlanner_BodyMedium)
            setTextColor(ContextCompat.getColor(context, R.color.text_sub))
            gravity = Gravity.CENTER
            visibility = GONE
        }
        gridBox.addView(emptyText, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3 * 52)))
        addView(gridBox, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8); marginStart = -dp(4); marginEnd = -dp(4)
        })

        // 바탕색
        addView(label(R.string.type_colors).apply { setPadding(0, dp(16), 0, dp(8)) })
        colors.orientation = HORIZONTAL
        PlaceKinds.COLORS.forEachIndexed { i, res ->
            colors.addView(ImageView(context).apply {
                contentDescription = context.getString(R.string.type_color_a11y, i + 1)
                isClickable = true; isFocusable = true
                scaleType = ImageView.ScaleType.CENTER
                setOnClickListener { colorIndex = i; refreshColors(); refreshPreview() }
            }, LayoutParams(0, dp(40), 1f))
        }
        addView(colors)

        // 삭제 · 저장 (새로 만들 때는 취소)
        val buttons = LinearLayout(context).apply { orientation = HORIZONTAL }
        val left = TpButton(context).apply {
            if (original != null) {
                setText(R.string.action_delete)
                background = ContextCompat.getDrawable(context, R.drawable.bg_btn_outlined)
                setTextColor(ContextCompat.getColor(context, R.color.badge_red))
                setOnClickListener { onDelete(original) }
            } else {
                setText(R.string.action_cancel)
                background = ContextCompat.getDrawable(context, R.drawable.bg_btn_outlined)
                setTextColor(ContextCompat.getColor(context, R.color.on_surface))
                setOnClickListener { onCancel() }
            }
            foreground = ContextCompat.getDrawable(context, R.drawable.press_pill_primary)
        }
        val save = TpButton(context).apply {
            setText(R.string.action_save)
            setOnClickListener { save() }
        }
        buttons.addView(left, LayoutParams(0, dp(48), 1f))
        buttons.addView(save, LayoutParams(0, dp(48), 2f).apply { marginStart = dp(8) })
        addView(buttons, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(20) })

        refreshTabs(); refreshGrid(); refreshColors(); refreshPreview()
    }

    private fun label(res: Int, vararg args: Any) = TextView(context).apply {
        text = context.getString(res, *args)
        setTextAppearance(R.style.TextAppearance_TripPlanner_LabelMedium)
        setTextColor(ContextCompat.getColor(context, R.color.text_sub))
        setPadding(0, 0, 0, dp(6))
    }

    fun focusName() { nameField.requestFocus() }

    private fun save() {
        val name = nameField.text.toString().trim()
        if (name.isEmpty()) { nameBox.error = context.getString(R.string.error_type_name); return }
        RecentIcons.add(context, icon)
        onSave((original ?: PlaceType(name = name, icon = icon, color = colorIndex)).copy(name = name, icon = icon, color = colorIndex))
    }

    private fun refreshPreview() {
        Thumbs.bind(preview, null, TypeIcons.res(icon), PlaceKinds.colorRes(colorIndex))
        preview.setPadding(dp(14), dp(14), dp(14), dp(14))
        (preview.background as? GradientDrawable)?.cornerRadius = 16 * d
    }

    private fun refreshTabs() {
        for (i in 0 until tabs.childCount) {
            val tv = tabs.getChildAt(i) as TextView
            val sel = (tv.tag == "recent" && group == null) || tv.tag == group
            tv.background = GradientDrawable().apply {
                cornerRadius = 10 * d
                setColor(ContextCompat.getColor(context, if (sel && query.isBlank()) R.color.primary_light else android.R.color.transparent))
            }
            tv.foreground = ContextCompat.getDrawable(context, R.drawable.press_r10)
            tv.setTextColor(ContextCompat.getColor(context, if (sel && query.isBlank()) R.color.on_primary_container else R.color.text_sub))
            tv.isSelected = sel
            ViewCompat.setStateDescription(tv, if (sel) context.getString(R.string.state_selected) else null)
        }
    }

    private fun refreshGrid() {
        shown = when {
            query.isNotBlank() -> TypeIcons.search(query)
            group == null -> RecentIcons.get(context).mapNotNull { k -> TypeIcons.ALL.firstOrNull { it.key == k } } +
                TypeIcons.ALL.filter { it.key !in RecentIcons.get(context) }
            else -> TypeIcons.ALL.filter { it.group == group }
        }
        emptyText.visibility = if (shown.isEmpty()) VISIBLE else GONE
        refreshTabs()
        @Suppress("NotifyDataSetChanged")
        grid.adapter?.notifyDataSetChanged()
    }

    private fun refreshColors() {
        for (i in 0 until colors.childCount) {
            val v = colors.getChildAt(i) as ImageView
            val sel = i == colorIndex
            v.setImageDrawable(android.graphics.drawable.LayerDrawable(arrayOf(GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, PlaceKinds.colorRes(i)))
                setStroke(dp(if (sel) 2 else 1), ContextCompat.getColor(context, if (sel) R.color.on_surface else R.color.outline_variant))
                setSize(dp(32), dp(32))
            })))
            if (sel) v.foreground = ContextCompat.getDrawable(context, R.drawable.ic_check)?.mutate()?.apply {
                setTint(ContextCompat.getColor(context, R.color.on_surface))
            }.let { ic -> android.graphics.drawable.InsetDrawable(ic, dp(0)) } else v.foreground = null
            v.foregroundGravity = Gravity.CENTER
            v.isSelected = sel
            ViewCompat.setStateDescription(v, if (sel) context.getString(R.string.state_selected) else null)
        }
    }

    private inner class IconAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = shown.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true; isFocusable = true
            foreground = ContextCompat.getDrawable(parent.context, R.drawable.press_r12)
        }) {}
        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            val item = shown[position]
            val v = h.itemView as ImageView
            val sel = item.key == icon
            v.setImageResource(item.res)
            v.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.on_surface))
            v.background = GradientDrawable().apply {
                cornerRadius = 12 * d
                setColor(ContextCompat.getColor(context, if (sel) R.color.primary_light else R.color.surface_container))
                if (sel) setStroke(dp(2), ContextCompat.getColor(context, R.color.primary))
            }
            v.contentDescription = item.keywords.split(" ").first()
            ViewCompat.setStateDescription(v, if (sel) context.getString(R.string.state_selected) else null)
            v.setOnClickListener {
                icon = item.key
                @Suppress("NotifyDataSetChanged")
                notifyDataSetChanged(); refreshPreview()
            }
        }
    }

    companion object { const val COLUMNS = 6 }
}

/** 최근에 고른 종류 아이콘 (기기에 저장, 처음엔 자주 쓰는 것들) */
object RecentIcons {
    private val DEFAULT = listOf("nightlight", "attractions", "confirmation_number", "image", "hotel", "category")
    fun get(context: Context): List<String> {
        val raw = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getString("recent_type_icons", null)
        return (raw?.split(",")?.filter { TypeIcons.has(it) } ?: emptyList()).ifEmpty { DEFAULT }
    }
    fun add(context: Context, key: String) {
        val list = (listOf(key) + get(context)).distinct().take(12)
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().putString("recent_type_icons", list.joinToString(",")).apply()
    }
}
