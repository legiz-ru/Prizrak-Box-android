package com.github.kr328.clash.design.compose.background

import android.content.Context
import android.graphics.BlurMaskFilter
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.github.kr328.clash.design.R
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The layer behind Home's cards, chosen in Settings → Visual → Home
 * Background, the same as on iOS. Grey while the VPN is off, the accent
 * while it is on; it moves only while connected, and never with animations
 * turned off in Android's settings.
 */
enum class HomeBackgroundStyle(val id: String, @StringRes val title: Int, @StringRes val detail: Int) {
    // Topography first: the default, at the top of the list in Settings.
    Topography("topography", R.string.bg_topography, R.string.bg_topography_detail),
    Clean("clean", R.string.bg_clean, R.string.bg_clean_detail),
    BigGhost("bigGhost", R.string.bg_big_ghost, R.string.bg_big_ghost_detail),
    DotGrid("dotGrid", R.string.bg_dot_grid, R.string.bg_dot_grid_detail),
    Hem("hem", R.string.bg_hem, R.string.bg_hem_detail),
    Signal("signal", R.string.bg_signal, R.string.bg_signal_detail),
    GhostPattern("ghostPattern", R.string.bg_ghost_pattern, R.string.bg_ghost_pattern_detail),
    Cucumber("cucumber", R.string.bg_cucumber, R.string.bg_cucumber_detail),
    Mushroomia("mushroomia", R.string.bg_mushroomia, R.string.bg_mushroomia_detail);

    /** Cards on Home turn to glass over this background. */
    val glassCards: Boolean get() = this == Mushroomia

    companion object {
        val DEFAULT = Topography

        /** A stored id or a `pxa-theme` `background` id, any case; null for `user` or one this app does not have. */
        fun of(id: String?): HomeBackgroundStyle? {
            val key = id?.lowercase() ?: return null
            return entries.firstOrNull { it.id.lowercase() == key }
        }

        /** The background in effect: the profile theme's, else the user's. */
        fun effective(stored: String, themeId: String?): HomeBackgroundStyle = of(themeId) ?: of(stored) ?: DEFAULT
    }
}

/** What a Home background is drawn with, worked out once per screen. */
@Immutable
class HomeBackgroundArt internal constructor(
    internal val ghost: ImageVector,
    private val context: Context,
    private val density: Float,
) {
    private val ghosts = HashMap<Int, ImageBitmap>()
    private val cucumbers = HashMap<Pair<CucumberGlyphs.Kind, Int>, ImageBitmap>()

    /** The ghost, white, [sidePt] square at this density. */
    internal fun ghost(sidePt: Float): ImageBitmap {
        val px = (sidePt * density).toInt().coerceAtLeast(1)
        return ghosts.getOrPut(px) {
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_prizrak_ghost)!!.mutate()
            drawable.setTint(android.graphics.Color.WHITE)
            drawable.toBitmap(px, px).asImageBitmap()
        }
    }

    internal fun cucumber(kind: CucumberGlyphs.Kind, sidePt: Float): ImageBitmap {
        val px = (sidePt * density).toInt().coerceAtLeast(1)
        return cucumbers.getOrPut(kind to px) { CucumberGlyphs.render(kind, px).asImageBitmap() }
    }
}

@Composable
private fun rememberHomeBackgroundArt(): HomeBackgroundArt {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val ghost = ImageVector.vectorResource(R.drawable.ic_prizrak_ghost)
    return remember(context, density, ghost) { HomeBackgroundArt(ghost, context, density) }
}

/** Seconds on one clock for every background on screen, so a glass card's backdrop matches what is behind it. */
private fun now(): Double = System.nanoTime() / 1e9

/** Animations turned off in Android's settings (Accessibility → Remove animations). */
private fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** The clock of a background: running (30 frames a second at most) while it [animates], else 0. */
@Composable
private fun rememberBackgroundTime(animates: Boolean): State<Double> {
    val time = remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(animates) {
        if (!animates) {
            time.doubleValue = 0.0
            return@LaunchedEffect
        }
        var last = 0L
        while (true) {
            withFrameNanos { frame ->
                if (frame - last >= 33_000_000L) {
                    last = frame
                    time.doubleValue = now()
                }
            }
        }
    }
    return time
}

