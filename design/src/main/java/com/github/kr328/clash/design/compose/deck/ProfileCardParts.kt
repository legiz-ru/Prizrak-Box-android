package com.github.kr328.clash.design.compose.deck

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.toBytesString
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.background.HomeGlassBackdrop
import com.github.kr328.clash.design.compose.background.homeGlass
import com.github.kr328.clash.design.compose.component.IconLoader
import com.github.kr328.clash.design.compose.theme.AccentRGB
import com.github.kr328.clash.design.compose.theme.Appearance
import com.github.kr328.clash.design.compose.theme.ProfileCardColours
import com.github.kr328.clash.service.model.Profile
import kotlin.math.max
import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/** Corner of a profile's card and of its strip in the deck. */
val CardShape = RoundedCornerShape(20.dp)

/**
 * The colour of [profile]'s card, as on iOS (ProfileCardColor): what is
 * already known in the first frame, then the logo's colour once loaded.
 */
@Composable
fun rememberCardColour(profile: Profile): AccentRGB {
    val context = LocalContext.current
    val appearance by Appearance.state.collectAsStateWithLifecycle()
    val uses = appearance.usesProfileTheme
    var colour by remember(profile.uuid, profile.profileLogo, profile.pxaTheme, uses) {
        mutableStateOf(ProfileCardColours.cachedCardColour(profile, uses))
    }
    LaunchedEffect(profile.uuid, profile.profileLogo, profile.pxaTheme, uses) {
        colour = ProfileCardColours.cardColour(context, profile, uses)
    }
    return colour
}

/** The colour with its brightness moved by [delta] (−1…1). */
private fun shade(c: AccentRGB, delta: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.colour.toArgb(), hsv)
    hsv[2] = min(max(hsv[2] + delta, 0f), 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * The card's face behind [content]: its colour as a soft diagonal gradient;
 * over Mushroomia tinted glass — the forest blurred behind, the colour over
 * it, a light rim.
 */
@Composable
fun CardFace(
    colour: AccentRGB,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = CardShape,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = homeGlass()
    val face = Brush.linearGradient(listOf(shade(colour, -0.08f), colour.colour, shade(colour, 0.1f)))
    Box(
        modifier
            .clip(shape)
            .then(if (glass) Modifier.border(BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)), shape) else Modifier),
    ) {
        if (glass) {
            // Dark glass, as iOS's card: the white text reads over any forest.
            HomeGlassBackdrop(frost = Color.Black.copy(alpha = 0.35f))
            Box(Modifier.matchParentSize().background(face, alpha = 0.6f))
        } else {
            Box(Modifier.matchParentSize().background(face))
        }
        content()
    }
}

private val lightness = ConcurrentHashMap<String, Boolean>()

/** Whether the logo's visible pixels are mostly light (a white or pale mark): it then gets a dark plate. */
private fun isMostlyLight(bitmap: Bitmap): Boolean {
    val side = 24
    val small = Bitmap.createScaledBitmap(bitmap, side, side, true)
    val pixels = IntArray(side * side)
    small.getPixels(pixels, 0, side, 0, 0, side, side)
    var sum = 0.0
    var count = 0
    for (p in pixels) {
        if (((p ushr 24) and 0xFF) / 255.0 <= 0.5) continue
        sum += (0.2126 * ((p shr 16) and 0xFF) + 0.7152 * ((p shr 8) and 0xFF) + 0.0722 * (p and 0xFF)) / 255
        count++
    }
    return count > 8 && sum / count > 0.8
}

/**
 * The profile's logo as it is: no plate, no line around it — where it is
 * transparent, the card shows through; a logo that is practically white
 * alone gets a dark plate, so it does not fade into a light card. Without a logo a sign of its kind on
 * a white rounded square, in the card's colour: a link for a subscription, a
 * page for a file.
 */
@Composable
fun CardLogo(profile: Profile, colour: AccentRGB, size: Dp = 40.dp) {
    val context = LocalContext.current
    val url = profile.profileLogo
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var darkPlate by remember(url) { mutableStateOf(lightness[url] ?: false) }
    LaunchedEffect(url) {
        if (url.isEmpty()) return@LaunchedEffect
        val bitmap = IconLoader.load(context, url) ?: return@LaunchedEffect
        darkPlate = lightness.getOrPut(url) { isMostlyLight(bitmap) }
        image = bitmap.asImageBitmap()
    }
    val shape = RoundedCornerShape(size * 0.26f)
    val shown = image
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(if (darkPlate) Color(0xFF1A1A1A) else Color.Transparent),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (profile.type == Profile.Type.File) R.drawable.ic_outline_article else R.drawable.ic_mdi_web),
                contentDescription = null,
                tint = colour.colour,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

/**
 * A card's name: as tall as the logo beside it while it fits, else the next
 * size down — fixed sizes, the same on Home's card and the opened profile.
 */
@Composable
fun CardTitle(name: String, modifier: Modifier = Modifier, colour: Color = Color.White) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val style = remember(name, width) {
            listOf(30, 22, 17).map { TextStyle(fontSize = it.sp, fontWeight = FontWeight.Bold) }
                .firstOrNull { measurer.measure(name, it, maxLines = 1).size.width <= width }
                ?: TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Text(name, style = style, color = colour, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The card's own text colours: white, and a softer white for labels. */
object CardInk {
    val primary = Color.White
    val secondary = Color.White.copy(alpha = 0.72f)
}

/** A profile's figures, white on its card: used, available, expiry, updated — only those it has. */
@Composable
fun CardFigures(profile: Profile) {
    if (profile.total > 1) {
        MainInfoRow(R.drawable.ic_mdi_chart_timeline_variant, stringResource(R.string.traffic_used), (profile.download + profile.upload).toBytesString())
        // What is left of the quota, as on iOS.
        MainInfoRow(
            R.drawable.ic_mdi_database_check,
            stringResource(R.string.traffic_available),
            (profile.total - profile.upload - profile.download).coerceAtLeast(0).toBytesString(),
        )
    }
    if (profile.expire > 0) {
        val expireText = remember(profile.expire) {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(profile.expire))
        }
        MainInfoRow(R.drawable.ic_mdi_calendar_alert, stringResource(R.string.expires), expireText)
    }
    val updatedText = (System.currentTimeMillis() - profile.updatedAt)
        .elapsedIntervalString(androidx.compose.ui.platform.LocalContext.current)
    MainInfoRow(R.drawable.ic_mdi_update, stringResource(R.string.updated), updatedText)
}

@Composable
private fun MainInfoRow(icon: Int, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = CardInk.secondary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = CardInk.primary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = CardInk.primary,
            maxLines = 1,
        )
    }
}
