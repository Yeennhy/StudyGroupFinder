package com.studyfinder.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.studyfinder.app.databinding.FragmentLoginBinding

/**
 * Email/password sign-in (§7.0).
 *
 * Auth errors must be distinguishable — wrong password, unknown email,
 * malformed email and network failure are four different FirebaseAuthException
 * codes and users hit all four.
 */
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // §7.0 Implementation: sign-in wiring.
    }

    private fun goToSignup() {
        findNavController().navigate(
            LoginFragmentDirections.actionLoginFragmentToSignupFragment()
        )
    }

    private fun goToForgotPassword() {
        findNavController().navigate(
            LoginFragmentDirections.actionLoginFragmentToForgotPasswordFragment()
        )
    }

    private fun goToCommunitySelection() {
        findNavController().navigate(
            LoginFragmentDirections.actionLoginFragmentToCommunitySelectionFragment(
                isEditMode = false
            )
        )
    }

    private fun goToHome() {
        findNavController().navigate(
            LoginFragmentDirections.actionLoginFragmentToHomeFragment()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
