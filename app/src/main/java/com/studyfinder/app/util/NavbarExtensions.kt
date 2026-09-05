package com.studyfinder.app.util

import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.studyfinder.app.R
import com.studyfinder.app.databinding.FragmentNavbarBinding

fun Fragment.setupNavbar(binding: FragmentNavbarBinding) {
    val navController = findNavController()
    val currentDestinationId = navController.currentDestination?.id

    // Reset all tabs to default state
    val tabs = listOf(
        Triple(binding.navHome, binding.tvNavHome, R.id.homeFragment),
        Triple(binding.navSessions, binding.tvNavSessions, R.id.mySessionsFragment),
        Triple(binding.navInbox, binding.tvNavInbox, R.id.inboxFragment),
        Triple(binding.navProfile, binding.tvNavProfile, R.id.profileFragment)
    )

    val pjsansBold = ResourcesCompat.getFont(requireContext(), R.font.pjsans_bold)
    val pjsansRegular = ResourcesCompat.getFont(requireContext(), R.font.pjsans_regular)

    tabs.forEach { (layout, textView, destinationId) ->
        if (currentDestinationId == destinationId) {
            layout.setBackgroundResource(R.drawable.bg_yellow_btn_lite)
            textView.typeface = pjsansBold
        } else {
            layout.background = null
            textView.typeface = pjsansRegular
        }

        layout.setOnClickListener {
            if (currentDestinationId != destinationId) {
                navController.navigate(
                    destinationId,
                    null,
                    navOptions { popUpTo(R.id.homeFragment) { inclusive = false } }
                )
            }
        }
    }
}
