package com.github.kr328.clash.zashboard

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.MainActivity
import com.github.kr328.clash.SettingsActivity
import com.github.kr328.clash.design.R
import com.github.kr328.clash.util.stopClashService
import kotlin.math.roundToInt

/**
 * What the round menu does, from the screen it is on: minimize or show the
 * dashboard again, Home and Settings (over the dashboard only — minimized,
 * the tabs are right there), close it (the page is released) and stop the VPN.
 */
internal class ZashboardActions(private val activity: Activity) {
    fun minimize() = ZashboardSession.minimize()

    fun show() = ZashboardSession.open(activity)

    /** Minimized, Home underneath. */
    fun home() {
        ZashboardSession.minimize()
        activity.startActivity(homeIntent())
    }

    /** Minimized, Settings underneath (above Home, as when opened from its tab). */
    fun settings() {
        ZashboardSession.minimize()
        activity.startActivities(arrayOf(homeIntent(), Intent(activity, SettingsActivity::class.java)))
    }

    fun close() = ZashboardSession.close()

    fun stopVpn() {
        ZashboardSession.close()
        activity.stopClashService()
        activity.startActivity(homeIntent())
    }

    private fun homeIntent() = Intent(activity, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

/**
 * The round button over Zashboard while it is open (shown or minimized), as
 * on iOS: drag it anywhere, tap it for the menu. One place on every screen —
 * where it was dragged to, or the bottom corner above the tab bar.
 */
@Composable
internal fun ZashboardMenuButton(shown: Boolean, actions: ZashboardActions) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val saved by ZashboardSession.buttonOffset.collectAsState()
    var drag by remember { mutableStateOf(Offset.Zero) }
    var menu by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        with(density) {
            val size = 56.dp.toPx()
            val margin = 12.dp.toPx()
            val bottomMargin = 96.dp.toPx()
            val minX = insets.calculateLeftPadding(direction).toPx() + margin
            val maxX = constraints.maxWidth - insets.calculateRightPadding(direction).toPx() - margin - size
            val minY = insets.calculateTopPadding().toPx() + margin
            val maxY = constraints.maxHeight - insets.calculateBottomPadding().toPx() - bottomMargin - size
            fun clamp(p: Offset) = Offset(p.x.coerceIn(minX, maxOf(minX, maxX)), p.y.coerceIn(minY, maxOf(minY, maxY)))
            val anchor = clamp(saved ?: Offset(maxX, maxY))
            val at = anchor + drag

            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
                Surface(
                    onClick = { menu = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 6.dp,
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .size(56.dp)
                        // A tap opens the menu; a deliberate drag moves the button.
                        .pointerInput(anchor, maxX, maxY) {
                            detectDragGestures(
                                onDragEnd = {
                                    ZashboardSession.buttonOffset.value = clamp(anchor + drag)
                                    drag = Offset.Zero
                                },
                                onDragCancel = { drag = Offset.Zero },
                            ) { change, amount ->
                                change.consume()
                                drag += amount
                            }
                        },
                ) {
                    Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_zashboard_overview),
                            contentDescription = stringResource(R.string.zashboard_menu),
                        )
                    }
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    shape = MaterialTheme.shapes.large,
                ) {
                    fun pick(action: () -> Unit) {
                        menu = false
                        action()
                    }
                    if (shown) {
                        MenuItem(R.string.zashboard_minimize, R.drawable.ic_baseline_fullscreen_exit) { pick(actions::minimize) }
                        MenuItem(R.string.home, R.drawable.ic_baseline_home) { pick(actions::home) }
                        MenuItem(R.string.settings, R.drawable.ic_baseline_settings) { pick(actions::settings) }
                    } else {
                        MenuItem(R.string.zashboard_show, R.drawable.ic_baseline_fullscreen) { pick(actions::show) }
                    }
                    HorizontalDivider()
                    MenuItem(R.string.zashboard_close, R.drawable.ic_baseline_close) { pick(actions::close) }
                    MenuItem(R.string.zashboard_stop_vpn, R.drawable.ic_baseline_stop, destructive = true) { pick(actions::stopVpn) }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(title: Int, icon: Int, destructive: Boolean = false, onClick: () -> Unit) {
    val colour = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(stringResource(title), color = colour) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, tint = colour) },
        onClick = onClick,
    )
}

/** The round menu over the app's screens while Zashboard is minimized. */
@Composable
internal fun ZashboardOverlay(activity: Activity) {
    val state by ZashboardSession.state.collectAsState()
    if (state == ZashboardSession.State.Minimized) {
        ZashboardMenuButton(shown = false, actions = remember(activity) { ZashboardActions(activity) })
    }
}
