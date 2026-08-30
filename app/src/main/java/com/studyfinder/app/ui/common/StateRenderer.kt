package com.studyfinder.app.ui.common

import android.view.View
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
        alpha = 0f
        animate().alpha(1f).setDuration(180L).start()
    }
}
