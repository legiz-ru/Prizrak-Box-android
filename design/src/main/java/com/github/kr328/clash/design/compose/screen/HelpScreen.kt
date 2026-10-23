package com.github.kr328.clash.design.compose.screen

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.PreferenceCard
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.PreferenceScaffold
import com.github.kr328.clash.design.compose.component.ValuePreference

private const val URL_CHANGELOG = "https://github.com/legiz-ru/Prizrak-Box/releases"
private const val URL_GITHUB = "https://github.com/legiz-ru"
private const val URL_TELEGRAM = "https://t.me/legiz_trashbag"
private const val URL_LICENSE = "https://github.com/legiz-ru/Prizrak-Box/blob/main/LICENSE"

/**
 * About, laid out as on iOS: the app's icon and name, version (ten taps in
 * a row open Debug), build and core; on Android also Check for Updates and
 * the Changelog; the developer in one row with GitHub's mark and Telegram's
 * plane; documentation, sources, license.
 */
@Composable
fun HelpScreen(
    appIcon: ImageBitmap?,
    appVersion: String,
    appBuild: String,
    coreVersion: String,
    onBack: (() -> Unit)?,
    onCheckUpdate: () -> Unit,
    onOpenLink: (String) -> Unit,
    onDebug: () -> Unit,
    /** The bundled dashboard's release, while Debug → Zashboard is on. */
    zashboardVersion: String? = null,
) {
    // Ten taps on the version open Debug; a pause of over two seconds starts the count again.
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    val onVersionTap = {
        val now = System.currentTimeMillis()
        taps = if (now - lastTap > 2000) 1 else taps + 1
        lastTap = now
        if (taps >= 10) {
            taps = 0
            onDebug()
        }
    }

    PreferenceScaffold(title = stringResource(R.string.about_the_app), onBack = onBack) {
        free("header") { Header(appIcon) }

        item { DetailRow(R.drawable.ic_prizrak_ghost, stringResource(R.string.about_version), appVersion, onVersionTap) }
        item { DetailRow(R.drawable.ic_baseline_work, stringResource(R.string.about_build), appBuild) }
        item { DetailRow(R.drawable.ic_baseline_meta, stringResource(R.string.core_version), coreVersion) }
        if (!zashboardVersion.isNullOrEmpty()) {
            item { DetailRow(R.drawable.ic_zashboard, "Zashboard", "v$zashboardVersion") }
        }

        gap()
        item { ActionRow(R.drawable.ic_mdi_update, R.string.check_for_updates, onCheckUpdate) }
        item { ActionRow(R.drawable.ic_baseline_assignment, R.string.about_changelog) { onOpenLink(URL_CHANGELOG) } }

        category(R.string.about_developer)
        item { Developer(onOpenLink) }

        category(R.string.about_documentation)
        item { LinkRow(stringResource(R.string.clash_meta_wiki), stringResource(R.string.clash_meta_wiki_url), onOpenLink) }
        // The URL is a localized resource: the Russian docs live under /ru/,
        // everyone else lands on the root.
        item { LinkRow(stringResource(R.string.app_documentation), stringResource(R.string.app_documentation_url), onOpenLink) }

        category(R.string.sources)
        item { LinkRow(stringResource(R.string.clash_meta_core), stringResource(R.string.clash_meta_core_url), onOpenLink) }
        item { LinkRow(stringResource(R.string.clash_meta_for_android), stringResource(R.string.meta_github_url), onOpenLink) }
        item { LinkRow(stringResource(R.string.moshen_core), stringResource(R.string.moshen_core_url), onOpenLink) }
        item { LinkRow(stringResource(R.string.mihomo_smart_core), stringResource(R.string.mihomo_smart_core_url), onOpenLink) }
        item { LinkRow(stringResource(R.string.moshen_fork_core), stringResource(R.string.moshen_fork_core_url), onOpenLink) }
        item { LinkRow(stringResource(R.string.cmfa_origin), stringResource(R.string.cmfa_origin_url), onOpenLink) }
        item { LinkRow("Zashboard (MIT)", "https://github.com/Zephyruso/zashboard", onOpenLink) }

        category(R.string.about_license)
        item { LinkRow("GPL-3.0", URL_LICENSE, onOpenLink) }
    }
}

@Composable
private fun Header(appIcon: ImageBitmap?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(22.dp)),
            )
        }
        Text(
            text = "Prizrak-Box",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun DetailRow(@DrawableRes icon: Int, title: String, value: String, onClick: (() -> Unit)? = null) {
    if (onClick == null) {
        ValuePreference(title, value, icon = icon)
    } else {
        PreferenceRow(
            title = title,
            icon = icon,
            onClick = onClick,
            trailing = { Text(value, style = MaterialTheme.typography.bodyLarge) },
        )
    }
}

@Composable
private fun ActionRow(@DrawableRes icon: Int, @StringRes title: Int, onClick: () -> Unit) {
    PreferenceRow(title = stringResource(title), icon = icon, onClick = onClick)
}

/** The developer in one row: avatar, name, and GitHub's mark and Telegram's plane as round buttons. */
@Composable
private fun Developer(onOpenLink: (String) -> Unit) = PreferenceCard {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_dev_avatar),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(14.dp))
        Text("legiz", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconLink(R.drawable.ic_github_mark, "GitHub") { onOpenLink(URL_GITHUB) }
        Spacer(Modifier.width(10.dp))
        IconLink(R.drawable.ic_outline_send, "Telegram") { onOpenLink(URL_TELEGRAM) }
    }
}

/** A round icon button opening a link, in the text colour. */
@Composable
private fun IconLink(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Icon(
        painter = painterResource(icon),
        contentDescription = label,
        tint = scheme.onSurface,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(scheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(9.dp),
    )
}

/** A link in the text colour, not the accent: links read like the other rows. */
@Composable
private fun LinkRow(title: String, url: String, onOpenLink: (String) -> Unit) {
    PreferenceRow(
        title = title,
        onClick = { onOpenLink(url) },
        trailing = {
            Icon(
                painter = painterResource(R.drawable.ic_outline_open_in_new),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}
