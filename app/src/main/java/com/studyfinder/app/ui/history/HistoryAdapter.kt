package com.studyfinder.app.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.studyfinder.app.R
import com.studyfinder.app.databinding.ItemHistoryRowBinding
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.SessionStatus
import com.studyfinder.app.util.DateTimeUtils

class HistoryAdapter(
    private val onClick: (Session) -> Unit
) : ListAdapter<Session, HistoryAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemHistoryRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemHistoryRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = getItem(position)
        holder.binding.apply {
            tvSessionTitle.text = session.title
            tvSessionTime.text = DateTimeUtils.formatDateTime(session.startTimeMillis)
            tvSessionLocation.text = session.locationName
            
            val isCancelled = session.status == SessionStatus.CANCELLED
            tvStatus.visibility = if (isCancelled) View.VISIBLE else View.GONE
            
            if (isCancelled) {
                row.setBackgroundResource(R.drawable.bg_dashed_card)
                row.alpha = 0.5f
            } else {
                row.setBackgroundResource(R.drawable.bg_whitebox)
                row.alpha = 1.0f
            }

            tagContainer.removeAllViews()
            addTag(tagContainer, session.courseCategory.wire, R.color.ginkgo_yellow)
            addTag(tagContainer, session.tagType.wire, R.color.light_blue)

            root.setOnClickListener { onClick(session) }
        }
    }

    private fun addTag(group: ViewGroup, text: String, colorRes: Int) {
        val chip = Chip(group.context).apply {
            this.text = text
            setChipBackgroundColorResource(colorRes)
            setTextColor(group.context.getColor(R.color.graphite))
            chipStrokeWidth = 2f
            setChipStrokeColorResource(R.color.graphite)
            chipCornerRadius = 20f
            isCloseIconVisible = false
        }
        group.addView(chip)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Session>() {
            override fun areItemsTheSame(old: Session, new: Session) = old.id == new.id
            override fun areContentsTheSame(old: Session, new: Session) = old == new
        }
    }
}
