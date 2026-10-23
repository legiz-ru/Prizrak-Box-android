package com.github.kr328.clash.design.compose.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * How visible the thumb is: shown while the content scrolls, gone a moment
 * after — as the system's own scrollbars. Always on, it read as a grey line
 * down the edge of a column.
 */
@Composable
private fun scrollbarAlpha(scrolling: Boolean): State<Float> {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(scrolling) {
        if (scrolling) {
            alpha.animateTo(0.5f, tween(150))
        } else {
            delay(800)
            alpha.animateTo(0f, tween(400))
        }
    }
    return alpha.asState()
}

/** Thin scrollbar thumb for a [ScrollState]-backed column, while it scrolls. */
fun Modifier.verticalScrollbar(
    state: ScrollState,
    color: Color,
    width: Dp = 4.dp,
): Modifier = composed {
    val alpha = scrollbarAlpha(state.isScrollInProgress)
    drawWithContent {
        drawContent()
        val maxValue = state.maxValue
        if (maxValue <= 0 || alpha.value <= 0f) return@drawWithContent
        val viewportHeight = size.height
        val contentHeight = viewportHeight + maxValue
        val thumbHeight = (viewportHeight / contentHeight) * viewportHeight
        val thumbOffsetY = (state.value.toFloat() / maxValue) * (viewportHeight - thumbHeight)
        val w = width.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - w, thumbOffsetY),
            size = Size(w, thumbHeight),
            cornerRadius = CornerRadius(w / 2, w / 2),
            alpha = alpha.value,
        )
    }
}

/** Thin scrollbar thumb for a [LazyListState]-backed list (approximate), while it scrolls. */
fun Modifier.verticalScrollbar(
    state: LazyListState,
    color: Color,
    width: Dp = 4.dp,
): Modifier = composed {
    val alpha = scrollbarAlpha(state.isScrollInProgress)
    drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo.size
        if (total <= 0 || visible <= 0 || visible >= total || alpha.value <= 0f) return@drawWithContent
        val viewportHeight = size.height
        val w = width.toPx()
        val thumbHeight = (viewportHeight * visible.toFloat() / total).coerceAtLeast(w * 6)
        val denom = (total - visible).coerceAtLeast(1)
        val progress = (state.firstVisibleItemIndex.toFloat() / denom).coerceIn(0f, 1f)
        val thumbOffsetY = progress * (viewportHeight - thumbHeight)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - w, thumbOffsetY),
            size = Size(w, thumbHeight),
            cornerRadius = CornerRadius(w / 2, w / 2),
            alpha = alpha.value,
        )
    }
}
