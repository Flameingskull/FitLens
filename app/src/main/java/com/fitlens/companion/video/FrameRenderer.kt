package com.fitlens.companion.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Photo
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.fmtSigned
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class OverlayLine(val label: String, val value: String, val delta: String?, val approxNote: String?)

/** One slide overlay entry, keyed by measurement name. [lines] holds only the overlays with a value near that date. */
data class Slide(val photo: Photo, val date: String, val dayNumber: Long, val lines: Map<String, OverlayLine>)

/** A measurement shown over the photos, either as a value or as a value with a progress chart. */
data class Overlay(val name: String, val chart: Boolean)

data class SlideOptions(
    val width: Int = 720,
    val height: Int = 1280,
    val secondsPerPhoto: Float = 1.0f,
    val fade: Boolean = true,
    val showDate: Boolean = true,
    val showDayCount: Boolean = true,
    val showPose: Boolean = true,
    /** At most [FrameRenderer.MAX_OVERLAYS], drawn in this order. */
    val overlays: List<Overlay> = emptyList(),
    val windowDays: Int = 7,
    val onePerDay: Boolean = true,
    val title: String = ""
)

class ChartSeries(val name: String, val unit: String, val points: List<Pair<Long, Double>>)

object FrameRenderer {

    const val MAX_OVERLAYS = 5

    // Luxury palette: gold on black, imperial purple accents
    private const val GOLD = 0xFFD4AF37.toInt()
    private const val GOLD_SOFT = 0x99D4AF37.toInt()
    private const val PURPLE = 0xFF9B5FD0.toInt()
    private const val MUTED = 0xFFCFC6D6.toInt()
    private const val IVORY = 0xFFF7F3EA.toInt()

    fun buildSlides(snap: Snapshot, photos: List<Photo>, opts: SlideOptions): List<Slide> {
        val dated = photos.filter { it.date != null }.sortedWith(compareBy({ it.date }, { it.takenAt ?: "" }, { it.id }))
        val chosen = if (opts.onePerDay) dated.groupBy { it.date!! }.values.map { it.first() } else dated
        if (chosen.isEmpty()) return emptyList()
        val startDay = Dates.epochDay(chosen.first().date!!)
        val baseline = HashMap<String, Double>()
        return chosen.map { p ->
            val date = p.date!!
            val lines = LinkedHashMap<String, OverlayLine>()
            for (name in opts.overlays.map { it.name }) {
                val (rec, exact) = snap.valueNear(name, date, opts.windowDays) ?: continue
                val base = baseline.getOrPut(name) { rec.value }
                val delta = if (abs(rec.value - base) < 1e-9) null else "${fmtSigned(rec.value - base)} ${rec.unit}".trim()
                val note = if (exact) null else {
                    val diff = Dates.epochDay(rec.date) - Dates.epochDay(date)
                    "≈ ${abs(diff)}d ${if (diff < 0) "before" else "after"}"
                }
                lines[name] = OverlayLine(name, "${fmtNum(rec.value)} ${rec.unit}".trim(), delta, note)
            }
            Slide(p, date, Dates.epochDay(date) - startDay + 1, lines)
        }
    }

    /** Progress charts for every overlay shown as a chart, keyed by measurement name. */
    fun chartsFor(snap: Snapshot, slides: List<Slide>, opts: SlideOptions): Map<String, ChartSeries> =
        opts.overlays.filter { it.chart }.mapNotNull { o -> chartFor(snap, slides, o.name)?.let { o.name to it } }.toMap()

    private fun chartFor(snap: Snapshot, slides: List<Slide>, name: String): ChartSeries? {
        if (slides.isEmpty()) return null
        val from = Dates.epochDay(slides.first().date) - 7
        val to = Dates.epochDay(slides.last().date) + 7
        val pts = snap.dailySeries(name).map { Dates.epochDay(it.date) to it.value }.filter { it.first in from..to }
        if (pts.size < 2) return null
        val unit = snap.recordsByName[name]?.lastOrNull()?.unit ?: ""
        return ChartSeries(name, unit, pts)
    }

