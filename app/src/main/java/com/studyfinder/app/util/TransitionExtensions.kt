package com.studyfinder.app.util

import androidx.fragment.app.Fragment
import com.google.android.material.transition.MaterialFadeThrough

/**
 * Sets up shared screen-change animations for a Fragment.
 */
fun Fragment.applyFadeThroughTransitions() {
    enterTransition = MaterialFadeThrough()
    exitTransition = MaterialFadeThrough()
    reenterTransition = MaterialFadeThrough()
    returnTransition = MaterialFadeThrough()
}
