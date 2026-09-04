package com.studyfinder.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.studyfinder.app.databinding.FragmentSplashBinding

/**
 * Decides where the app actually starts.
 *
 * Firebase persists the auth session across restarts, so the start
 * destination cannot be a static value in nav_graph.xml. The routing decision
 * lives in [AuthViewModel.resolveStartRoute]; this fragment only navigates and
 * pops itself off the back stack.
 *
 *  - no signed-in user                    -> Login
 *  - signed in, but no communityId set    -> Community Selection
 *  - signed in with a community           -> Home
 */
class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by viewModels()

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

        authViewModel.resolveStartRoute { route ->
            // The callback can arrive after the view is torn down.
            if (_binding == null || !isAdded) return@resolveStartRoute
            when (route) {
                AuthViewModel.StartRoute.LOGIN -> goToLogin()
                AuthViewModel.StartRoute.COMMUNITY_SELECTION -> goToCommunitySelection()
                AuthViewModel.StartRoute.HOME -> goToHome()
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
