package com.studyfinder.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.databinding.FragmentSplashBinding
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Decides where the app actually starts (§7.0).
 *
 * Firebase persists the auth session across restarts, so the start
 * destination cannot be a static value in nav_graph.xml. This fragment routes
 * three ways and pops itself off the back stack either way:
 *
 *  - no signed-in user                    -> Login
 *  - signed in, but no communityId set    -> Community Selection
 *  - signed in with a community           -> Home
 */
class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val authRepository = ServiceLocator.authRepository
        val profileRepository = ServiceLocator.profileRepository

        lifecycleScope.launch {
            val uid = authRepository.currentUid
            if (uid == null) {
                goToLogin()
            } else {
                // Check communityId in profile
                profileRepository.observeCurrentProfile().first { state ->
                    state !is UiState.Loading
                }.let { state ->
                    when (state) {
                        is UiState.Success -> {
                            if (state.data.hasCommunity) {
                                goToHome()
                            } else {
                                goToCommunitySelection()
                            }
                        }
                        else -> {
                            // If profile fetch fails or is empty, assume no community yet
                            goToCommunitySelection()
                        }
                    }
                }
            }
        }
    }

    private fun goToLogin() {
        findNavController().navigate(
            SplashFragmentDirections.actionSplashFragmentToLoginFragment()
        )
    }

    private fun goToCommunitySelection() {
        findNavController().navigate(
            SplashFragmentDirections.actionSplashFragmentToCommunitySelectionFragment(
                isEditMode = false
            )
        )
    }

    private fun goToHome() {
        findNavController().navigate(
            SplashFragmentDirections.actionSplashFragmentToHomeFragment()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
