package com.studyfinder.app.ui.mysessions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studyfinder.app.databinding.ItemCalendarDayBinding
import com.studyfinder.app.databinding.ItemSessionListBinding
import com.studyfinder.app.model.Session
import java.time.LocalDate

/** The list half of §7.6. */
class MySessionListAdapter(
    private val onClick: (Session) -> Unit,
) : ListAdapter<Session, MySessionListAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemSessionListBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemSessionListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        TODO("§7.6")
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Session>() {
            override fun areItemsTheSame(old: Session, new: Session) = old.id == new.id
            override fun areContentsTheSame(old: Session, new: Session) = old == new
        }
    }
}

/**
 * The calendar half of §7.6 — one month of day cells in a
 * `GridLayoutManager(context, 7)`. Each cell shows the date and a dot when
 * that day has sessions; tapping filters the list underneath.
 *
 * Structurally the same grid as the activity graph in §7.7 — build one,
 * adapt it for the other.
 */
class CalendarDayAdapter(
    private val onDayClick: (LocalDate) -> Unit,
) : ListAdapter<CalendarDayAdapter.Day, CalendarDayAdapter.ViewHolder>(DIFF) {

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
        TODO("§7.6")
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Day>() {
            override fun areItemsTheSame(old: Day, new: Day) = old.date == new.date
            override fun areContentsTheSame(old: Day, new: Day) = old == new
        }
    }
}
