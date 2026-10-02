package com.fitlens.companion.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.fitlens.companion.data.Dates
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Branded images for the share sheet: a graph (#22) and a workout card (#11). Both are drawn on an Android [Canvas] in
 * the FitLens look (black and gold, serif headings, letter-spaced labels, gold hairlines), so they
 * don't depend on what is on screen. Colours come from [Brand].
 */
object ShareImages {
    private const val W = 1080
    private const val PAD = 72f

    private val black get() = Brand.Black.toArgb()
    private val graphite get() = Brand.Graphite.toArgb()
    private val red get() = Brand.Fall.toArgb()
    private val gold get() = Brand.Gold.toArgb()
    private val goldLight get() = Brand.GoldLight.toArgb()
    private val ivory get() = Brand.Ivory.toArgb()
    private val muted get() = Brand.Muted.toArgb()
    private val hairline get() = Brand.Hairline.toArgb()
    private val card get() = Brand.Surface.toArgb()

    /**
     * Renders off the main thread, writes the image to `cacheDir/exports` and opens the share sheet. [busy] is shown
     * while it works; a failure is reported instead of closing the app.
     */
    fun share(ctx: Context, busy: String, fileName: String, render: () -> Bitmap) {
        val app = ctx.applicationContext
        AppScope.scope.launch {
            UiEvents.busy.value = busy
            try {
                val file = withContext(Dispatchers.Default) {
                    val bmp = render()
                    val dir = File(app.cacheDir, "exports").apply { mkdirs() }
                    val f = File(dir, fileName)
                    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    bmp.recycle()
                    f
                }
                shareFile(app, file, "image/jpeg")
            } catch (e: Exception) {
                UiEvents.show("Couldn't create the image: ${e.message}")
            } finally {
                UiEvents.busy.value = null
            }
        }
    }

    /** A file name made safe for any share target: letters, digits and dashes. */
    fun fileName(vararg parts: String): String =
        "FitLens_" + parts.joinToString("_") { it.replace(Regex("[^A-Za-z0-9-]+"), "-").trim('-') } + ".jpg"

    // ---------- Graph (#22) ----------

    /** What a shared graph shows: its line, unit and the lines around it. [format] writes a value with its unit. */
    class GraphImage(
        val title: String,
        val graph: String,
        val range: String,
        val points: List<ChartPoint>,
        val format: (Double) -> String,
        val summary: String,
        val trend: TrendLine? = null,
        val goal: Double? = null
    )

    fun renderGraph(g: GraphImage): Bitmap {
        val h = 1350
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        background(c, h)
        var y = header(c, g.title, "${g.graph.uppercase()}  ·  ${g.range.uppercase()}")

        val chart = RectF(PAD + 120f, y + 40f, W - PAD, h - 320f)
        c.drawRoundRect(RectF(PAD - 24f, chart.top - 40f, W - PAD + 24f, chart.bottom + 90f), 36f, 36f, fill(card))
        drawLine(c, g, chart)

        y = chart.bottom + 170f
        wrap(g.summary, text(ivory, 40f, serif = true), W - 2 * PAD).forEach { line ->
            c.drawText(line, PAD, y, text(ivory, 40f, serif = true)); y += 54f
        }
        footer(c, h)
        return bmp
    }

    private fun drawLine(c: Canvas, g: GraphImage, r: RectF) {
        val pts = g.points
        if (pts.isEmpty()) return
        val x0 = pts.first().x
        val x1 = max(pts.last().x, x0 + 1)
        val values = pts.map { it.y } + listOfNotNull(g.goal)
        var y0 = values.min()
        var y1 = values.max()
        if (y1 - y0 < 1e-6) { y0 -= 1; y1 += 1 }
        val pad = (y1 - y0) * 0.1
        y0 -= pad; y1 += pad
        fun px(x: Long) = r.left + (x - x0).toFloat() / (x1 - x0).toFloat() * r.width()
        fun py(v: Double) = r.bottom - ((v - y0) / (y1 - y0)).toFloat() * r.height()

        val grid = fill(hairline)
        val label = text(muted, 28f)
        for (i in 0..4) {
            val gy = r.top + r.height() * i / 4f
            c.drawRect(r.left, gy, r.right, gy + 2f, grid)
            val v = y1 - (y1 - y0) * i / 4.0
            val s = g.format(v)
            c.drawText(s, r.left - 20f - label.measureText(s), gy + 10f, label)
        }
        c.drawText(Dates.short(pts.first().date), r.left, r.bottom + 50f, label)
        val last = Dates.short(pts.last().date)
        c.drawText(last, r.right - label.measureText(last), r.bottom + 50f, label)

        g.goal?.let { goal ->
            c.drawLine(r.left, py(goal), r.right, py(goal), stroke(ivory, 3f, dashed = true))
            c.drawText("Goal", r.right - label.measureText("Goal"), py(goal) - 14f, text(ivory, 28f))
        }
        g.trend?.let { t ->
            c.drawLine(px(x0), py(t.at(x0)), px(x1), py(t.at(x1)), stroke(goldLight, 4f, dashed = true))
        }
        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(px(p.x), py(p.y)) else path.lineTo(px(p.x), py(p.y)) }
        // The app's graph style (1.0.71): a red line over translucent gold fading to the baseline.
        val area = Path(path).apply {
            lineTo(px(pts.last().x), r.bottom)
            lineTo(px(pts.first().x), r.bottom)
            close()
        }
        c.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, r.top, 0f, r.bottom, gold and 0x00FFFFFF or 0x55000000, gold and 0x00FFFFFF or 0x08000000, Shader.TileMode.CLAMP)
        })
        c.drawPath(path, stroke(red, 7f))
        if (pts.size <= 80) pts.forEach { p -> c.drawCircle(px(p.x), py(p.y), 9f, fill(red)) }
    }

    // ---------- Workout card (#11) ----------

    /** One exercise on a workout card: its name, set lines (each marked when it was a PR) and its comment. */
    class CardExercise(val name: String, val sets: List<Pair<String, Boolean>>, val comment: String?)

    class WorkoutCard(
        val title: String,
        val subtitle: String?,
        val exercises: List<CardExercise>,
        val comments: List<String>,
        val photo: Bitmap?
    )

    fun renderWorkout(w: WorkoutCard): Bitmap {
        val width = W - 2 * PAD
        val name = text(ivory, 46f, serif = true, bold = true)
        val set = text(ivory, 36f)
        val note = text(muted, 32f, italic = true)
        val pr = text(gold, 28f, bold = true, tracking = 0.15f)
        // Work out the height first, so the card is exactly as tall as its content.
        val photoH = w.photo?.let { p -> minOf(width * p.height / p.width.toFloat(), 1100f) } ?: 0f
        var body = 0f
        w.exercises.forEach { e ->
            body += 90f + e.sets.size * 52f
            e.comment?.let { body += wrap("“$it”", note, width).size * 44f + 8f }
            body += 24f
        }
        w.comments.forEach { body += wrap("“$it”", note, width).size * 44f + 16f }
        val h = (330f + (if (photoH > 0) photoH + 48f else 0f) + body + 200f).toInt()

        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        background(c, h)
        var y = header(c, w.title, w.subtitle?.uppercase())
        y += 48f
        w.photo?.let { p ->
            val pw = photoH * p.width / p.height.toFloat()
            val dst = RectF((W - pw) / 2f, y, (W + pw) / 2f, y + photoH)
            c.drawBitmap(p, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            c.drawRect(dst, stroke(gold, 3f))
            y += photoH + 48f
        }
        w.exercises.forEach { e ->
            y += 56f
            c.drawText(ellipsize(e.name, name, width), PAD, y, name)
            y += 34f
            e.sets.forEachIndexed { i, (line, isPr) ->
                y += 52f
                val num = "${i + 1}"
                c.drawText(num, PAD, y, text(muted, 32f))
                val shown = ellipsize(line, set, width - 200f)
                c.drawText(shown, PAD + 64f, y, set)
                if (isPr) c.drawText("PR", PAD + 64f + set.measureText(shown) + 24f, y - 2f, pr)
            }
            e.comment?.let {
                y += 8f
                wrap("“$it”", note, width).forEach { l -> y += 44f; c.drawText(l, PAD, y, note) }
            }
            y += 24f
        }
        w.comments.forEach {
            y += 16f
            wrap("“$it”", note, width).forEach { l -> y += 44f; c.drawText(l, PAD, y, note) }
        }
        footer(c, h)
        return bmp
    }

    // ---------- Shared pieces ----------

    /** Black with a graphite glow at the top, as the app's screens. */
    private fun background(c: Canvas, h: Int) {
        c.drawColor(black)
        c.drawRect(0f, 0f, W.toFloat(), h * 0.45f, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h * 0.45f, graphite, black, Shader.TileMode.CLAMP)
        })
    }

    /** The FITLENS label, a serif title, an optional letter-spaced subtitle and a gold hairline. Returns the y below. */
    private fun header(c: Canvas, title: String, subtitle: String?): Float {
        c.drawText("FITLENS", PAD, 110f, text(gold, 30f, bold = true, tracking = 0.4f))
        val t = text(ivory, 64f, serif = true)
        c.drawText(ellipsize(title, t, W - 2 * PAD), PAD, 200f, t)
        var y = 200f
        subtitle?.let {
            y += 56f
            val s = text(muted, 28f, tracking = 0.18f)
            c.drawText(ellipsize(it, s, W - 2 * PAD), PAD, y, s)
        }
        y += 34f
        goldHairline(c, y)
        return y
    }

    private fun footer(c: Canvas, h: Int) {
        goldHairline(c, h - 110f)
        val p = text(muted, 28f, tracking = 0.3f)
        val s = "LOGGED WITH FITLENS"
        c.drawText(s, (W - p.measureText(s)) / 2f, h - 56f, p)
    }

    private fun goldHairline(c: Canvas, y: Float) {
        val clear = gold and 0x00FFFFFF
        c.drawRect(PAD, y, W - PAD, y + 3f, Paint().apply {
            shader = LinearGradient(PAD, 0f, W - PAD, 0f, intArrayOf(clear, gold, clear), null, Shader.TileMode.CLAMP)
        })
    }

    private fun text(color: Int, size: Float, serif: Boolean = false, bold: Boolean = false, italic: Boolean = false, tracking: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            val style = when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, style)
            letterSpacing = tracking
        }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun stroke(color: Int, width: Float, dashed: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        if (dashed) pathEffect = DashPathEffect(floatArrayOf(18f, 14f), 0f)
    }

    private fun ellipsize(s: String, p: Paint, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var end = s.length
        while (end > 1 && p.measureText(s.take(end) + "…") > maxW) end--
        return s.take(end) + "…"
    }

    private fun wrap(s: String, p: Paint, maxW: Float): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        s.split(' ').forEach { word ->
            val next = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(next) <= maxW || line.isEmpty()) line = next
            else { lines += line; line = word }
        }
        if (line.isNotEmpty()) lines += line
        return lines.map { ellipsize(it, p, maxW) }
    }
}
