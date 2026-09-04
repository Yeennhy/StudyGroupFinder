package com.studyfinder.app.ui.common

import android.view.View
import android.view.ViewGroup
import com.studyfinder.app.util.UiState
import com.studyfinder.app.util.gone
import com.studyfinder.app.util.visible

/**
 * Switches loading / empty / error / offline state views for a [UiState].
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
