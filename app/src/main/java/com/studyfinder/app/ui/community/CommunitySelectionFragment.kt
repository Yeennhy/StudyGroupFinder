package com.studyfinder.app.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.studyfinder.app.databinding.FragmentCommunitySelectionBinding
import com.studyfinder.app.util.setupHeader

/**
 * Community selection (§7.1) — two entry points, two data sources.
 *
 * **Entry points:** first login (`isEditMode = false`, continues to Home) and
 * "change community" from Profile (`isEditMode = true`, pops back).
 *
 * **Data sources — this distinction is the course's API requirement:**
 *  - initial "browse all" list  -> Retrofit REST call (§7.1)
 *  - search / filter as you type -> Firestore SDK query
 *
 * Do not quietly collapse the first into an SDK query; it is the only REST
 * call in the app.
 */
class CommunitySelectionFragment : Fragment() {

    private var _binding: FragmentCommunitySelectionBinding? = null
    private val binding get() = _binding!!
    private val args: CommunitySelectionFragmentArgs by navArgs()
    private val viewModel: CommunityViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCommunitySelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeader(
            binding.appHeader,
            "Select Community",
            showHistory = false,
            showBackBtn = args.isEditMode,
            showAvatar = false
        )
        // §7.1 Implementation: REST browse list, search, etc.
    }

    /** Join succeeded. First-time users continue; editors just go back. */
    private fun onJoined() {
        if (args.isEditMode) {
            findNavController().popBackStack()
        } else {
            findNavController().navigate(
                CommunitySelectionFragmentDirections
                    .actionCommunitySelectionFragmentToHomeFragment()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