/** Everything a glass card needs to draw the background behind it. */
@Immutable
class HomeBackdrop internal constructor(
    val style: HomeBackgroundStyle,
    internal val connected: Boolean,
    internal val accent: Color,
    internal val dark: Boolean,
    internal val art: HomeBackgroundArt,
    internal val time: State<Double>,
    internal val origin: State<Offset>,
    internal val size: State<Size>,
)

/** The background behind the screen, for glass cards over it; null outside Home. */
val LocalHomeBackdrop = compositionLocalOf<HomeBackdrop?> { null }

/**
 * A Home background filling its box, with [content] over it. Cards in the
 * content read [LocalHomeBackdrop] to turn to glass over Mushroomia.
 *
 * @param animated off for previews such as the Settings list: they stand still.
 */
@Composable
fun HomeBackground(
    style: HomeBackgroundStyle,
    connected: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    animated: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val art = rememberHomeBackgroundArt()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val animates = connected && animated && style != HomeBackgroundStyle.Clean && !remember { animationsOff(context) }
    val time = rememberBackgroundTime(animates)
    val origin = remember { mutableStateOf(Offset.Zero) }
    val size = remember { mutableStateOf(Size.Zero) }
    val ground = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground

    val backdrop = HomeBackdrop(style, connected, accent, dark, art, time, origin, size)
    Box(modifier) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .onGloballyPositioned {
                    origin.value = it.positionInRoot()
                    size.value = Size(it.size.width.toFloat(), it.size.height.toFloat())
                },
        ) {
            drawRect(ground)
            drawHomeBackground(backdrop, time.value, ink)
        }
        CompositionLocalProvider(LocalHomeBackdrop provides backdrop) {
            content()
        }
    }
}

/**
 * Behind a card's content over a glass background: the background as it is
 * behind the card, blurred (Android 12 and later; translucent before), with
 * a frosted tint ([frost], else one for the light or dark forest). Nothing
 * when the background is not a glass one.
 */
@Composable
fun BoxScope.HomeGlassBackdrop(frost: Color? = null) {
    val backdrop = LocalHomeBackdrop.current ?: return
    if (!backdrop.style.glassCards) return
    var position by remember { mutableStateOf(Offset.Zero) }
    val ink = MaterialTheme.colorScheme.onBackground
    Canvas(
        modifier = Modifier
            .matchParentSize()
            .onGloballyPositioned { position = it.positionInRoot() }
            .graphicsLayer {
                clip = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = BlurEffect(24.dp.toPx(), 24.dp.toPx(), TileMode.Clamp)
                }
            },
    ) {
        val shift = position - backdrop.origin.value
        translate(-shift.x, -shift.y) {
            drawHomeBackground(backdrop, backdrop.time.value, ink, backdrop.size.value)
        }
    }
    // Frosted: darker over the dark forest, lighter over the pale one.
    Canvas(Modifier.matchParentSize()) {
        drawRect(frost ?: if (backdrop.dark) Color.Black.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.42f))
    }
}

/** Whether Home's cards are glass now. */
@Composable
fun homeGlass(): Boolean = LocalHomeBackdrop.current?.style?.glassCards == true

// MARK: - Drawing

/** The background in points (dp), as on iOS; [area] is the whole screen's size in pixels. */
private fun DrawScope.drawHomeBackground(b: HomeBackdrop, t: Double, ink: Color, area: Size = size) {
    if (b.style == HomeBackgroundStyle.Clean || area.width <= 0f) return
    val u = density
    val w = area.width / u
    val h = area.height / u
    scale(u, u, pivot = Offset.Zero) {
        when (b.style) {
            HomeBackgroundStyle.Clean -> Unit
            HomeBackgroundStyle.BigGhost -> drawBigGhost(b, ink, w, h, t)
            HomeBackgroundStyle.DotGrid -> drawDots(b, ink, w, h, t)
            HomeBackgroundStyle.Hem -> drawHem(b, ink, w, h, t)
            HomeBackgroundStyle.Signal -> drawSignal(b, ink, w, h, t)
            HomeBackgroundStyle.GhostPattern -> drawPattern(b, ink, w, h, t)
            HomeBackgroundStyle.Topography -> drawTopography(b, ink, w, h, t)
            HomeBackgroundStyle.Cucumber -> drawCucumbers(b, ink, w, h, t)
            HomeBackgroundStyle.Mushroomia -> drawMushroomia(b, w, h, t)
        }
    }
}

