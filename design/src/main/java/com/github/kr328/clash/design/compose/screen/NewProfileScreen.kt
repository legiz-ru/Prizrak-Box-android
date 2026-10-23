package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.ExpressiveScaffold
import com.github.kr328.clash.design.compose.component.PreferenceList
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.listPadding
import com.github.kr328.clash.design.model.ProfileProvider

/**
 * Profile-source picker (1:1 with NewProfileDesign, restyled to MD3E). Tapping
 * a source creates a profile of that type (or launches the QR scanner); a
 * long-press on an external provider opens its app details.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NewProfileScreen(
    providers: List<ProfileProvider>,
    onBack: () -> Unit,
    onSelect: (ProfileProvider) -> Unit,
    onDetail: (ProfileProvider) -> Unit,
) {
    ExpressiveScaffold(title = stringResource(R.string.new_profile), onBack = onBack) { inner ->
        PreferenceList(modifier = Modifier.fillMaxSize(), contentPadding = listPadding(inner)) {
            items(providers) { provider ->
                ProfileProviderRow(
                    provider = provider,
                    onClick = { onSelect(provider) },
                    onLongClick = { onDetail(provider) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ProfileProviderRow(
    provider: ProfileProvider,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val painter = remember(provider) {
        provider.icon?.let { BitmapPainter(it.toBitmap(96, 96).asImageBitmap()) }
    }
    PreferenceRow(
        title = provider.name,
        summary = provider.summary,
        onClick = onClick,
        onLongClick = onLongClick,
        leading = painter?.let {
            { Image(painter = it, contentDescription = null, modifier = Modifier.size(40.dp)) }
        },
    )
}
