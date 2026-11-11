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
        val coverPhoto: ImageView = view.findViewById(R.id.ivCoverPhoto)
        val coverScrim: View = view.findViewById(R.id.coverScrim)
        val coverIcon: ImageView = view.findViewById(R.id.ivCoverIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvTripTitle)
        val tvDday: TextView = view.findViewById(R.id.tvDday)
        val tvDates: TextView = view.findViewById(R.id.tvTripDates)
        val tvCount: TextView = view.findViewById(R.id.tvTripCount)
        val badges: android.widget.LinearLayout = view.findViewById(R.id.badges)
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
        holder.cover.clipToOutline = true
        // 표지 사진이 있으면 그라데이션 위에 사진 (사진 파일이 없어졌으면 그라데이션만)
        val photo = item.trip.coverPhoto?.takeIf { java.io.File(it).exists() }
        holder.coverPhoto.visibility = if (photo != null) View.VISIBLE else View.GONE
        holder.coverScrim.visibility = holder.coverPhoto.visibility
        if (photo != null) CoverPhoto.bind(holder.coverPhoto, photo, item.trip.coverY, item.trip.coverScale)
        holder.coverIcon.setImageResource(item.firstMode?.icon ?: R.drawable.ic_place)
        holder.tvTitle.text = item.trip.title
        holder.tvDday.visibility = if (dday == null) View.GONE else View.VISIBLE
        holder.tvDday.text = dday?.text

        holder.tvDates.text = TripFormat.fullRangeWithDays(item.trip)
        holder.tvCount.text = ctx.getString(R.string.trip_counts, item.confirmedCount, item.candidateCount)
        val badges = tripBadges(ctx, item.trip)
        holder.badges.removeAllViews()
        badges.forEach { (icon, text, tertiary) ->
            val tv = LayoutInflater.from(ctx).inflate(R.layout.view_badge, holder.badges, false) as TextView
            tv.text = text
            tv.backgroundTintList = android.content.res.ColorStateList.valueOf(ctx.color(if (tertiary) R.color.tertiary_container else R.color.surface_container))
            tv.setTextColor(ctx.color(if (tertiary) R.color.on_tertiary_container else R.color.text_sub))
            val s = (14 * ctx.resources.displayMetrics.density).toInt()
            val d = androidx.core.content.ContextCompat.getDrawable(ctx, icon)?.mutate()?.apply {
                setBounds(0, 0, s, s); setTint(ctx.color(if (tertiary) R.color.on_tertiary_container else R.color.text_sub))
            }
            tv.setCompoundDrawablesRelative(d, null, null, null)
            tv.compoundDrawablePadding = (4 * ctx.resources.displayMetrics.density).toInt()
            holder.badges.addView(tv)
        }
        holder.badges.visibility = if (badges.isEmpty()) View.GONE else View.VISIBLE

        holder.btnMore.contentDescription = ctx.getString(R.string.trip_menu_a11y, item.trip.title)
        holder.btnMore.setOnClickListener { onMenu(item.trip, it) }
        holder.itemView.contentDescription = (listOfNotNull(item.trip.title, dday?.text, holder.tvDates.text, holder.tvCount.text) +
            badges.map { it.second }).joinToString(", ")
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

    /** (아이콘, 글자, 청록 여부): 내가 만든 여행 + n명과 공유 중 / ○○님이 초대함 */
    private fun tripBadges(ctx: android.content.Context, t: Trip): List<Triple<Int, String, Boolean>> = buildList {
        if (t.ownedByMe) {
            add(Triple(R.drawable.ic_person, ctx.getString(R.string.badge_mine), false))
            if (t.shareCode != null) add(Triple(R.drawable.ic_group_add,
                if (t.members.isEmpty()) ctx.getString(R.string.shared_badge) else ctx.getString(R.string.badge_shared_n, t.members.size), true))
        } else {
            add(Triple(R.drawable.ic_group_add,
                if (t.inviterName.isBlank()) ctx.getString(R.string.badge_invited) else ctx.getString(R.string.badge_invited_by, t.inviterName), true))
        }
    }
}