private fun HomeBackdrop.ink(ink: Color, off: Float, on: Float): Color =
    if (connected) accent.copy(alpha = on) else ink.copy(alpha = off)

/** An image [side] points square at ([x], [y]), tinted. */
private fun DrawScope.glyph(image: ImageBitmap, x: Float, y: Float, side: Float, colour: Color, alpha: Float) {
    withTransform({
        translate(x, y)
        scale(side / image.width, side / image.height, pivot = Offset.Zero)
    }) {
        drawImage(image, alpha = alpha, colorFilter = ColorFilter.tint(colour))
    }
}

private fun DrawScope.drawBigGhost(b: HomeBackdrop, ink: Color, w: Float, h: Float, t: Double) {
    val side = w * 1.4f
    val phase = sin(t * 2 * PI / 6).toFloat()
    val image = b.art.ghost(min(side, 360f))
    withTransform({
        translate(w * 0.42f, h - side * 0.36f - phase * 10)
        rotate(-3 + phase * 2.5f, pivot = Offset.Zero)
    }) {
        glyph(image, -side / 2, -side / 2, side, if (b.connected) b.accent else ink, if (b.connected) 0.16f else 0.05f)
    }
}

private fun DrawScope.drawDots(b: HomeBackdrop, ink: Color, w: Float, h: Float, t: Double) {
    val step = 16f
    // While connected a band of light crosses the grid every five seconds.
    val sweep = ((t % 5) / 5 * (w + h) * 1.4 - h * 0.4).toFloat()
    var y = step / 2
    while (y < h) {
        var x = step / 2
        while (x < w) {
            var alpha = if (b.connected) 0.45f else 0.12f
            if (b.connected) {
                val d = (x + y * 0.47f) - sweep
                alpha *= 1 - 0.85f * exp(-(d * d) / 900)
            }
            drawCircle((if (b.connected) b.accent else ink).copy(alpha = alpha), radius = 1.3f, center = Offset(x, y))
            x += step
        }
        y += step
    }
}

/** The ghost's hem: an irregular wave across the bottom, 100 pt a period. */
private fun hemPath(width: Float, baseline: Float, height: Float, shift: Float): Path {
    val amps = floatArrayOf(1.0f, 0.8f, 1.1f, 0.9f, 1.05f, 0.85f)
    val path = Path()
    var x = -(shift % 600) - 600
    path.moveTo(x, baseline)
    var i = 0
    while (x < width + 100) {
        val a = amps[i % amps.size] * 22
        path.cubicTo(x + 15, baseline - a, x + 35, baseline - a, x + 50, baseline)
        path.cubicTo(x + 65, baseline + a * 0.6f, x + 85, baseline + a * 0.6f, x + 100, baseline)
        x += 100
        i++
    }
    path.lineTo(x, baseline + height)
    path.lineTo(-700f, baseline + height)
    path.close()
    return path
}

private fun DrawScope.drawHem(b: HomeBackdrop, ink: Color, w: Float, h: Float, t: Double) {
    val rise = if (b.connected) 330f else 260f
    val back = hemPath(w, h - rise + 24 + 40, rise, (-t / 11 * 300).toFloat() - 50)
    val front = hemPath(w, h - rise + 40, rise, (t / 7 * 300).toFloat())
    drawPath(back, b.ink(ink, 0.04f, 0.12f))
    drawPath(front, b.ink(ink, 0.05f, 0.18f))
}

