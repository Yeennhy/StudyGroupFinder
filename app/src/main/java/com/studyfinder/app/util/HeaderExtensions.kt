package com.studyfinder.app.util

import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import com.studyfinder.app.R
import com.studyfinder.app.databinding.LayoutAppHeaderBinding
import com.studyfinder.app.ui.home.HomeFragmentDirections
import com.studyfinder.app.ui.inbox.InboxFragmentDirections
import com.studyfinder.app.ui.mysessions.MySessionsFragmentDirections

/**
 * Common setup for the shared layout_app_header.
 */
fun Fragment.setupHeader(
    binding: LayoutAppHeaderBinding,
    title: String,
    showHistory: Boolean = false,
    showBackBtn: Boolean = true,
    showAvatar: Boolean = false,
    rightBtnIcon: Int? = null,
    onRightBtnClick: (() -> Unit)? = null,
    rightBtn2Icon: Int? = null,
    onRightBtn2Click: (() -> Unit)? = null
) {
    val navController = findNavController()
    
    binding.screenTitle.text = title
    
    // Back button wiring
    binding.backBtnContainer.visibility = if (showBackBtn) View.VISIBLE else View.GONE
    binding.backBtnContainer.setOnClickListener {
        navController.popBackStack()
    }

    // Avatar visibility
    binding.userAvatarContainer.visibility = if (showAvatar) View.VISIBLE else View.GONE
    
    // Header button logic
    when {
        showHistory -> {
            binding.rightmostBtnContainer.visibility = View.VISIBLE
            binding.rightmostBtn.setImageResource(R.drawable.ic_hourglass)
            binding.rightmostBtn.imageTintList = null
            
            binding.rightmostBtnContainer.setOnClickListener {
                val direction: NavDirections? = when (currentDestinationId()) {
                    R.id.homeFragment -> HomeFragmentDirections.actionHomeFragmentToHistoryFragment()
                    R.id.inboxFragment -> InboxFragmentDirections.actionInboxFragmentToHistoryFragment()
                    R.id.mySessionsFragment -> MySessionsFragmentDirections.actionMySessionsFragmentToHistoryFragment()
                    else -> null
                }
                direction?.let { navController.navigate(it) }
            }
        }
        rightBtnIcon != null -> {
            binding.rightmostBtnContainer.visibility = View.VISIBLE
            binding.rightmostBtn.setImageResource(rightBtnIcon)
            if (rightBtnIcon == R.drawable.ic_block) {
                binding.rightmostBtn.imageTintList = null
            }
            binding.rightmostBtnContainer.setOnClickListener {
                onRightBtnClick?.invoke()
            }
        }
        else -> {
            binding.rightmostBtnContainer.visibility = View.GONE
        }
    }

    // Second right button logic
    if (rightBtn2Icon != null) {
        binding.rightBtnContainer.visibility = View.VISIBLE
        binding.rightBtn.setImageResource(rightBtn2Icon)
        binding.rightBtnContainer.setOnClickListener {
            onRightBtn2Click?.invoke()
        }
    } else {
        binding.rightBtnContainer.visibility = View.GONE
    }
}

private fun Fragment.currentDestinationId(): Int? {
    return findNavController().currentDestination?.id
}
