package com.studyfinder.app.util

import androidx.fragment.app.Fragment
import com.google.android.material.transition.MaterialFadeThrough

/**
 * One shared screen-change animation (§2.1 "Animation"). Call from a
 * fragment's onCreate so entering/leaving a destination fades through instead
 * of snapping. Pairs with the per-list layoutAnimation on the RecyclerViews.
 */
fun Fragment.applyFadeThroughTransitions() {
    enterTransition = MaterialFadeThrough()
    exitTransition = MaterialFadeThrough()
    reenterTransition = MaterialFadeThrough()
    returnTransition = MaterialFadeThrough()
}
