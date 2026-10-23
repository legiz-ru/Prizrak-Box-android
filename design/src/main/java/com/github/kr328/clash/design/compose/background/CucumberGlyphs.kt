package com.github.kr328.clash.design.compose.background

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The eight figures of the Cucumber background, the same as on iOS
 * (CucumberGlyph): drawn in white in a 40 × 40 box scaled to the bitmap.
 * Vector and one colour: details (bumps, seeds, the jar's window, the
 * basket's weave) are cut out of the figure; the background tints it.
 */
internal object CucumberGlyphs {
    enum class Kind { Cucumber, Slices, Jar, CutCucumber, Basket, Bunch, Blossom, Buddy }

    /** The figure as a white mask on transparent, [sidePx] square. */
    fun render(kind: Kind, sidePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(max(sidePx, 1), max(sidePx, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(sidePx / 40f, sidePx / 40f)
        val g = Glyph(canvas)
        when (kind) {
            Kind.Cucumber -> g.cucumber()
            Kind.Slices -> g.slices()
            Kind.Jar -> g.jar()
            Kind.CutCucumber -> g.cutCucumber()
            Kind.Basket -> g.basket()
            Kind.Bunch -> g.bunch()
            Kind.Blossom -> g.blossom()
            Kind.Buddy -> g.buddy()
        }
        return bitmap
    }

    private class Glyph(val canvas: Canvas) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val eraseFill = Paint(fill).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
        private val eraseStroke = Paint(stroke).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }

        // MARK: Primitives

        fun capsule(cx: Float, cy: Float, len: Float, wid: Float, ang: Float): Path {
            val p = Path()
            p.addRoundRect(RectF(-len / 2, -wid / 2, len / 2, wid / 2), wid / 2, wid / 2, Path.Direction.CW)
            p.transform(Matrix().apply {
                setRotate(Math.toDegrees(ang.toDouble()).toFloat())
                postTranslate(cx, cy)
            })
            return p
        }

        fun circle(x: Float, y: Float, r: Float) = Path().apply { addCircle(x, y, r, Path.Direction.CW) }

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }

        /** Fills a shape, first clearing a thin gap around it so overlapping parts stay apart. */
        fun put(path: Path, gap: Float = 1.3f) {
            eraseStroke.strokeWidth = gap * 2
            eraseStroke.strokeCap = Paint.Cap.BUTT
            canvas.drawPath(path, eraseStroke)
            eraseStroke.strokeCap = Paint.Cap.ROUND
            canvas.drawPath(path, fill)
        }

        fun cutFill(path: Path) = canvas.drawPath(path, eraseFill)

        fun cutStroke(path: Path, width: Float) {
            eraseStroke.strokeWidth = width
            canvas.drawPath(path, eraseStroke)
        }

        fun strokeWhite(path: Path, width: Float) {
            stroke.strokeWidth = width
            canvas.drawPath(path, stroke)
        }

        /** The bumps along a cucumber, cut out. */
        fun bumps(cx: Float, cy: Float, len: Float, wid: Float, ang: Float, n: Int) {
            for (i in 0 until n) {
                val u = (i.toFloat() / (n - 1) - 0.5f) * (len - wid) * 0.9f
                val v = (if (i % 2 == 1) 1 else -1) * wid * 0.2f
                val x = cx + cos(ang) * u - sin(ang) * v
                val y = cy + sin(ang) * u + cos(ang) * v
                cutFill(circle(x, y, max(0.7f, wid * 0.07f)))
            }
        }

        /** A round slice: rind ring and three seeds cut out. */
        fun slice(x: Float, y: Float, r: Float) {
            put(circle(x, y, r))
            cutStroke(circle(x, y, r * 0.78f), max(0.8f, r * 0.13f))
            for (k in 0 until 3) {
                val a = k * 2.094f + 0.5f
                cutFill(circle(x + cos(a) * r * 0.32f, y + sin(a) * r * 0.32f, r * 0.11f))
            }
        }

        // MARK: Figures

        fun cucumber() {
            val a = -0.72f
            put(capsule(20f, 21f, 33f, 12f, a))
            put(capsule(20 + cos(a) * 17.5f, 21 + sin(a) * 17.5f, 5f, 3f, a), gap = 0.8f)
            bumps(20f, 21f, 33f, 12f, a, 7)
        }

        fun slices() {
            slice(13f, 25f, 9f)
            slice(25f, 15f, 8f)
            slice(29f, 29f, 7f)
        }

        fun jar() {
            canvas.drawRoundRect(RectF(9.5f, 4f, 30.5f, 10f), 1.5f, 1.5f, fill)
            put(Path().apply { addRoundRect(RectF(8f, 11.5f, 32f, 37.5f), 6f, 6f, Path.Direction.CW) })
            cutFill(Path().apply { addRoundRect(RectF(11f, 15f, 29f, 34f), 3.5f, 3.5f, Path.Direction.CW) })
            for ((x, y, t) in listOf(Triple(14.5f, 24.5f, 0.12f), Triple(20f, 24f, -0.06f), Triple(25.5f, 24.5f, 0.1f))) {
                put(capsule(x, y, 17f, 4.6f, (PI / 2).toFloat() + t), gap = 0.9f)
            }
        }

        fun cutCucumber() {
            val a = -0.55f
            put(capsule(15f, 23f, 26f, 11f, a))
            bumps(15f, 23f, 26f, 11f, a, 5)
            cutFill(Path().apply {
                addRect(RectF(-3f, -9f, 9f, 9f), Path.Direction.CW)
                transform(Matrix().apply {
                    setRotate(Math.toDegrees(a.toDouble()).toFloat())
                    postTranslate(15 + cos(a) * 10, 23 + sin(a) * 10)
                })
            })
            slice(26f, 14f, 6.5f)
            slice(32f, 27f, 6f)
        }

        fun basket() {
            data class C(val x: Float, val y: Float, val l: Float, val w: Float, val a: Float)
            for (c in listOf(C(13f, 17f, 15f, 5.5f, -0.35f), C(22f, 15f, 16f, 5.5f, 0.3f), C(28f, 18f, 11f, 5f, -0.7f))) {
                put(capsule(c.x, c.y, c.l, c.w, c.a), gap = 1f)
            }
            val body = Path().apply {
                moveTo(5f, 20f)
                lineTo(35f, 20f)
                lineTo(31.8f, 33.5f)
                quadTo(31f, 36.6f, 27.9f, 36.6f)
                lineTo(12.1f, 36.6f)
                quadTo(9f, 36.6f, 8.2f, 33.5f)
                close()
            }
            put(body, gap = 1.4f)
            cutStroke(line(6.2f, 25f, 33.8f, 25f), 1.1f)
            cutStroke(line(7.3f, 30f, 32.7f, 30f), 1.1f)
            var x = 11f
            while (x <= 29f) {
                cutStroke(line(x, 20.5f, x + (x - 20) * 0.08f, 36f), 1.1f)
                x += 6f
            }
            val handle = Path().apply {
                val start = Math.toDegrees(PI * 1.08).toFloat()
                val end = Math.toDegrees(PI * 1.92).toFloat()
                addArc(RectF(20 - 12.5f, 20 - 12.5f, 20 + 12.5f, 20 + 12.5f), start, end - start)
            }
            strokeWhite(handle, 2.2f)
        }

        fun bunch() {
            for (t in listOf(-0.5f, 0f, 0.5f)) {
                val a = (PI / 2).toFloat() + t * 0.7f
                val cx = 20 + cos(a) * 15
                val cy = 8 + sin(a) * 15
                put(capsule(cx, cy, 24f, 8f, a), gap = 1.1f)
                bumps(cx, cy, 24f, 8f, a, 4)
            }
            strokeWhite(Path().apply {
                moveTo(20f, 9f)
                quadTo(21f, 4f, 26f, 3f)
            }, 2f)
            put(Path().apply {
                moveTo(20f, 7f)
                cubicTo(17f, 2f, 11f, 2f, 9f, 5f)
                cubicTo(12f, 8f, 17f, 9f, 20f, 7f)
                close()
            }, gap = 0.8f)
        }

        fun blossom() {
            val a = -0.5f
            put(capsule(19f, 25f, 30f, 10.5f, a))
            bumps(19f, 25f, 30f, 10.5f, a, 6)
            put(Path().apply {
                addOval(RectF(-8f, -4.2f, 8f, 4.2f), Path.Direction.CW)
                transform(Matrix().apply {
                    setRotate(Math.toDegrees(-0.5).toFloat())
                    postTranslate(29f, 10f)
                })
            })
            cutStroke(line(22.5f, 13.6f, 35.5f, 6.4f), 0.9f)
            for (k in 0 until 5) {
                val t = k * 1.2566f - 1.57f
                put(circle(9 + cos(t) * 3.2f, 10 + sin(t) * 3.2f, 2.6f), gap = 0.6f)
            }
            cutFill(circle(9f, 10f, 1.6f))
        }

        fun buddy() {
            val a = (PI / 2).toFloat() + 0.08f
            put(capsule(20f, 21.5f, 33f, 15f, a))
            bumps(20f, 21.5f, 33f, 15f, a, 6)
            cutFill(circle(16.3f, 14.5f, 3.5f))
            cutFill(circle(23.7f, 14.8f, 3.5f))
            canvas.drawPath(circle(17f, 15.2f, 1.6f), fill)
            canvas.drawPath(circle(24.4f, 15.5f, 1.6f), fill)
            val smile = Path().apply {
                val start = Math.toDegrees(0.25).toFloat()
                val end = Math.toDegrees(PI - 0.25).toFloat()
                addArc(RectF(20 - 3.4f, 21.5f - 3.4f, 20 + 3.4f, 21.5f + 3.4f), start, end - start)
            }
            cutStroke(smile, 1.4f)
        }
    }
}