private fun DrawScope.drawSignal(b: HomeBackdrop, ink: Color, w: Float, h: Float, t: Double) {
    if (b.connected) {
        // Waves rolling out of the power button, bottom right.
        val center = Offset(w - 40, h - 40)
        for (k in 0 until 3) {
            val p = (((t + k * 1.5) / 4.5) % 1).toFloat()
            val r = (0.2f + 2.2f * p) * 145
            drawCircle(b.accent.copy(alpha = 0.4f * (1 - p)), radius = r, center = center, style = Stroke(2f))
        }
    } else {
        // Quiet rings around the connect button.
        val center = Offset(w / 2, h * 0.6f)
        for (r in floatArrayOf(90f, 125f, 165f)) {
            drawCircle(ink.copy(alpha = 0.07f), radius = r, center = center, style = Stroke(1.5f))
        }
    }
}

private fun DrawScope.drawPattern(b: HomeBackdrop, ink: Color, w: Float, h: Float, t: Double) {
    val tile = 60f
    val shift = ((t / 14) % 1).toFloat() * tile
    val colour = if (b.connected) b.accent else ink
    val alpha = if (b.connected) 0.22f else 0.07f
    val big = b.art.ghost(20f)
    val small = b.art.ghost(16f)
    var y = -tile
    while (y < h + tile) {
        var x = -tile
        while (x < w + tile) {
            glyph(big, x + 8 - shift, y + 8 - shift, 20f, colour, alpha)
            glyph(small, x + 38 - shift, y + 36 - shift, 16f, colour, alpha * 0.7f)
            x += tile
        }
        y += tile
    }
}

private fun DrawScope.drawTopography(b: HomeBackdrop, ink: Color, w: Float, h: Float, t: Double) {
    val reach = hypot(w, h)
    rotate((t / 80 * 360).toFloat(), pivot = Offset(w / 2, h / 2)) {
        data class Rings(val cx: Float, val cy: Float, val step: Float, val off: Float, val on: Float)
        for (r in listOf(Rings(0.7f, 0.62f, 14.2f, 0.07f, 0.22f), Rings(0.25f, 0.3f, 18.2f, 0.05f, 0.14f))) {
            val center = Offset(w * r.cx, h * r.cy)
            val colour = b.ink(ink, r.off, r.on)
            var radius = r.step
            while (radius < reach) {
                drawCircle(colour, radius = radius, center = center, style = Stroke(1.1f))
                radius += r.step
            }
        }
    }
}

/** The app's green: Cucumber stays green whatever the accent. */
private val CucumberGreen = Color(0xFF2FB257)

/**
 * Like Ghost Pattern, with eight different cucumber figures: cells of 225 pt,
 * a 96-pt figure and a 76-pt one (70 %) in each; four cells hold all eight,
 * then it repeats.
 */
private fun DrawScope.drawCucumbers(b: HomeBackdrop, ink: Color, w: Float, h: Float, t: Double) {
    val cell = 225f
    val tile = cell * 2
    val kinds = CucumberGlyphs.Kind.entries
    val shift = ((t / 56) % 1).toFloat() * tile
    val colour = if (b.connected) CucumberGreen else ink
    val alpha = if (b.connected) 0.22f else 0.07f
    var y = -tile
    while (y < h + tile) {
        var x = -tile
        while (x < w + tile) {
            for (n in 0 until 4) {
                val ox = x + (n % 2) * cell - shift
                val oy = y + (n / 2) * cell - shift
                glyph(b.art.cucumber(kinds[n * 2], 96f), ox + 16, oy + 16, 96f, colour, alpha)
                glyph(b.art.cucumber(kinds[n * 2 + 1], 76f), ox + 133, oy + 125, 76f, colour, alpha * 0.7f)
            }
            x += tile
        }
        y += tile
    }
}

// MARK: - Mushroomia

/** One mushroom of the forest: where it stands (0…1 of the width), how tall and how wide its cap, its lean. */
private class Shroom(val x: Float, val h: Float, val w: Float, val lean: Float, val light: Boolean)

private val forest = listOf(
    Shroom(0.18f, 0.30f, 0.42f, -0.06f, true),
    Shroom(0.78f, 0.40f, 0.55f, 0.05f, false),
    Shroom(0.52f, 0.20f, 0.28f, 0.02f, true),
    Shroom(0.93f, 0.16f, 0.20f, -0.04f, true),
    Shroom(0.05f, 0.14f, 0.18f, 0.03f, false),
)

