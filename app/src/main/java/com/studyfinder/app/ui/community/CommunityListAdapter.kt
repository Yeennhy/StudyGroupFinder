package com.studyfinder.app.ui.community

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studyfinder.app.databinding.ItemCommunityBinding
import com.studyfinder.app.model.Community

/** §7.1 — shows name, city, and a "verified" badge. */
class CommunityListAdapter(
    private val onClick: (Community) -> Unit,
) : ListAdapter<Community, CommunityListAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemCommunityBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemCommunityBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val community = getItem(position)
        val b = holder.binding

        b.tvCommunityName.text = community.name
        b.tvLocation.text = community.city.ifBlank { "—" }
        b.ivJoinCheck.visibility = if (community.verified) android.view.View.VISIBLE else android.view.View.GONE

        b.tvMembers.text = when {
            community.verified && community.domainWhitelist.isNotEmpty() ->
                "@${community.domainWhitelist.first()}"
            community.verified -> "Verified community"
            else -> "Open to everyone"
        }

        b.btnJoinCommunity.setOnClickListener { onClick(community) }
        b.root.setOnClickListener { onClick(community) }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Community>() {
            override fun areItemsTheSame(old: Community, new: Community) = old.id == new.id
            override fun areContentsTheSame(old: Community, new: Community) = old == new
        }
    }
}
