package com.tripplanner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.tripplanner.R
import com.tripplanner.data.Trip
import com.tripplanner.data.TripWithCounts

/**
 * 여행 목록 어댑터 — 누르면 열기.
 * 수정·삭제 메뉴는 ⋮ 버튼 또는 길게 누르기(기존 방식)로 열림.
 */
class TripAdapter(
    private val onClick: (Trip) -> Unit,
    private val onMenu: (Trip, View) -> Unit
) : RecyclerView.Adapter<TripAdapter.Holder>() {

    private var items: List<TripWithCounts> = emptyList()

    fun submit(list: List<TripWithCounts>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val cover: View = view.findViewById(R.id.cover)
        val coverIcon: ImageView = view.findViewById(R.id.ivCoverIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvTripTitle)
        val tvDday: TextView = view.findViewById(R.id.tvDday)
        val tvDates: TextView = view.findViewById(R.id.tvTripDates)
        val tvCount: TextView = view.findViewById(R.id.tvTripCount)
        val tvShared: TextView = view.findViewById(R.id.tvShared)
        val progress: LinearProgressIndicator = view.findViewById(R.id.progressPlan)
        val btnMore: ImageButton = view.findViewById(R.id.btnTripMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trip, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        val dday = TripFormat.dday(item.trip)
        val past = dday?.phase == TripFormat.Phase.PAST

        // 커버: 여행마다 다른 색 (지난 여행은 회색), 첫 교통편 아이콘
        holder.cover.background = Covers.gradient(ctx, item.trip.id, past)
        holder.coverIcon.setImageResource(item.firstMode?.icon ?: R.drawable.ic_place)
        holder.tvTitle.text = item.trip.title
        holder.tvDday.visibility = if (dday == null) View.GONE else View.VISIBLE
        holder.tvDday.text = dday?.text

        holder.tvDates.text = TripFormat.dateRange(item.trip)
        holder.tvCount.text = ctx.getString(R.string.trip_counts, item.confirmedCount, item.candidateCount)
        holder.tvShared.visibility = if (item.trip.shareCode != null) View.VISIBLE else View.GONE
        val total = item.confirmedCount + item.candidateCount
        holder.progress.max = if (total == 0) 1 else total
        holder.progress.progress = item.confirmedCount

        holder.btnMore.contentDescription = ctx.getString(R.string.trip_menu_a11y, item.trip.title)
        holder.btnMore.setOnClickListener { onMenu(item.trip, it) }
        holder.itemView.contentDescription = listOfNotNull(item.trip.title, dday?.text, holder.tvDates.text, holder.tvCount.text)
            .joinToString(", ")
        holder.itemView.setOnClickListener { onClick(item.trip) }
        holder.itemView.setOnLongClickListener { onMenu(item.trip, holder.btnMore); true }
    }

    override fun getItemCount() = items.size
}
