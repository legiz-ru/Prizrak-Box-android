package com.github.kr328.clash.zashboard

import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.kr328.clash.BaseActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Zashboard over the whole app: the page under the status bar's strip, which
 * takes the page's own top colour, down to the navigation bar, with the round
 * menu over it. Back minimizes it, as the menu's first item does; the page
 * lives on in [ZashboardSession].
 */
class ZashboardActivity : BaseActivity() {
    /** The page's top colour; the dashboard's dark or light background until the page says. */
    private val topColour by lazy {
        MutableStateFlow(if (isNightTheme) Color(0xFF212529) else Color.White)
    }

    override val showsZashboardMenu: Boolean
        get() = false

    override suspend fun main() {
        if (!ZashboardSession.isEnabled(this) || !clashRunning) {
            ZashboardSession.close()
            finish()
            return
        }

        ZashboardSession.markShown()
        onBackPressedDispatcher.addCallback(this) { ZashboardSession.minimize() }

        setContent {
            val top by topColour.collectAsStateWithLifecycle()
            AppTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(top)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsTopHeight(WindowInsets.statusBars)
                        )
                        AndroidView(
                            factory = { ZashboardSession.attach(this@ZashboardActivity) },
                            onRelease = { ZashboardSession.detach(this@ZashboardActivity) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .windowInsetsPadding(
                                    WindowInsets.navigationBars
                                        .union(WindowInsets.ime)
                                        .union(WindowInsets.displayCutout)
                                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                                ),
                        )
                    }
                    ZashboardMenuButton(shown = true, actions = remember { ZashboardActions(this@ZashboardActivity) })
                }
            }
        }

        // The status bar's strip follows the page (its theme, its route).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    ZashboardSession.evaluate(TOP_COLOUR_SCRIPT) { json -> parse(json)?.let(::setTop) }
                    delay(1000)
                }
            }
        }

        // Minimized or closed — from the menu, back, the VPN stopping — it leaves.
        ZashboardSession.state.first { it != ZashboardSession.State.Shown }
        finish()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    private fun setTop(colour: Color) {
        if (topColour.value == colour) return
        topColour.value = colour
        val light = colour.luminance() > 0.5f
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    /** "rgb(r, g, b)" / "rgba(r, g, b, a)", as a JSON string. */
    private fun parse(json: String?): Color? {
        val css = json?.trim('"') ?: return null
        val open = css.indexOf('(')
        val close = css.lastIndexOf(')')
        if (open < 0 || close <= open) return null
        val parts = css.substring(open + 1, close).split(',').mapNotNull { it.trim().toFloatOrNull() }
        if (parts.size < 3) return null
        val alpha = parts.getOrNull(3) ?: 1f
        if (alpha < 0.5f) return null
        return Color(parts[0] / 255f, parts[1] / 255f, parts[2] / 255f)
    }

    private companion object {
        /** The page's top background colour: its blurred header, its content, or the page itself. */
        const val TOP_COLOUR_SCRIPT = """
            (function () {
              function colour(el) {
                if (!el) return null;
                var c = getComputedStyle(el).backgroundColor;
                return !c || c === 'transparent' || c === 'rgba(0, 0, 0, 0)' ? null : c;
              }
              return colour(document.querySelector('.need-blur')) || colour(document.querySelector('#app-content'))
                || colour(document.body) || colour(document.documentElement) || '';
            })()
        """
    }
}
