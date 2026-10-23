package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.PreferenceScaffold
import com.github.kr328.clash.design.compose.component.PreferenceRow

/**
 * Shown when the bundled native libraries are missing/broken (1:1 with
 * ApkBrokenDesign, restyled to MD3E). Explains the problem and links to the
 * GitHub releases page for a clean reinstall.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkBrokenScreen(
    onBack: () -> Unit,
    onOpenReleases: () -> Unit,
) {
    PreferenceScaffold(title = stringResource(R.string.application_broken), onBack = onBack) {
        free("tips") {
            Text(
                text = stringResource(R.string.application_broken_tips),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        category(R.string.reinstall)
        item {
            PreferenceRow(
                title = stringResource(R.string.github_releases),
                summary = stringResource(R.string.meta_github_url),
                onClick = onOpenReleases,
            )
        }
    }
}
