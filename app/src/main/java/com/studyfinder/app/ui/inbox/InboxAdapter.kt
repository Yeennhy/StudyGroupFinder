package com.studyfinder.app.ui.inbox

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studyfinder.app.R
import com.studyfinder.app.databinding.ItemInboxBinding
import com.studyfinder.app.databinding.ItemNotificationDatePillBinding
import com.studyfinder.app.model.InboxItem
import com.studyfinder.app.model.InboxType
import com.studyfinder.app.util.DateTimeUtils

/** A flat row in the inbox list — either a date header or an item. */
sealed class InboxRow {
    data class DatePill(val label: String) : InboxRow()
    data class Item(val inboxItem: InboxItem) : InboxRow()
}

/**
 * Multi-view-type inbox list. The item's `type` decides the buttons:
 *  - `invite`       -> Accept (joins in place) + Details (Session Detail)
 *  - `join_request` -> Details (Session Management, host-facing)
 *  - `system`       -> tap the row to mark read
 */
class InboxAdapter(
    private val onAccept: (InboxItem) -> Unit,
    private val onDetails: (InboxItem) -> Unit,
    private val onMarkRead: (InboxItem) -> Unit,
) : ListAdapter<InboxRow, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is InboxRow.DatePill -> TYPE_PILL
        is InboxRow.Item -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_PILL) {
            PillViewHolder(ItemNotificationDatePillBinding.inflate(inflater, parent, false))
        } else {
            ItemViewHolder(ItemInboxBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is InboxRow.DatePill -> (holder as PillViewHolder).binding.tvDateLabel.text = row.label
            is InboxRow.Item -> (holder as ItemViewHolder).bind(row.inboxItem)
        }
    }

    inner class PillViewHolder(val binding: ItemNotificationDatePillBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ItemViewHolder(private val binding: ItemInboxBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InboxItem) {
            binding.tvTitle.text = item.message
            binding.tvTimestamp.text = DateTimeUtils.formatDateTime(item.createdAtMillis)
            binding.ivIcon.setImageResource(
                when (item.type) {
                    InboxType.INVITE -> R.drawable.ic_mail
                    InboxType.JOIN_REQUEST -> R.drawable.ic_bell
                    InboxType.SYSTEM -> R.drawable.ic_bell
                }
            )
            binding.ivIcon.alpha = if (item.read) 0.4f else 1f

            val primary = binding.btnPrimary
            val secondary = binding.btnSecondary
            primary.visibility = View.GONE
            secondary.visibility = View.GONE
            binding.root.setOnClickListener(null)

            when (item.type) {
                InboxType.INVITE -> {
                    // Accept is only offered while the invite is still open;
                    // once handled (read) it just links to the session.
                    if (!item.read) {
                        primary.visibility = View.VISIBLE
                        primary.setText(R.string.inbox_accept)
                        primary.setOnClickListener { onAccept(item) }
                    }
                    if (item.sessionId != null) {
                        secondary.visibility = View.VISIBLE
                        secondary.setText(R.string.inbox_details)
                        secondary.setOnClickListener { onDetails(item) }
                    }
                }
                InboxType.JOIN_REQUEST -> {
                    if (item.sessionId != null) {
                        secondary.visibility = View.VISIBLE
                        secondary.setText(R.string.inbox_details)
                        secondary.setOnClickListener { onDetails(item) }
                    }
                }
                InboxType.SYSTEM -> {
                    binding.root.setOnClickListener { 
                        if (!item.read) onMarkRead(item)
                        if (item.sessionId != null) {
                            onDetails(item)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val TYPE_PILL = 0
        const val TYPE_ITEM = 1

        val DIFF = object : DiffUtil.ItemCallback<InboxRow>() {
            override fun areItemsTheSame(old: InboxRow, new: InboxRow): Boolean = when {
                old is InboxRow.DatePill && new is InboxRow.DatePill -> old.label == new.label
                old is InboxRow.Item && new is InboxRow.Item ->
                    old.inboxItem.id == new.inboxItem.id
                else -> false
            }

            override fun areContentsTheSame(old: InboxRow, new: InboxRow) = old == new
        }
    }
}
