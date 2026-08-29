package com.studyfinder.app.ui.auth

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.studyfinder.app.databinding.FragmentSignupBinding
import com.studyfinder.app.util.ActionResult

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
        
        // Clear previous state
        viewModel.clearResult()
        binding.tvEmailError.isVisible = false
        binding.tvPasswordError.isVisible = false

        // Apply underlines
        binding.tvSignUp.paintFlags = binding.tvSignUp.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        binding.btnLogin.setOnClickListener {
            val name = binding.etID.text.toString().trim() // Full Name hint
            val studentId = binding.etName.text.toString().trim() // Student ID hint
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val cfPassword = binding.etCfPassword.text.toString().trim()

            // Reset errors
            binding.tvEmailError.isVisible = false
            binding.tvPasswordError.isVisible = false

            when {
                name.isEmpty() || studentId.isEmpty() || email.isEmpty() || password.isEmpty() || cfPassword.isEmpty() -> {
                    binding.tvPasswordError.text = "All fields are required"
                    binding.tvPasswordError.isVisible = true
                }
                password != cfPassword -> {
                    binding.tvPasswordError.text = "Confirmation doesn't match password"
                    binding.tvPasswordError.isVisible = true
                }
                else -> {
                    viewModel.signUp(email, password, name, studentId)
                }
            }
        }

        binding.tvSignUp.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.tvSignUpPrompt.setOnClickListener {
            findNavController().popBackStack()
        }

        // Observe results
        viewModel.result.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ActionResult.Idle -> {
                    binding.tvEmailError.isVisible = false
                    binding.tvPasswordError.isVisible = false
                }
                is ActionResult.Success -> {
                    Toast.makeText(context, "Account created!", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(
                        SignupFragmentDirections.actionSignupFragmentToSuccessFragment(
                            message = "Account Created",
                            subtitle = "Welcome to StudyCohort!",
                            buttonText = "Select Community",
                            isSignupSuccess = true
                        )
                    )
                }
                is ActionResult.Failure -> {
                    handleAuthError(result.message)
                }
            }
        }
    }

    private fun handleAuthError(message: String) {
        val lowerMessage = message.lowercase()
        when {
            lowerMessage.contains("password") -> {
                binding.tvPasswordError.text = message
                binding.tvPasswordError.isVisible = true
            }
            else -> {
                binding.tvEmailError.text = message
                binding.tvEmailError.isVisible = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
