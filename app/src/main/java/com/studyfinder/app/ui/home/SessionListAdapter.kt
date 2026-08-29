package com.studyfinder.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studyfinder.app.databinding.ItemHomeSessionBinding
import com.studyfinder.app.model.Session

/**
 * Home's session card (§7.2): course, tag, time, location, X/Y joined, and
 * optionally a "0.3 km away" distance label.
 *
 * Two greyed-out treatments share this view holder — a session that overlaps
 * the user's availability, and one containing a blocked user. Both stay
 * visible but dimmed with a reason label, rather than vanishing.
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
        TODO("§7.2")
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(old: Row, new: Row) =
                old.session.id == new.session.id

            override fun areContentsTheSame(old: Row, new: Row) = old == new
        }
    }
}
