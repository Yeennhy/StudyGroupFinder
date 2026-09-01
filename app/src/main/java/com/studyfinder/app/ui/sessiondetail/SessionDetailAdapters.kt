package com.studyfinder.app.ui.sessiondetail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.studyfinder.app.R
import com.studyfinder.app.databinding.ItemAttendeeRowBinding
import com.studyfinder.app.databinding.ItemFileRowBinding
import com.studyfinder.app.model.SessionMember
import com.studyfinder.app.model.UserProfile

class AttendeeAdapter(
    private val onClick: (SessionMember) -> Unit
) : ListAdapter<SessionMember, AttendeeAdapter.ViewHolder>(DIFF) {

    private var blockedUids: Set<String> = emptySet()

    fun setBlockedUids(uids: Set<String>) {
        if (blockedUids != uids) {
            blockedUids = uids
            notifyDataSetChanged()
        }
    }

    class ViewHolder(val binding: ItemAttendeeRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemAttendeeRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = getItem(position)
        holder.binding.apply {
            tvName.text = member.profile?.name ?: "Unknown"
            tvId.text = member.profile?.studentId ?: member.uid
            
            member.profile?.photoUrl?.let { url ->
                if (url.isNotBlank()) {
                    Glide.with(root.context).load(url).circleCrop().into(ivAvatar)
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_profile)
                }
            } ?: ivAvatar.setImageResource(R.drawable.ic_profile)

            tvBlockedBadge.visibility = if (blockedUids.contains(member.uid)) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

            root.setOnClickListener { onClick(member) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SessionMember>() {
            override fun areItemsTheSame(old: SessionMember, new: SessionMember) = old.uid == new.uid
            override fun areContentsTheSame(old: SessionMember, new: SessionMember) = old == new
        }
    }
}

class MaterialAdapter(
    private val onClick: (String) -> Unit
) : ListAdapter<String, MaterialAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemFileRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemFileRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val url = getItem(position)
        val fileName = url.substringAfterLast("/").substringBefore("?").replace("%20", " ")
        holder.binding.apply {
            tvfileName.text = fileName
            root.setOnClickListener { onClick(url) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(old: String, new: String) = old == new
            override fun areContentsTheSame(old: String, new: String) = old == new
        }
    }
}

class InviteStudentAdapter(
    private val onInvite: (UserProfile) -> Unit
) : ListAdapter<UserProfile, InviteStudentAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemAttendeeRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemAttendeeRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = getItem(position)
        holder.binding.apply {
            tvName.text = user.name
            tvId.text = user.studentId
            
            if (user.photoUrl.isNotBlank()) {
                Glide.with(root.context).load(user.photoUrl).circleCrop().into(ivAvatar)
            } else {
                ivAvatar.setImageResource(R.drawable.ic_profile)
            }

            root.setOnClickListener { onInvite(user) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<UserProfile>() {
            override fun areItemsTheSame(old: UserProfile, new: UserProfile) = old.uid == new.uid
            override fun areContentsTheSame(old: UserProfile, new: UserProfile) = old == new
        }
    }
}
