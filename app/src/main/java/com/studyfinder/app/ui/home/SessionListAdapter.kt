package com.studyfinder.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studyfinder.app.R
import com.studyfinder.app.databinding.ItemHomeSessionBinding
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.TagType
import com.studyfinder.app.util.DateTimeUtils

/**
 * Home's session card.
 *
 * Three greyed-out treatments share this view holder — a full session, one
 * that overlaps the user's availability, and one containing a blocked user.
 * All stay visible but dimmed with a reason label, rather than vanishing.
 * Priority when more than one applies: full > blocked user > schedule overlap.
 */
class SessionListAdapter(
    private val onClick: (Session) -> Unit,
) : ListAdapter<SessionListAdapter.Row, SessionListAdapter.ViewHolder>(DIFF) {

    /** The session plus the per-render annotations Home computes client-side. */
    data class Row(
        val session: Session,
        val distanceKm: Double? = null,
        val overlapsAvailability: Boolean = false,
        val containsBlockedUser: Boolean = false,
    )

    class ViewHolder(val binding: ItemHomeSessionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemHomeSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        val s = row.session
        val b = holder.binding
        val ctx = b.root.context

        b.tvSessionTitle.text = s.title
        b.tvSubtitle.text = listOf(s.courseId, s.description)
            .filter { it.isNotBlank() }
            .joinToString(" • ")

        b.tvTime.text = DateTimeUtils.formatDateTime(s.startTimeMillis)
        b.tvSpots.text = ctx.getString(R.string.attendees_count_format, s.joinedCount, s.capacity)

        b.tvLocation.text = when {
            row.distanceKm != null && s.locationName.isNotBlank() ->
                "${s.locationName} (${formatKm(row.distanceKm)})"
            row.distanceKm != null -> formatKm(row.distanceKm)
            else -> s.locationName
        }

        val durationMinutes = ((s.endTimeMillis - s.startTimeMillis) / 60_000L).toInt().coerceAtLeast(0)
        b.tvDuration.text = DateTimeUtils.formatDuration(durationMinutes)

        b.tagType.text = s.tagType.name
        b.tagCourse.text = s.courseId.ifBlank { s.courseCategory.name }
        b.tagEffort.text = s.expectationLevel.name

        // ---- grey treatment + notice ------------------------------------------
        val noticeRes: Int? = when {
            s.isFull -> R.string.notice_session_full
            row.containsBlockedUser -> R.string.notice_contains_blocked_user
            row.overlapsAvailability -> R.string.notice_overlaps_schedule
            else -> null
        }
        if (noticeRes != null) {
            b.tvNotice.setText(noticeRes)
            b.tvNotice.visibility = ViewGroup.VISIBLE
            b.headerContainer.setBackgroundResource(R.drawable.bg_card_header_gray)
            b.root.alpha = 0.6f
        } else {
            b.tvNotice.visibility = ViewGroup.GONE
            b.headerContainer.setBackgroundResource(headerColorFor(s.tagType))
            b.root.alpha = 1f
        }

        b.root.setOnClickListener { onClick(s) }
    }

    private fun formatKm(km: Double): String =
        if (km < 1.0) "${(km * 1000).toInt()}m" else "%.1fkm".format(km)

    private fun headerColorFor(tag: TagType): Int = when (tag) {
        TagType.NORMAL -> R.drawable.bg_card_header_lblue
        TagType.MIDTERM -> R.drawable.bg_card_header_clay
        TagType.FINAL -> R.drawable.bg_card_header_red
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(old: Row, new: Row) =
                old.session.id == new.session.id

            override fun areContentsTheSame(old: Row, new: Row) = old == new
        }
    }
}
