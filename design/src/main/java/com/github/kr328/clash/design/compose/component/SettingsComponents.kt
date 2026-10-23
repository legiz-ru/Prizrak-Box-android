package com.github.kr328.clash.design.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R

/** A group's header, in the accent, as Material 3 Expressive titles its lists. */
@Composable
fun SettingsCategory(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** A row that opens a screen: its icon, title, a line under it, and a chevron. */
@Composable
fun SettingsItem(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showChevron: Boolean = true,
    /** Open beside the list (a wide window): in the accent's container, as iOS marks it. */
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    PreferenceRow(
        title = title,
        summary = subtitle,
        icon = icon,
        modifier = modifier,
        container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceBright,
        onClick = onClick,
        trailing = if (showChevron) {
            { Icon(painterResource(R.drawable.ic_baseline_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp)) }
        } else {
            null
        },
    )
}
