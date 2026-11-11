package com.tripplanner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tripplanner.R
import com.tripplanner.data.Trip
import com.tripplanner.data.TripWithCounts

/**
 * 메인 목록 어댑터 (시안 TripCard): 여행 카드 / '지난 여행 n' 제목 / 지난 여행 작은 줄.
 * 누르면 열기, ⋮ 또는 길게 누르기로 수정·삭제 메뉴 (기존 동작 그대로).
 */
class TripAdapter(
    private val onClick: (Trip) -> Unit,
    private val onMenu: (Trip, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<TripRow> = emptyList()

    fun submit(list: List<TripRow>) {
        rows = list
        notifyDataSetChanged()
    }

    fun isFullWidth(position: Int) = rows.getOrNull(position) !is TripRow.Card

    private class CardHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cover: View = view.findViewById(R.id.cover)
        val coverIcon: ImageView = view.findViewById(R.id.ivCoverIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvTripTitle)
        val tvDday: TextView = view.findViewById(R.id.tvDday)
        val tvDates: TextView = view.findViewById(R.id.tvTripDates)
        val tvCount: TextView = view.findViewById(R.id.tvTripCount)
        val tvShared: TextView = view.findViewById(R.id.tvShared)
        val btnMore: ImageButton = view.findViewById(R.id.btnTripMore)
    }

    private class PastHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivPastIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvPastTitle)
        val tvDates: TextView = view.findViewById(R.id.tvPastDates)
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is TripRow.Card -> 0; is TripRow.PastHeader -> 1; is TripRow.Past -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> CardHolder(inflater.inflate(R.layout.item_trip, parent, false))
            1 -> HeaderHolder(inflater.inflate(R.layout.item_trip_header, parent, false))
            else -> PastHolder(inflater.inflate(R.layout.item_trip_past, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is TripRow.Card -> bindCard(holder as CardHolder, row.item)
            is TripRow.PastHeader -> (holder.itemView as TextView).text =
                holder.itemView.context.getString(R.string.past_trips_header, row.count)
            is TripRow.Past -> bindPast(holder as PastHolder, row.item)
        }
    }

    private fun bindCard(holder: CardHolder, item: TripWithCounts) {
        val ctx = holder.itemView.context
        val dday = TripFormat.dday(item.trip)
        // 표지: 여행마다 다른 색, 첫 교통편 아이콘
        holder.cover.background = Covers.gradient(ctx, item.trip.id, past = false)
        holder.coverIcon.setImageResource(item.firstMode?.icon ?: R.drawable.ic_place)
        holder.tvTitle.text = item.trip.title
        holder.tvDday.visibility = if (dday == null) View.GONE else View.VISIBLE
        holder.tvDday.text = dday?.text

        holder.tvDates.text = TripFormat.fullRangeWithDays(item.trip)
        holder.tvCount.text = ctx.getString(R.string.trip_counts, item.confirmedCount, item.candidateCount)
        holder.tvShared.visibility = if (item.trip.shareCode != null) View.VISIBLE else View.GONE

        holder.btnMore.contentDescription = ctx.getString(R.string.trip_menu_a11y, item.trip.title)
        holder.btnMore.setOnClickListener { onMenu(item.trip, it) }
        holder.itemView.contentDescription = listOfNotNull(item.trip.title, dday?.text, holder.tvDates.text, holder.tvCount.text,
            if (item.trip.shareCode != null) ctx.getString(R.string.shared_badge) else null).joinToString(", ")
        holder.itemView.setOnClickListener { onClick(item.trip) }
        holder.itemView.setOnLongClickListener { onMenu(item.trip, holder.btnMore); true }
    }

    private fun bindPast(holder: PastHolder, item: TripWithCounts) {
        val ctx = holder.itemView.context
        holder.icon.background = Covers.tile(ctx)
        holder.icon.setImageResource(item.firstMode?.icon ?: R.drawable.ic_place)
        holder.tvTitle.text = item.trip.title
        holder.tvDates.text = TripFormat.fullRange(item.trip)
        holder.itemView.contentDescription = listOf(item.trip.title, ctx.getString(R.string.past_trip), holder.tvDates.text)
            .joinToString(", ")
        holder.itemView.setOnClickListener { onClick(item.trip) }
        holder.itemView.setOnLongClickListener { onMenu(item.trip, holder.itemView); true }
    }

    override fun getItemCount() = rows.size
}
