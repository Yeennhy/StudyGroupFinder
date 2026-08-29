package com.studyfinder.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.studyfinder.app.databinding.FragmentSignupBinding

/**
 * Account creation (§7.0).
 *
 * Order matters: create the Auth account -> write `users/{uid}` -> send the
 * verification email -> route to Community Selection. Writing the user
 * document before navigating is required because every downstream screen
 * reads it.
 */
class SignupFragment : Fragment() {

    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // §7.0 Implementation: sign-up wiring.
    }

    private fun goToCommunitySelection() {
        findNavController().navigate(
            SignupFragmentDirections.actionSignupFragmentToCommunitySelectionFragment(
                isEditMode = false
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
