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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tripplanner.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 장소 검색 창. 고르면 onPick 으로 결과를 돌려주고 닫힘.
 * @param domesticDefault 여행이 국내면 true (카카오 검색이 먼저 선택됨)
 */
fun showPlaceSearch(activity: AppCompatActivity, initialQuery: String, domesticDefault: Boolean, onPick: (SearchHit) -> Unit) {
    val view = LayoutInflater.from(activity).inflate(R.layout.view_place_search, null)
    val til = view.findViewById<TextInputLayout>(R.id.tilQuery)
    val et = view.findViewById<TextInputEditText>(R.id.etQuery)
    val toggle = view.findViewById<SegmentedGroup>(R.id.toggleRegion)
    val progress = view.findViewById<LinearProgressIndicator>(R.id.progress)
    val msg = view.findViewById<TextView>(R.id.tvMessage)
    val rv = view.findViewById<RecyclerView>(R.id.rvHits)
    toggle.check(if (domesticDefault) R.id.btnDomestic else R.id.btnOverseas)
    et.setText(initialQuery)

    val dialog = MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.search_place)
        .setView(view)
        .setNegativeButton(R.string.action_cancel, null)
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
                msg.text = if (e is PlaceSearch.NoKeyException) activity.getString(e.messageRes)
                           else activity.getString(R.string.search_failed, e.message ?: e.javaClass.simpleName)
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
