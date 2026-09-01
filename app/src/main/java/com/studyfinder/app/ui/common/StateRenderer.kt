package com.studyfinder.app.ui.common

import android.view.View
import android.view.ViewGroup
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.studyfinder.app.util.UiState
import com.studyfinder.app.util.gone
import com.studyfinder.app.util.visible

/**
 * Common logic to switch visibility of loading / empty / error / offline
 * state views backed by a [UiState] (§2.1). Whatever view becomes visible
 * fades in, so state transitions never snap.
 */
object StateRenderer {

    fun <T> render(
        state: UiState<T>,
        loadingView: View? = null,
        emptyView: View? = null,
        errorView: View? = null,
        offlineView: View? = null,
        contentView: View? = null,
        transitionContainer: ViewGroup? = null,
    ) {
        // Apply easing transition to the target container or fallback to common parent
        val parent = transitionContainer
            ?: loadingView?.parent as? ViewGroup
            ?: emptyView?.parent as? ViewGroup
            ?: errorView?.parent as? ViewGroup
            ?: contentView?.parent as? ViewGroup

        if (parent != null) {
            val transition = AutoTransition().apply {
                duration = 250L
            }
            TransitionManager.beginDelayedTransition(parent, transition)
        }

        loadingView?.gone()
        emptyView?.gone()
        errorView?.gone()
        offlineView?.gone()
        contentView?.gone()

        when (state) {
            is UiState.Loading -> loadingView?.visible()
            is UiState.Empty -> emptyView?.visible()
            is UiState.Error -> errorView?.visible()
            is UiState.Offline -> {
                offlineView?.visible()
                contentView?.visible()
            }
            is UiState.Success -> contentView?.visible()
        }
    }
}
