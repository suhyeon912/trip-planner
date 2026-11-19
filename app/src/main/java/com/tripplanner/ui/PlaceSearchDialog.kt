package com.tripplanner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.ui.widget.TpDialog
import com.tripplanner.ui.widget.TpProgressBar
import android.widget.EditText
import com.tripplanner.ui.widget.TpTextField
import com.tripplanner.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 장소 검색 창. 고르면 onPick 으로 결과를 돌려주고 닫힘.
 * @param domesticDefault 여행이 국내면 true (카카오 검색이 먼저 선택됨)
 */
fun showPlaceSearch(activity: AppCompatActivity, initialQuery: String, domesticDefault: Boolean, onPick: (SearchHit) -> Unit) {
    val view = LayoutInflater.from(activity).inflate(R.layout.view_place_search, null)
    val til = view.findViewById<TpTextField>(R.id.tilQuery)
    val et = view.findViewById<EditText>(R.id.etQuery)
    val toggle = view.findViewById<SegmentedGroup>(R.id.toggleRegion)
    val progress = view.findViewById<TpProgressBar>(R.id.progress)
    val msg = view.findViewById<TextView>(R.id.tvMessage)
    val rv = view.findViewById<RecyclerView>(R.id.rvHits)
    toggle.check(if (domesticDefault) R.id.btnDomestic else R.id.btnOverseas)
    et.setText(initialQuery)

    val dialog = TpDialog.Builder(activity)
        .setTitle(R.string.search_place)
        .setView(view)
        .setCloseButton()   // 아래 '취소' 대신 오른쪽 위 X
        .create()

    val adapter = HitAdapter { hit -> dialog.dismiss(); onPick(hit) }
    rv.layoutManager = LinearLayoutManager(activity)
    rv.adapter = adapter

    var job: Job? = null
    fun run() {
        val q = et.text?.toString()?.trim().orEmpty()
        if (q.isEmpty()) return
        job?.cancel()
        progress.visibility = View.VISIBLE
        msg.visibility = View.GONE
        job = activity.lifecycleScope.launch {
            val result = runCatching { PlaceSearch.search(activity, q, toggle.checkedButtonId == R.id.btnDomestic) }
            progress.visibility = View.INVISIBLE
            result.onSuccess { hits ->
                adapter.submit(hits)
                msg.visibility = if (hits.isEmpty()) View.VISIBLE else View.GONE
                msg.setText(R.string.search_empty)
            }.onFailure { e ->
                adapter.submit(emptyList())
                msg.visibility = View.VISIBLE
                val raw = e.message.orEmpty()
                val reason = Regex("""\[([A-Z_]+)]""").find(raw)?.groupValues?.get(1)
                msg.text = when {
                    e is PlaceSearch.NoKeyException -> activity.getString(e.messageRes)
                    // Google 오류 이유별 안내 (끝에 이유 코드도 함께)
                    reason == "API_KEY_ANDROID_APP_BLOCKED" -> activity.getString(R.string.search_denied_app, reason)
                    reason == "API_KEY_SERVICE_BLOCKED" -> activity.getString(R.string.search_denied_api, reason)
                    reason == "SERVICE_DISABLED" || reason == "ACCESS_NOT_CONFIGURED" -> activity.getString(R.string.search_denied_disabled, reason)
                    reason == "BILLING_DISABLED" -> activity.getString(R.string.search_denied_billing, reason)
                    reason != null -> activity.getString(R.string.search_failed, raw)
                    // Google 콘솔 설정 문제: API 꺼짐 · 키 제한
                    raw.contains("Permission denied", true) || raw.contains("PERMISSION_DENIED") || raw.contains("not authorized", true) ||
                        raw.contains("has not been used", true) || raw.contains("disabled", true) ->
                        activity.getString(if (toggle.checkedButtonId == R.id.btnDomestic) R.string.search_denied_kakao else R.string.search_denied_google)
                    else -> activity.getString(R.string.search_failed, raw.ifBlank { e.javaClass.simpleName })
                }
            }
        }
    }
    til.setEndIconOnClickListener { run() }
    et.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEARCH) { run(); true } else false }
    toggle.addOnButtonCheckedListener { _, _, checked -> if (checked) run() }
    dialog.show()
    if (initialQuery.isNotBlank()) run()
}

private class HitAdapter(private val onPick: (SearchHit) -> Unit) : RecyclerView.Adapter<HitAdapter.H>() {
    private var items: List<SearchHit> = emptyList()
    fun submit(list: List<SearchHit>) { items = list; notifyDataSetChanged() }
    class H(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.ivCat)
        val name: TextView = v.findViewById(R.id.tvHitName)
        val addr: TextView = v.findViewById(R.id.tvHitAddr)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = H(LayoutInflater.from(p.context).inflate(R.layout.item_search_hit, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: H, i: Int) {
        val hit = items[i]
        h.name.text = hit.name
        h.addr.text = listOfNotNull(hit.category.label, hit.address.takeIf { it.isNotBlank() }, hit.hours).joinToString(" · ")
        Thumbs.bind(h.icon, null, hit.category)
        h.itemView.setOnClickListener { onPick(hit) }
    }
}
