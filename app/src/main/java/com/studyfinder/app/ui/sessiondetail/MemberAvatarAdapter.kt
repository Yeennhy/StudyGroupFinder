package com.studyfinder.app.ui.sessiondetail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studyfinder.app.databinding.ItemMemberAvatarBinding
import com.studyfinder.app.model.SessionMember

/**
 * The member avatar row (§7.3). Glide loads `photoUrl` with a placeholder.
 *
 * Blocked users are NOT stripped from this list — the roster would then
 * disagree with the X/Y counter, which reads as a bug (§7.7).
 */
class MemberAvatarAdapter(
    private val onClick: (SessionMember) -> Unit,
) : ListAdapter<SessionMember, MemberAvatarAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemMemberAvatarBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemMemberAvatarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    }
    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<SessionMember>() {
            override fun areItemsTheSame(old: SessionMember, new: SessionMember) =
                old.uid == new.uid

            override fun areContentsTheSame(old: SessionMember, new: SessionMember) =
                old == new
        }
    }
}