/** Lights of the forest: position (0…1), size, rise speed, phase. */
private class Bokeh(val x: Float, val y: Float, val s: Float, val v: Double, val p: Double)

private val bokeh: List<Bokeh> = run {
    var seed = 7UL
    fun next(): Float {
        seed = seed * 6364136223846793005UL + 1442695040888963407UL
        return ((seed shr 33).toDouble() / 2147483647.0).toFloat()
    }
    List(34) { Bokeh(next(), next(), 4 + next() * 14, 0.01 + next() * 0.03, next() * 6.28) }
}

private fun hsv(h: Float, s: Float, v: Float) = Color.hsv(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))

/** The glowing mushroom forest, in the accent: dim while off; while connected the caps breathe and the lights rise. */
private fun DrawScope.drawMushroomia(b: HomeBackdrop, w: Float, h: Float, t: Double) {
    val dark = b.dark
    val ground = if (dark) {
        listOf(Color(0.012f, 0.075f, 0.043f), Color(0.027f, 0.10f, 0.063f), Color(0.043f, 0.12f, 0.075f))
    } else {
        listOf(Color(0.95f, 0.975f, 0.955f), Color(0.89f, 0.95f, 0.905f), Color(0.83f, 0.92f, 0.85f))
    }
    drawRect(Brush.verticalGradient(ground, startY = 0f, endY = h), size = Size(w, h))

    // Shades of the accent: a light one for the caps, a pale one for the spots and stems.
    val hsvOf = FloatArray(3)
    android.graphics.Color.colorToHSV(b.accent.toArgb(), hsvOf)
    val pale = hsv(hsvOf[0], hsvOf[1] * 0.18f, 1f)
    val bright = hsv(hsvOf[0], hsvOf[1] * 0.65f, min(1f, hsvOf[2] + 0.3f))
    val scale = w / 390

    for (l in bokeh) {
        val y = if (b.connected) (((l.y - (t * l.v).toFloat()) % 1) + 1) % 1 else l.y
        val twinkle = if (b.connected) (0.6 + 0.4 * sin(t * 1.5 + l.p)).toFloat() else 0.5f
        val a = (if (b.connected) 0.3f else 0.04f) * twinkle * (if (dark) 1f else 0.7f)
        val r = l.s * scale * 1.3f
        val c = Offset(l.x * w, y * h)
        drawCircle(
            Brush.radialGradient(listOf(bright.copy(alpha = a), bright.copy(alpha = 0f)), center = c, radius = r),
            radius = r,
            center = c,
        )
    }

    for (m in forest) {
        val glow = if (b.connected) (0.65 + 0.35 * sin(t * 0.9 + m.x * 7)).toFloat() else 0.18f
        drawShroom(b, m, w, h, glow, pale, bright)
    }

    val fogTop = h * 0.75f
    drawRect(
        Brush.verticalGradient(
            listOf(b.accent.copy(alpha = 0f), b.accent.copy(alpha = if (b.connected) 0.2f else 0.04f)),
            startY = fogTop,
            endY = h,
        ),
        topLeft = Offset(0f, fogTop),
        size = Size(w, h - fogTop),
    )
}

private val glowPaint = Paint().asFrameworkPaint().apply { isAntiAlias = true }

