package com.github.kr328.clash.design.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.emptyFlow

/** Two columns from this width of the window on, as iOS's regular width. */
val TwoPaneWidth = 600.dp

/**
 * Drawn inside a column of a wide screen (a settings section beside the
 * list): the screen's own bars and insets give way to the column's.
 */
val LocalInPane = staticCompositionLocalOf { false }

/**
 * How wide the window is, the way iOS decides between its iPhone layout and
 * two columns (`horizontalSizeClass`): by the window, never by the device —
 * a tablet in a narrow window lays out as a phone, an unfolded foldable as
 * two columns.
 *
 * @property twoPane the window is [TwoPaneWidth] wide or more.
 * @property foldable the window has a fold or hinge (Jetpack WindowManager's
 *   [FoldingFeature]): a foldable unfolded, as iPhone Duo — no Profiles tab,
 *   the phone's bar stays. Without one in two columns: a tablet, as iPad.
 * @property foldStart where a fold across the width begins, from the
 *   window's left edge; the columns split there.
 * @property foldTop where a fold across the height begins (half-open as a
 *   laptop), from the window's top edge: nothing to press is laid on it.
 * @property foldThickness the fold's own width: zero for a seamless screen,
 *   the hinge for a separating one.
 */
@Immutable
data class WideLayout(
    val twoPane: Boolean,
    val foldable: Boolean,
    val foldStart: Dp? = null,
    val foldTop: Dp? = null,
    val foldThickness: Dp = 0.dp,
) {
    /** Two columns without a fold: a tablet, with the Profiles tab at the top, as iPad. */
    val tablet: Boolean get() = twoPane && !foldable

    companion object {
        val Phone = WideLayout(twoPane = false, foldable = false)
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** The window's [WideLayout], following folding, unfolding and resizing. */
@Composable
fun rememberWideLayout(): WideLayout {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val activity = LocalContext.current.activity()
    val flow = remember(activity) {
        activity?.let { WindowInfoTracker.getOrCreate(it).windowLayoutInfo(it) } ?: emptyFlow()
    }
    val info by flow.collectAsState(initial = WindowLayoutInfo(emptyList()))
    val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()

    val twoPane = configuration.screenWidthDp.dp >= TwoPaneWidth
    // A foldable even where this window does not cross the fold.
    val context = LocalContext.current
    val hinge = remember(context) {
        android.os.Build.VERSION.SDK_INT >= 30 &&
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
    }
    if (fold == null) return WideLayout(twoPane = twoPane, foldable = hinge)

    // Bounds are in the window's pixels.
    val bounds = fold.bounds
    return with(density) {
        when (fold.orientation) {
            FoldingFeature.Orientation.VERTICAL -> WideLayout(
                twoPane = twoPane,
                foldable = true,
                foldStart = bounds.left.toDp(),
                foldThickness = bounds.width().toDp(),
            )
            else -> WideLayout(
                twoPane = twoPane,
                foldable = true,
                // Only a half-open fold divides the screen in two; flat, it is just a crease.
                foldTop = bounds.top.toDp().takeIf { fold.state == FoldingFeature.State.HALF_OPENED || fold.isSeparating },
                foldThickness = bounds.height().toDp(),
            )
        }
    }
}
