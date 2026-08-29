package com.studyfinder.app.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studyfinder.app.databinding.ItemSessionHistoryBinding
import com.studyfinder.app.model.Session

/** History rows — time + location + tags (§7.6). Cancelled rows are struck through. */
class HistoryAdapter(
    private val onClick: (Session) -> Unit,
) : ListAdapter<Session, HistoryAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemSessionHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemSessionHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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
