package com.studyfinder.app.ui.common

import android.view.View
import com.studyfinder.app.util.UiState
import com.studyfinder.app.util.gone
import com.studyfinder.app.util.visible

/**
 * Common logic to switch visibility of loading / empty / error / offline
 * state views backed by a [UiState] (§2.1).
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
