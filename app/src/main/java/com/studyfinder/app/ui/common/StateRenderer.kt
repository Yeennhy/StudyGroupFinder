package com.studyfinder.app.ui.common

import android.view.View
import android.view.ViewGroup
import com.studyfinder.app.util.UiState
import com.studyfinder.app.util.gone
import com.studyfinder.app.util.visible

/**
 * Switches loading / empty / error / offline state views for a [UiState] (§2.1).
 *
 * Visibility is toggled immediately (every non-target view is hidden before the
 * target is shown, so two state views can never be on screen at once), then the
 * view that becomes visible fades in. Deliberately NOT using
 * TransitionManager cross-fades here — during a fast sequence of state changes
 * (e.g. search-as-you-type) the outgoing and incoming views overlap mid-fade.
 */
object StateRenderer {

    fun <T> render(
        state: UiState<T>,
        loadingView: View? = null,
        emptyView: View? = null,
        errorView: View? = null,
        offlineView: View? = null,
        contentView: View? = null,
        /** Accepted for source compatibility; unused. */
        transitionContainer: ViewGroup? = null,
    ) {
        loadingView?.gone()
        emptyView?.gone()
        errorView?.gone()
        offlineView?.gone()
        contentView?.gone()

        when (state) {
            is UiState.Loading -> loadingView.showFading()
            is UiState.Empty -> emptyView.showFading()
            is UiState.Error -> errorView.showFading()
            is UiState.Offline -> {
                offlineView.showFading()
                contentView.showFading()
            }
            is UiState.Success -> contentView.showFading()
        }
    }

    private fun View?.showFading() {
        this ?: return
        visible()
        clearAnimation()
        alpha = 0f
        animate().alpha(1f).setDuration(160L).start()
    }
}
