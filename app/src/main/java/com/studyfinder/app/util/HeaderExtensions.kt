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
    rightBtnIcon: Int? = null,
    onRightBtnClick: (() -> Unit)? = null
) {
    val navController = findNavController()
    
    binding.screenTitle.text = title
    
    // Back button wiring
    binding.backBtnContainer.setOnClickListener {
        navController.popBackStack()
    }
    
    // Header button logic
    when {
        showHistory -> {
            binding.rightmostBtnContainer.visibility = View.VISIBLE
            binding.rightmostBtn.setImageResource(R.drawable.ic_history)
            binding.rightmostBtn.imageTintList = null // Use original icon color if needed, or keep themed
            
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
            // If it's the block icon, we might want to disable the default tint to show its red color
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
}

private fun Fragment.currentDestinationId(): Int? {
    return findNavController().currentDestination?.id
}
