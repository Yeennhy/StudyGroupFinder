package com.studyfinder.app.ui.auth

import android.graphics.Paint
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.studyfinder.app.R
import com.studyfinder.app.databinding.FragmentLoginBinding
import com.studyfinder.app.util.ActionResult

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
        
        // Clear previous state
        viewModel.clearResult()
        binding.tvEmailError.isVisible = false
        binding.tvPasswordError.isVisible = false

        // Apply underlines to links
        binding.tvForgotPassword.paintFlags = binding.tvForgotPassword.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.tvSignUp.paintFlags = binding.tvSignUp.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        // Click listeners
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            
            // Reset errors
            binding.tvEmailError.isVisible = false
            binding.tvPasswordError.isVisible = false

            if (email.isEmpty() || password.isEmpty()) {
                binding.tvPasswordError.text = "All fields are required"
                binding.tvPasswordError.isVisible = true
            } else {
                binding.stateLoading.root.isVisible = true
                viewModel.signIn(email, password)
            }
        }

        binding.tvForgotPassword.setOnClickListener { goToForgotPassword() }
        binding.tvSignUp.setOnClickListener { goToSignup() }

        setupPasswordToggle(binding.etPassword, binding.btnTogglePassword)

        // Observe results
        viewModel.result.observe(viewLifecycleOwner) { result ->
            if (result !is ActionResult.Idle) binding.stateLoading.root.isVisible = false
            when (result) {
                is ActionResult.Idle -> {
                    binding.tvEmailError.isVisible = false
                    binding.tvPasswordError.isVisible = false
                }
                is ActionResult.Success -> {
                    // Navigate to Home (Repository handles checking if community is set, 
                    // but for now we follow the resolved route logic or just Home)
                    goToHome()
                }
                is ActionResult.Failure -> {
                    handleAuthError(result.message)
                }
            }
        }
    }

    private fun handleAuthError(message: String) {
        // Simple heuristic to show error in the right field
        val lowerMessage = message.lowercase()
        when {
            lowerMessage.contains("password") -> {
                binding.tvPasswordError.text = message
                binding.tvPasswordError.isVisible = true
                binding.tvEmailError.isVisible = false
            }
            else -> {
                binding.tvEmailError.text = message
                binding.tvEmailError.isVisible = true
                binding.tvPasswordError.isVisible = false
            }
        }
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

    private fun setupPasswordToggle(editText: EditText, toggleButton: ImageButton) {
        var isPasswordVisible = false
        toggleButton.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                editText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                toggleButton.setImageResource(R.drawable.ic_eye_cross)
            } else {
                editText.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleButton.setImageResource(R.drawable.ic_eye)
            }
            editText.setSelection(editText.text.length)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