private fun DrawScope.drawShroom(b: HomeBackdrop, m: Shroom, w: Float, h: Float, glow: Float, pale: Color, bright: Color) {
    val dark = b.dark
    val x = m.x * w
    val base = h
    val height = m.h * h
    val capW = m.w * w
    val top = base - height
    val cx = x + m.lean * height
    val stemW = capW * 0.13f

    val stem = Path().apply {
        moveTo(x - stemW * 1.3f, base)
        quadraticTo(x - stemW + m.lean * height * 0.3f, base - height * 0.5f, cx - stemW * 0.8f, top + capW * 0.08f)
        lineTo(cx + stemW * 0.8f, top + capW * 0.08f)
        quadraticTo(x + stemW + m.lean * height * 0.3f, base - height * 0.5f, x + stemW * 1.3f, base)
        close()
    }
    val rimY = top + capW * 0.06f
    val cap = Path().apply {
        moveTo(cx - capW / 2, rimY)
        quadraticTo(cx, rimY - capW * 0.62f, cx + capW / 2, rimY)
        quadraticTo(cx, rimY + capW * 0.12f, cx - capW / 2, rimY)
        close()
    }

    // Off: one quiet tone, no glow — the shapes still read, the text over them stays legible.
    if (!b.connected) {
        val tone = if (dark) Color(0.1f, 0.2f, 0.14f) else Color(0.7f, 0.8f, 0.73f)
        drawPath(stem, tone.copy(alpha = 0.55f))
        drawPath(cap, tone.copy(alpha = 0.8f))
        drawPath(cap, b.accent.copy(alpha = if (dark) 0.22f else 0.25f), style = Stroke(1f))
        return
    }

    val stemDark = if (dark) Color(0.04f, 0.12f, 0.07f) else Color(0.55f, 0.75f, 0.6f)
    drawPath(
        stem,
        Brush.verticalGradient(listOf(pale.copy(alpha = 0.3f + 0.5f * glow), stemDark.copy(alpha = 0.9f)), startY = top, endY = base),
    )

    // The glow around the cap.
    drawIntoCanvas { canvas ->
        glowPaint.color = b.accent.copy(alpha = (0.25f + 0.5f * glow) * (if (dark) 1f else 0.6f)).toArgb()
        glowPaint.maskFilter = BlurMaskFilter((8 + 22 * glow) * w / 390, BlurMaskFilter.Blur.NORMAL)
        canvas.nativeCanvas.drawPath(cap.asAndroidPath(), glowPaint)
    }
    val rim = if (dark) Color(0.05f, 0.24f, 0.13f) else Color(0.12f, 0.45f, 0.24f)
    val capColour = if (m.light) bright else b.accent
    val reach = capW * 0.6f
    drawPath(
        cap,
        Brush.radialGradient(
            0.05f / 0.6f to pale.copy(alpha = 0.5f + 0.45f * glow),
            0.55f to capColour.copy(alpha = 0.55f + 0.4f * glow),
            1f to rim.copy(alpha = 0.8f),
            center = Offset(cx, top - capW * 0.05f),
            radius = reach,
        ),
    )

    // Gills under the cap.
    for (i in -5..5) {
        drawLine(
            pale.copy(alpha = 0.2f + 0.35f * glow),
            Offset(cx, top + capW * 0.1f),
            Offset(cx + i * capW * 0.085f, rimY + capW * 0.01f),
            strokeWidth = 0.6f,
        )
    }

    // Spots on the cap.
    val spots = listOf(-0.28f to -0.12f, -0.08f to -0.22f, 0.14f to -0.17f, 0.3f to -0.06f, -0.36f to -0.02f, 0.04f to -0.06f)
    for ((dx, dy) in spots) {
        drawCircle(
            pale.copy(alpha = 0.35f + 0.55f * glow),
            radius = capW * 0.025f + 1,
            center = Offset(cx + dx * capW, rimY + dy * capW),
        )
    }
}

/**
 * For the Settings list: the background as it looks on a whole 400 × 582 pt
 * screen while connected, shrunk into this box; it stands still.
 */
@Composable
fun HomeBackgroundThumbnail(style: HomeBackgroundStyle, accent: Color, modifier: Modifier = Modifier) {
    val art = rememberHomeBackgroundArt()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ground = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground
    val still = remember { mutableDoubleStateOf(0.0) }
    val backdrop = remember(style, accent, dark, art) {
        HomeBackdrop(style, true, accent, dark, art, still, mutableStateOf(Offset.Zero), mutableStateOf(Size.Zero))
    }
    Canvas(modifier.graphicsLayer { clip = true }) {
        drawRect(ground)
        val screen = Size(400f * density, 582f * density)
        val k = size.width / screen.width
        scale(k, k, pivot = Offset.Zero) {
            drawHomeBackground(backdrop, 0.0, ink, screen)
        }
    }
}
