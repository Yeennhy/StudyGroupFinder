package com.studyfinder.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.studyfinder.app.databinding.FragmentSuccessBinding

class SuccessFragment : Fragment() {

    private var _binding: FragmentSuccessBinding? = null
    private val binding get() = _binding!!
    private val args: SuccessFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSuccessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.message.text = args.message
        binding.subtitle.text = args.subtitle
        binding.btnBack.text = args.buttonText

        binding.btnBack.setOnClickListener {
            if (args.isSignupSuccess) {
                findNavController().navigate(
                    SuccessFragmentDirections.actionSuccessFragmentToCommunitySelectionFragment()
                )
            } else if (args.message == "Session Created!" || args.message == "Invitation Sent!" || args.message == "History Exported!") {
                findNavController().navigate(
                    SuccessFragmentDirections.actionSuccessFragmentToHomeFragment()
                )
            } else {
                findNavController().navigate(
                    SuccessFragmentDirections.actionSuccessFragmentToLoginFragment()
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
