package com.github.kr328.clash.design.compose.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.background.HomeBackground
import com.github.kr328.clash.design.compose.background.HomeBackgroundStyle
import com.github.kr328.clash.design.compose.background.HomeBackgroundThumbnail
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.PreferenceScaffold
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.Appearance
import kotlinx.coroutines.launch

/**
 * Settings → Visual → Home Background: each background as it looks on a
 * whole screen while connected, shrunk, with what it does; at the top right
 * every background on the whole screen.
 */
@Composable
internal fun HomeBackgroundPage(
    onBack: () -> Unit,
    appearance: Appearance.State,
    darkTheme: Boolean,
    selected: HomeBackgroundStyle,
    onSelect: (HomeBackgroundStyle) -> Unit,
    onFullScreen: () -> Unit,
) {
    val themed = appearance.themeBackground
    val accent = appearance.accent(darkTheme)
    val scheme = MaterialTheme.colorScheme

    PreferenceScaffold(
        title = stringResource(R.string.home_background),
        onBack = onBack,
        actions = {
            IconButton(onClick = onFullScreen) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_fullscreen),
                    contentDescription = stringResource(R.string.bg_full_screen),
                    tint = scheme.onSurface,
                )
            }
        },
    ) {
        if (themed != null) {
            free("locked") {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_palette),
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.bg_locked, appearance.activeThemeOwner, stringResource(themed.title)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        for (style in HomeBackgroundStyle.entries) {
            item(key = style.id) {
                val shape = RoundedCornerShape(9.dp)
                // Kept, not lost: it comes back with the theme off.
                PreferenceRow(
                    title = stringResource(style.title),
                    summary = stringResource(style.detail),
                    enabled = themed == null,
                    onClick = { onSelect(style) },
                    leading = {
                        HomeBackgroundThumbnail(
                            style = style,
                            accent = accent,
                            modifier = Modifier
                                .size(width = 44.dp, height = 64.dp)
                                .clip(shape)
                                .border(1.dp, scheme.onSurface.copy(alpha = 0.12f), shape),
                        )
                    },
                    trailing = if (selected == style) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_check),
                                contentDescription = null,
                                tint = ClashTheme.extraColors.settingsGreen,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
        footnote(R.string.bg_footer)
    }
}

/**
 * Home Background → full screen: the backgrounds swiped through left and
 * right, dots for where in the list, the power button to see each with the
 * VPN off and on, and Apply. Back (or the edge swipe) closes it.
 */
@Composable
internal fun HomeBackgroundPreview(
    start: HomeBackgroundStyle,
    applied: HomeBackgroundStyle,
    accent: Color,
    onApply: (HomeBackgroundStyle) -> Unit,
    onClose: () -> Unit,
) {
    val styles = HomeBackgroundStyle.entries
    val pager = rememberPagerState(initialPage = start.ordinal) { styles.size }
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(false) }
    val page = styles[pager.currentPage]
    val scheme = MaterialTheme.colorScheme
    val isApplied = applied == page

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
            HomeBackground(style = styles[index], connected = connected, accent = accent, modifier = Modifier.fillMaxSize())
        }

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, top = 8.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(scheme.surface.copy(alpha = 0.8f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_close),
                    contentDescription = stringResource(R.string.close),
                    tint = scheme.onSurface,
                )
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .widthIn(max = 520.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(28.dp))
                    .background(scheme.surface.copy(alpha = 0.88f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(page.title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(page.detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                // Where in the list; a dot is also a way to jump.
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    styles.forEachIndexed { index, style ->
                        Box(
                            modifier = Modifier
                                .clickable { scope.launch { pager.animateScrollToPage(index) } }
                                .padding(4.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(scheme.onSurface.copy(alpha = if (style == page) 0.9f else 0.25f)),
                        )
                    }
                }

                // ⏻ here connects nothing: it only shows the background both ways.
                Text(
                    stringResource(if (connected) R.string.bg_preview_hint_on else R.string.bg_preview_hint_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val powerFill by animateColorAsState(
                        if (connected) accent else scheme.onSurface.copy(alpha = 0.12f),
                        label = "power",
                    )
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(powerFill)
                            .clickable { connected = !connected },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_power),
                            contentDescription = stringResource(if (connected) R.string.bg_vpn_on else R.string.bg_vpn_off),
                            tint = if (connected) Color.White else scheme.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            // A solid text colour, as on iOS.
                            .background(if (isApplied) scheme.onSurface.copy(alpha = 0.12f) else scheme.onSurface)
                            .clickable(enabled = !isApplied) { onApply(page) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val content = if (isApplied) scheme.onSurfaceVariant else scheme.surface
                        Icon(
                            painter = painterResource(if (isApplied) R.drawable.ic_baseline_check else R.drawable.ic_baseline_brush),
                            contentDescription = null,
                            tint = content,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(if (isApplied) R.string.bg_applied else R.string.bg_apply),
                            color = content,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