    /** Decodes a photo (EXIF orientation applied) scaled to fit within maxW × maxH. */
    fun loadBitmap(file: File, maxW: Int, maxH: Int): Bitmap? = try {
        val src = ImageDecoder.createSource(file)
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = min(1f, min(maxW.toFloat() / w, maxH.toFloat() / h))
            decoder.setTargetSize(max(1, (w * scale).toInt()), max(1, (h * scale).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (e: Exception) {
        null
    }

    private fun paint(color: Int, sizePx: Float, bold: Boolean = false, serif: Boolean = false, tracking: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = sizePx
            typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            letterSpacing = tracking
        }

    /** Shrinks [p] so [text] fits in [maxW]. */
    private fun fit(p: Paint, text: String, maxW: Float): Paint {
        val tw = p.measureText(text)
        if (tw > maxW && tw > 0f) p.textSize *= maxW / tw
        return p
    }

    private fun hairline(c: Canvas, x0: Float, x1: Float, y: Float, u: Float) {
        c.drawRect(x0, y, x1, y + max(1f, 1.5f * u), Paint().apply {
            shader = LinearGradient(x0, 0f, x1, 0f, intArrayOf(0x00D4AF37, GOLD, 0x00D4AF37), null, Shader.TileMode.CLAMP)
        })
    }

    private fun drawPhotoFit(c: Canvas, bmp: Bitmap?, dst: RectF) {
        if (bmp == null) return
        val s = min(dst.width() / bmp.width, dst.height() / bmp.height)
        val w = bmp.width * s
        val h = bmp.height * s
        val l = dst.left + (dst.width() - w) / 2
        val t = dst.top + (dst.height() - h) / 2
        c.drawBitmap(bmp, null, RectF(l, t, l + w, t + h), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /** Draws one complete slideshow/video frame. */
    fun drawSlide(c: Canvas, bmp: Bitmap?, slide: Slide, opts: SlideOptions, charts: Map<String, ChartSeries>) {
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        val u = min(w, h) / 720f // scale unit
        c.drawColor(Color.BLACK)
        drawPhotoFit(c, bmp, RectF(0f, 0f, w, h))

        // Top gradient: title, date, day counter, pose
        val topH = 210 * u
        c.drawRect(0f, 0f, w, topH, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, topH, 0xE0050308.toInt(), 0x00050308, Shader.TileMode.CLAMP)
        })
        val poseText = if (opts.showPose && slide.photo.pose.isNotBlank()) slide.photo.pose.uppercase() else null
        val poseP = paint(GOLD, 22 * u, bold = true, tracking = 0.2f)
        val poseW = poseText?.let { poseP.measureText(it) + 24 * u } ?: 0f
        var y = 36 * u
        if (opts.title.isNotBlank()) {
            y += 22 * u
            val t = opts.title.uppercase()
            c.drawText(t, 32 * u, y, fit(paint(GOLD, 22 * u, tracking = 0.2f), t, w - 64 * u - poseW))
            y += 12 * u
        }
        if (opts.showDate) {
            y += 48 * u
            c.drawText(Dates.medium(slide.date), 32 * u, y, paint(IVORY, 48 * u, bold = true, serif = true))
            y += 8 * u
        }
        if (opts.showDayCount) {
            y += 30 * u
            val weeks = (slide.dayNumber - 1) / 7
            val txt = "Day ${slide.dayNumber}" + if (weeks >= 1) "  ·  Week ${weeks + 1}" else ""
            c.drawText(txt, 32 * u, y, paint(MUTED, 26 * u, tracking = 0.05f))
        }
        if (poseText != null) c.drawText(poseText, w - 32 * u - poseP.measureText(poseText), 58 * u, poseP)

        // Bottom panel: up to MAX_OVERLAYS values/charts, two columns on square or wide frames
        val items = opts.overlays.take(MAX_OVERLAYS)
        if (items.isEmpty()) return
        val cols = if (w >= h * 0.8f && items.size >= 3) 2 else 1
        val rows = items.chunked(cols)
        val headerU = 50f
        val chartU = 118f
        val rowGapU = 14f
        val padTopU = 40f
        val padBottomU = 44f
        val rowHU = rows.map { r -> headerU + if (r.any { charts.containsKey(it.name) }) chartU else 0f }
        val naturalU = padTopU + rowHU.sum() + rowGapU * (rows.size - 1) + padBottomU
        // Keep the panel within 60% of the frame so the photo stays the focus
        val k = u * min(1f, h * 0.6f / (naturalU * u))
        val top = h - naturalU * k
        c.drawRect(0f, top - 90 * u, w, h, Paint().apply {
            shader = LinearGradient(0f, top - 90 * u, 0f, top + 30 * k, 0x00050308, 0xE6050308.toInt(), Shader.TileMode.CLAMP)
        })
        hairline(c, 32 * u, w - 32 * u, top + 8 * k, k)
        val gap = 28 * u
        val colW = (w - 64 * u - gap * (cols - 1)) / cols
        val day = Dates.epochDay(slide.date)
        var ry = top + padTopU * k
        rows.forEachIndexed { ri, row ->
            row.forEachIndexed { ci, o ->
                val x = 32 * u + ci * (colW + gap)
                drawOverlay(c, o.name, slide.lines[o.name], charts[o.name], day, RectF(x, ry, x + colW, ry + rowHU[ri] * k), headerU * k, k)
            }
            ry += (rowHU[ri] + rowGapU) * k
        }
    }

    /** One overlay: label on the left, value and change on the right, optional chart underneath. */
    private fun drawOverlay(c: Canvas, name: String, line: OverlayLine?, chart: ChartSeries?, day: Long, r: RectF, headerH: Float, k: Float) {
        val baseline = r.top + headerH * 0.62f
        val label = name.uppercase()
        val labelP = fit(paint(MUTED, 21 * k, tracking = 0.12f), label, r.width() * 0.45f)
        c.drawText(label, r.left, baseline, labelP)

        val value = line?.value ?: "—"
        val dText = listOfNotNull(line?.delta, line?.approxNote).joinToString("  ")
        val valueP = paint(IVORY, 34 * k, bold = true)
        val deltaP = paint(GOLD, 22 * k)
        val space = if (dText.isNotEmpty()) 12 * k else 0f
        var vw = valueP.measureText(value)
        var dw = if (dText.isNotEmpty()) deltaP.measureText(dText) else 0f
        val avail = r.width() - labelP.measureText(label) - 16 * k
        if (vw + dw + space > avail && avail > 0f) {
            val f = avail / (vw + dw + space)
            valueP.textSize *= f; deltaP.textSize *= f; vw *= f; dw *= f
        }
        c.drawText(value, r.right - dw - space - vw, baseline, valueP)
        if (dText.isNotEmpty()) c.drawText(dText, r.right - dw, baseline, deltaP)
        if (chart != null) drawMiniChart(c, chart, day, RectF(r.left, r.top + headerH + 4 * k, r.right, r.bottom - 6 * k), k)
    }

    private fun drawMiniChart(c: Canvas, s: ChartSeries, currentDay: Long, r: RectF, u: Float) {
        val xs = s.points.map { it.first }
        val ys = s.points.map { it.second }
        val x0 = xs.first()
        val x1 = max(xs.last(), x0 + 1)
        var y0 = ys.min()
        var y1 = ys.max()
        if (y1 - y0 < 1e-6) { y0 -= 1; y1 += 1 }
        val pad = (y1 - y0) * 0.1
        y0 -= pad; y1 += pad
        fun px(x: Long) = r.left + (x - x0).toFloat() / (x1 - x0).toFloat() * r.width()
        fun py(y: Double) = r.bottom - ((y - y0) / (y1 - y0)).toFloat() * r.height()

        c.drawRect(r.left, r.bottom, r.right, r.bottom + max(1f, u), Paint().apply { color = 0x33FFFFFF })
        val full = Path()
        val done = Path()
        var doneStarted = false
        var doneFirstX = 0f
        var doneLastX = 0f
        s.points.forEachIndexed { i, (x, y) ->
            if (i == 0) full.moveTo(px(x), py(y)) else full.lineTo(px(x), py(y))
            if (x <= currentDay) {
                if (!doneStarted) { done.moveTo(px(x), py(y)); doneStarted = true; doneFirstX = px(x) } else done.lineTo(px(x), py(y))
                doneLastX = px(x)
            }
        }
        c.drawPath(full, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.5f * u; color = 0x40FFFFFF
        })
        if (doneStarted) {
            val area = Path(done).apply { lineTo(doneLastX, r.bottom); lineTo(doneFirstX, r.bottom); close() }
            c.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, r.top, 0f, r.bottom, 0x55D4AF37, 0x00D4AF37, Shader.TileMode.CLAMP)
            })
            c.drawPath(done, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 4 * u; color = GOLD
                strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
            })
        }
        // Current position marker: interpolate the value at the current day
        val cx = currentDay.coerceIn(x0, x1)
        var cy = ys.first()
        for (i in 0 until s.points.size - 1) {
            val (ax, ay) = s.points[i]
            val (bx, by) = s.points[i + 1]
            if (cx in ax..bx) {
                cy = if (bx == ax) by else ay + (by - ay) * (cx - ax).toDouble() / (bx - ax).toDouble()
                break
            }
            if (cx > bx) cy = by
        }
        c.drawCircle(px(cx), py(cy), 10 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD_SOFT })
        c.drawCircle(px(cx), py(cy), 7 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD })
        c.drawCircle(px(cx), py(cy), 4 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PURPLE })
    }

    /** Side-by-side before/after image with dates and measurement changes. */
    fun renderCompare(
        a: Bitmap?, b: Bitmap?, dateA: String, dateB: String,
        rows: List<Triple<String, String, String>>, footer: String
    ): Bitmap {
        val w = 1440
        val photoH = 960
        val headerH = 110
        val rowH = 56
        val h = headerH + photoH + 40 + rows.size * rowH + 90
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF050308.toInt())
        val half = w / 2f
        val dp = paint(GOLD, 44f, bold = true, serif = true)
        c.drawText(Dates.medium(dateA), 32f, 72f, dp)
        c.drawText(Dates.medium(dateB), half + 32f, 72f, dp)
        drawPhotoFit(c, a, RectF(0f, headerH.toFloat(), half - 4f, (headerH + photoH).toFloat()))
        drawPhotoFit(c, b, RectF(half + 4f, headerH.toFloat(), w.toFloat(), (headerH + photoH).toFloat()))
        hairline(c, 32f, w - 32f, headerH + photoH + 20f, 1f)
        var y = headerH + photoH + 40f + 40f
        val lp = paint(MUTED, 30f, tracking = 0.08f)
        val vp = paint(IVORY, 34f, bold = true)
        rows.forEach { (label, left, right) ->
            c.drawText(label, 32f, y, lp)
            c.drawText(left, half - 32f - vp.measureText(left), y, vp)
            c.drawText(right, w - 32f - vp.measureText(right), y, vp)
            y += rowH
        }
        c.drawText(footer, 32f, y + 20f, paint(MUTED, 28f))
        return bmp
    }
}
