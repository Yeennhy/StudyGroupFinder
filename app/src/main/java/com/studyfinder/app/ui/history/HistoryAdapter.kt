package com.studyfinder.app.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studyfinder.app.R
import com.studyfinder.app.databinding.ItemHistoryRowBinding
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.SessionStatus
import com.studyfinder.app.util.DateTimeUtils
import java.text.SimpleDateFormat
import java.util.Locale

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
            
            val durationMinutes = ((session.endTimeMillis - session.startTimeMillis) / 60000).toInt()
            val timeText = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(session.startTimeMillis)
            tvSessionTime.text = "$timeText • ${DateTimeUtils.formatDuration(durationMinutes)}"
            
            tvSessionLocation.text = session.locationName
            
            val isCancelled = session.status == SessionStatus.CANCELLED
            if (isCancelled) {
                tvStatus.text = "CANCELLED"
                tvStatus.setBackgroundResource(R.drawable.bg_cancelled_pill)
                row.setBackgroundResource(R.drawable.bg_dashed_card)
                row.alpha = 0.5f
            } else {
                tvStatus.text = "FINISHED"
                tvStatus.setBackgroundResource(R.drawable.bg_finished_pill)
                row.setBackgroundResource(R.drawable.bg_whitebox)
                row.alpha = 1.0f
            }

            root.setOnClickListener { onClick(session) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Session>() {
            override fun areItemsTheSame(old: Session, new: Session) = old.id == new.id
            override fun areContentsTheSame(old: Session, new: Session) = old == new
        }
    }
}
