package com.github.kr328.clash.design.compose.component

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.background.homeGlass
import com.github.kr328.clash.design.compose.theme.ClashTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Home's connect control, as on iOS: the app's ghost is the button, shown
 * while disconnected. An accent ring round it on every background (it glows
 * on Mushroomia). While connecting a ring spreads and fades on its own clock
 * and the button breathes in and out — never snapping back — then settles
 * smoothly.
 */
@Composable
fun GhostPowerButton(
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    val glass = homeGlass()

    /** The button breathing: 1 → 1.04 → 1, a second each way. */
    val swell = remember { Animatable(1f) }

    /** The connecting ring: 0 → 1 over a second, then afresh. */
    val ripple = remember { Animatable(0f) }

    LaunchedEffect(busy) {
        if (!busy) {
            // Back to its size the way it grew, not in one jump.
            ripple.snapTo(0f)
            swell.animateTo(1f, tween(350, easing = LinearOutSlowInEasing))
            return@LaunchedEffect
        }
        coroutineScope {
            launch {
                // The ring starts afresh each second: it has faded out by then.
                while (true) {
                    ripple.snapTo(0f)
                    ripple.animateTo(1f, tween(1000, easing = LinearOutSlowInEasing))
                }
            }
            launch {
                // Grows and shrinks back, so a cycle ends where it began.
                while (true) {
                    swell.animateTo(1.04f, tween(1000, easing = FastOutSlowInEasing))
                    swell.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    val glowPaint = remember {
        Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = swell.value
                scaleY = swell.value
            }
            .drawBehind {
                // Mushroomia: the ring glows.
                if (glass) {
                    drawIntoCanvas { canvas ->
                        glowPaint.color = accent.copy(alpha = 0.6f).toArgb()
                        glowPaint.strokeWidth = 3.dp.toPx()
                        glowPaint.maskFilter = BlurMaskFilter(12.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                        canvas.nativeCanvas.drawCircle(center.x, center.y, this.size.minDimension / 2, glowPaint)
                    }
                }
            }
            .drawWithContent {
                drawContent()
                // An accent ring on every background.
                val ring = 1.5.dp.toPx()
                drawCircle(accent.copy(alpha = 0.7f), radius = this.size.minDimension / 2 - ring / 2, style = Stroke(ring))
                if (busy) {
                    val p = ripple.value
                    val wide = 3.dp.toPx()
                    drawCircle(
                        accent.copy(alpha = 0.6f * (1 - p)),
                        radius = (this.size.minDimension / 2 - wide / 2) * (1 + 0.12f * p),
                        style = Stroke(wide),
                    )
                }
            }
            .clip(CircleShape)
            .background(scheme.onSurface.copy(alpha = 0.07f))
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_prizrak_ghost),
            contentDescription = stringResource(R.string.start),
            tint = scheme.onSurface.copy(alpha = if (busy) 0.85f else 0.55f),
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

@Preview
@Composable
private fun GhostPowerButtonPreview() {
    ClashTheme(darkTheme = false) {
        GhostPowerButton(busy = false, onClick = {})
    }
}

@Preview
@Composable
private fun GhostPowerButtonBusyPreview() {
    ClashTheme(darkTheme = true) {
        GhostPowerButton(busy = true, onClick = {})
    }
}
