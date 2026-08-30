package com.studyfinder.app.ui.mysessions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studyfinder.app.R
import com.studyfinder.app.databinding.ItemCalendarDayBinding
import com.studyfinder.app.databinding.ItemMySessionDateheaderBinding
import com.studyfinder.app.databinding.ItemSessionCalendarBinding
import com.studyfinder.app.databinding.ItemSessionListBinding
import com.studyfinder.app.model.Session
import com.studyfinder.app.util.DateTimeUtils
import java.time.LocalDate

/** The list half of §7.6. */
class MySessionListAdapter(
    private val onClick: (Session) -> Unit,
) : ListAdapter<MySessionListItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is MySessionListItem.Header -> 0
        is MySessionListItem.SessionItem -> 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> HeaderViewHolder(ItemMySessionDateheaderBinding.inflate(inflater, parent, false))
            else -> SessionViewHolder(ItemSessionListBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is HeaderViewHolder && item is MySessionListItem.Header) {
            holder.binding.tvDateLabel.text = item.label
        } else if (holder is SessionViewHolder && item is MySessionListItem.SessionItem) {
            val session = item.session
            holder.binding.apply {
                tvSessionTitle.text = session.title
                tvSessionTime.text = DateTimeUtils.formatTime(session.startTimeMillis)
                tvSessionLocation.text = session.locationName
                
                val durationMinutes = ((session.endTimeMillis - session.startTimeMillis) / 60000).toInt()
                tvSessionDuration.text = DateTimeUtils.formatDuration(durationMinutes)
                
                tvSessionSpots.text = "${session.joinedCount} / ${session.capacity} spots filled"
                btnViewDetails.setOnClickListener { onClick(session) }
                root.setOnClickListener { onClick(session) }
            }
        }
    }

    class HeaderViewHolder(val binding: ItemMySessionDateheaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    class SessionViewHolder(val binding: ItemSessionListBinding) :
        RecyclerView.ViewHolder(binding.root)

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<MySessionListItem>() {
            override fun areItemsTheSame(old: MySessionListItem, new: MySessionListItem): Boolean {
                return if (old is MySessionListItem.Header && new is MySessionListItem.Header) {
                    old.label == new.label
                } else if (old is MySessionListItem.SessionItem && new is MySessionListItem.SessionItem) {
                    old.session.id == new.session.id
                } else false
            }

            override fun areContentsTheSame(old: MySessionListItem, new: MySessionListItem): Boolean {
                return old == new
            }
        }
    }
}

sealed class MySessionListItem {
    data class Header(val label: String) : MySessionListItem()
    data class SessionItem(val session: Session) : MySessionListItem()
}

/**
 * The sessions list underneath the calendar in §7.6, using a smaller card
 * style (`item_session_calendar`).
 */
class CalendarSessionAdapter(
    private val onClick: (Session) -> Unit,
) : ListAdapter<Session, CalendarSessionAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemSessionCalendarBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemSessionCalendarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = getItem(position)
        holder.binding.apply {
            tvSessionTitle.text = session.title
            tvSessionTime.text = DateTimeUtils.formatTime(session.startTimeMillis)
            tvSessionLocation.text = session.locationName
            tvSessionSpots.text = "${session.joinedCount} / ${session.capacity} spots filled"
            
            val durationMinutes = ((session.endTimeMillis - session.startTimeMillis) / 60000).toInt()
            tvSessionDuration.text = DateTimeUtils.formatDuration(durationMinutes)

            btnViewDetails.setOnClickListener { onClick(session) }
            root.setOnClickListener { onClick(session) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Session>() {
            override fun areItemsTheSame(old: Session, new: Session) = old.id == new.id
            override fun areContentsTheSame(old: Session, new: Session) = old == new
        }
    }
}

/**
 * The calendar half of §7.6 — one month of day cells.
 */
class CalendarDayAdapter(
    private val onDayClick: (LocalDate) -> Unit,
) : ListAdapter<CalendarDayAdapter.Day, CalendarDayAdapter.ViewHolder>(DIFF) {

    private var selectedDate: LocalDate? = null

    data class Day(
        val date: LocalDate,
        val sessionCount: Int,
        val inCurrentMonth: Boolean,
    )

    class ViewHolder(val binding: ItemCalendarDayBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val day = getItem(position)
        holder.binding.apply {
            tvDayNumber.text = day.date.dayOfMonth.toString()
            
            // Selection highlight
            val isSelected = day.date == selectedDate
            tvDayNumber.setBackgroundResource(
                if (isSelected) R.drawable.bg_calendar_selected else 0
            )
            tvDayNumber.setTextColor(root.context.getColor(R.color.graphite))

            // Bold if in current month
            val typeFace = if (day.inCurrentMonth) {
                ResourcesCompat.getFont(root.context, R.font.pjsans_bold)
            } else {
                ResourcesCompat.getFont(root.context, R.font.pjsans_regular)
            }
            tvDayNumber.typeface = typeFace
            
            // Faint color for days not in current month
            tvDayNumber.alpha = if (day.inCurrentMonth) 1.0f else 0.4f

            // Busy highlight
            if (day.sessionCount > 0 && !isSelected) {
                tvDayNumber.setBackgroundResource(R.drawable.bg_calendar_busy)
            } else if (!isSelected) {
                tvDayNumber.background = null
            }

            root.setOnClickListener {
                val oldSelected = selectedDate
                selectedDate = day.date
                onDayClick(day.date)
                
                // Refresh affected items
                val oldPos = currentList.indexOfFirst { it.date == oldSelected }
                if (oldPos != -1) notifyItemChanged(oldPos)
                notifyItemChanged(position)
            }
        }
    }

    fun setSelected(date: LocalDate) {
        val oldSelected = selectedDate
        selectedDate = date
        val oldPos = currentList.indexOfFirst { it.date == oldSelected }
        val newPos = currentList.indexOfFirst { it.date == date }
        if (oldPos != -1) notifyItemChanged(oldPos)
        if (newPos != -1) notifyItemChanged(newPos)
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Day>() {
            override fun areItemsTheSame(old: Day, new: Day) = old.date == new.date
            override fun areContentsTheSame(old: Day, new: Day) = old == new
        }
    }
}
