package com.fitlens.companion.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Photo
import com.fitlens.companion.data.Records
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.fmtSigned
import com.fitlens.companion.ui.defOrder
import com.fitlens.companion.ui.describeSet
import com.fitlens.companion.video.FrameRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/** What goes into a PDF report. Dates are ISO (yyyy-MM-dd) and inclusive. */
data class ReportOptions(
    val from: String,
    val to: String,
    /** Dark pages match the app; light pages are better for printing. */
    val dark: Boolean = true,
    val measurements: Boolean = true,
    val training: Boolean = true,
    val dailyLog: Boolean = true,
    /** 0–4 photos shown per day in the daily log. */
    val photosPerDay: Int = 2,
    val onlyPhotoDays: Boolean = false,
    val highQuality: Boolean = false
)

private class Palette(
    val bg: Int, val card: Int, val text: Int, val muted: Int,
    val gold: Int, val purple: Int, val hairline: Int, val grid: Int
)

private val DARK = Palette(
    bg = 0xFF050308.toInt(), card = 0xFF151019.toInt(), text = 0xFFF7F3EA.toInt(), muted = 0xFFBDB3C6.toInt(),
    gold = 0xFFD4AF37.toInt(), purple = 0xFFB48BDB.toInt(), hairline = 0xFF3A2F44.toInt(), grid = 0x33FFFFFF
)

// Deeper gold and purple on white keep text readable when printed.
private val LIGHT = Palette(
    bg = 0xFFFFFFFF.toInt(), card = 0xFFF6F2FA.toInt(), text = 0xFF1A1320.toInt(), muted = 0xFF5E5566.toInt(),
    gold = 0xFF9C7A1E.toInt(), purple = 0xFF4B1E6E.toInt(), hairline = 0xFFD8CFE0.toInt(), grid = 0x22000000
)

private const val PW = 595 // A4 in points
private const val PH = 842
private const val M = 40f
private const val CW = PW - 2 * M
private const val BOTTOM = PH - 56f

private fun paint(color: Int, size: Float, bold: Boolean = false, serif: Boolean = false, tracking: Float = 0f, italic: Boolean = false) =
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

private fun wrap(text: String, p: Paint, width: Float): List<String> {
    val out = ArrayList<String>()
    for (para in text.split('\n')) {
        var line = ""
        for (word in para.split(' ')) {
            val trial = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(trial) <= width || line.isEmpty()) line = trial else { out.add(line); line = word }
        }
        out.add(line)
    }
    return out
}

/** Starts pages as needed and draws the footer on each one. */
private class PageWriter(val doc: PdfDocument, val p: Palette, val footer: String) {
    var pages = 0
        private set
    private var page: PdfDocument.Page? = null
    lateinit var c: Canvas
        private set
    var y = M

    fun newPage() {
        finish()
        pages++
        val pg = doc.startPage(PdfDocument.PageInfo.Builder(PW, PH, pages).create())
        page = pg
        c = pg.canvas
        c.drawColor(p.bg)
        c.drawRect(M, PH - 40f, PW - M, PH - 39.4f, fill(p.hairline))
        val fp = paint(p.muted, 7.5f)
        val num = "Page $pages"
        c.drawText(footer, M, PH - 26f, fp)
        c.drawText(num, PW - M - fp.measureText(num), PH - 26f, fp)
        y = M
    }

    fun finish() {
        page?.let { doc.finishPage(it) }
        page = null
    }

    /** Starts a new page if [h] points don't fit. Returns true when it did. */
    fun ensure(h: Float): Boolean {
        if (page == null || y + h > BOTTOM) {
            newPage()
            return true
        }
        return false
    }

    fun centered(text: String, pt: Paint, baseline: Float) = c.drawText(text, (PW - pt.measureText(text)) / 2f, baseline, pt)

    fun rightText(text: String, pt: Paint, right: Float, baseline: Float) = c.drawText(text, right - pt.measureText(text), baseline, pt)

    fun section(title: String, subtitle: String? = null) {
        newPage()
        c.drawText(title.uppercase(), M, y + 12f, paint(p.gold, 9f, bold = true, tracking = 0.25f))
        c.drawRect(M, y + 20f, M + CW, y + 20.8f, fill(p.gold))
        y += 30f
        if (subtitle != null) {
            c.drawText(subtitle, M, y + 10f, paint(p.muted, 9f))
            y += 22f
        }
    }
}

object PdfReport {

    /** Days in the report, oldest first. */
    fun daysIn(snap: Snapshot, o: ReportOptions): List<String> =
        snap.allDates.filter { it >= o.from && it <= o.to }
            .filter { !o.onlyPhotoDays || snap.photosByDate.containsKey(it) }
            .sorted()

    /** Rough number of photos the report will embed, used to warn about very large reports. */
    fun photoCount(snap: Snapshot, o: ReportOptions): Int =
        if (!o.dailyLog) 2 else daysIn(snap, o).sumOf { min(o.photosPerDay, snap.photosByDate[it]?.size ?: 0) } + 2

    /** Writes the report to [os]. Returns the number of pages. */
    suspend fun create(snap: Snapshot, o: ReportOptions, os: OutputStream, progress: (String) -> Unit): Int =
        withContext(Dispatchers.Default) {
            val doc = PdfDocument()
            try {
                val w = PageWriter(doc, if (o.dark) DARK else LIGHT, "FitLens progress report · ${Dates.medium(o.from)} – ${Dates.medium(o.to)}")
                val days = daysIn(snap, o)
                progress("Creating PDF… cover")
                cover(w, snap, o, days)
                if (o.measurements) { progress("Creating PDF… measurements"); measurements(w, snap, o) }
                if (o.training) { progress("Creating PDF… training"); training(w, snap, o) }
                if (o.dailyLog) daily(w, snap, o, days, progress)
                w.finish()
                progress("Saving PDF…")
                withContext(Dispatchers.IO) { doc.writeTo(os) }
                w.pages
            } finally {
                doc.close()
            }
        }

    // ---------- Photos ----------

    private fun drawPhoto(w: PageWriter, snap: Snapshot, photo: Photo, r: RectF, hq: Boolean) {
        val c = w.c
        c.drawRect(r, fill(w.p.card))
        val scale = if (hq) 2.5f else 1.5f
        val longest = (max(r.width(), r.height()) * scale * 1.34f).toInt()
        FrameRenderer.loadBitmap(snap.photoFile(photo), longest, longest)?.let { src ->
            // Standard quality uses half the memory per photo; the PDF keeps every photo until it's saved.
            val bmp = if (hq) src else src.copy(Bitmap.Config.RGB_565, false) ?: src
            val s = max(r.width() / bmp.width, r.height() / bmp.height)
            val sw = r.width() / s
            val sh = r.height() / s
            val sx = (bmp.width - sw) / 2f
            val sy = (bmp.height - sh) / 2f
            c.drawBitmap(bmp, Rect(sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt()), r, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        c.drawRect(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = w.p.gold })
    }

    // ---------- Cover ----------

    private fun cover(w: PageWriter, snap: Snapshot, o: ReportOptions, days: List<String>) {
        w.newPage()
        val p = w.p
        val c = w.c
        w.centered("FITLENS", paint(p.gold, 13f, bold = true, tracking = 0.5f), 96f)
        c.drawRect(PW / 2f - 40f, 108f, PW / 2f + 40f, 108.8f, fill(p.gold))
        w.centered("Progress Report", paint(p.text, 34f, serif = true), 156f)
        w.centered("${Dates.medium(o.from)} – ${Dates.medium(o.to)}", paint(p.muted, 12f, tracking = 0.05f), 182f)

        val photos = snap.datedPhotos.filter { it.date!! >= o.from && it.date <= o.to }
        val first = photos.firstOrNull()
        val last = photos.lastOrNull()
        val top = 214f
        val boxH = 340f
        val boxW = boxH * 0.75f
        val label = paint(p.muted, 8f, bold = true, tracking = 0.18f)
        if (first != null && last != null && first.id != last.id) {
            val gap = 18f
            val left = (PW - (2 * boxW + gap)) / 2f
            drawPhoto(w, snap, first, RectF(left, top, left + boxW, top + boxH), o.highQuality)
            drawPhoto(w, snap, last, RectF(left + boxW + gap, top, left + 2 * boxW + gap, top + boxH), o.highQuality)
            val a = "BEFORE · ${Dates.medium(first.date!!).uppercase()}"
            val b = "LATEST · ${Dates.medium(last.date!!).uppercase()}"
            c.drawText(a, left + (boxW - label.measureText(a)) / 2f, top + boxH + 18f, label)
            c.drawText(b, left + boxW + gap + (boxW - label.measureText(b)) / 2f, top + boxH + 18f, label)
        } else if (first != null) {
            val left = (PW - boxW) / 2f
            drawPhoto(w, snap, first, RectF(left, top, left + boxW, top + boxH), o.highQuality)
            w.centered(Dates.medium(first.date!!).uppercase(), label, top + boxH + 18f)
        }

        val stats = listOf(
            "Days logged" to days.size,
            "Photos" to photos.size,
            "Workouts" to days.count { snap.setsByDate.containsKey(it) },
            "Measurements" to days.sumOf { snap.recordsByDate[it]?.size ?: 0 }
        )
        val colW = CW / stats.size
        val valueP = paint(p.gold, 24f, serif = true)
        val labelP = paint(p.muted, 7.5f, bold = true, tracking = 0.2f)
        stats.forEachIndexed { i, (name, v) ->
            val cx = M + colW * i + colW / 2f
            val vs = v.toString()
            c.drawText(vs, cx - valueP.measureText(vs) / 2f, 652f, valueP)
            val ls = name.uppercase()
            c.drawText(ls, cx - labelP.measureText(ls) / 2f, 670f, labelP)
        }
        c.drawRect(M, 700f, M + CW, 700.6f, fill(p.hairline))
        w.centered("Generated ${Dates.medium(Dates.today())} with FitLens", paint(p.muted, 8f), 724f)
    }

    // ---------- Measurements ----------

    private fun measurements(w: PageWriter, snap: Snapshot, o: ReportOptions) {
        val series = snap.usedMeasurements.mapNotNull { m ->
            val pts = snap.dailySeries(m.name).filter { it.date >= o.from && it.date <= o.to }
            if (pts.isEmpty()) null else Triple(m.name, m.unit.ifBlank { pts.last().unit }, pts)
        }
        w.section("Body measurements", if (series.isEmpty()) "No measurements in this period." else "${series.size} measurements")
        val p = w.p
        val photoDays = snap.photosByDate.keys.filter { it >= o.from && it <= o.to }.map { Dates.epochDay(it) }
        val cardH = 138f
        for ((name, unit, pts) in series) {
            w.ensure(cardH + 12f)
            val c = w.c
            val top = w.y
            c.drawRoundRect(RectF(M, top, M + CW, top + cardH), 6f, 6f, fill(p.card))
            c.drawText(name, M + 14f, top + 24f, paint(p.text, 14f, serif = true))
            if (unit.isNotBlank()) c.drawText(unit, M + 14f, top + 37f, paint(p.muted, 8f))

            val first = pts.first()
            val last = pts.last()
            val low = pts.minBy { it.value }
            val high = pts.maxBy { it.value }
            val rows = listOf(
                "Start" to "${fmtNum(first.value)}  ·  ${Dates.short(first.date)}",
                "Latest" to "${fmtNum(last.value)}  ·  ${Dates.short(last.date)}",
                "Change" to fmtSigned(last.value - first.value),
                "Low" to "${fmtNum(low.value)}  ·  ${Dates.short(low.date)}",
                "High" to "${fmtNum(high.value)}  ·  ${Dates.short(high.date)}"
            )
            val lp = paint(p.muted, 7f, bold = true, tracking = 0.15f)
            val vp = paint(p.text, 9f, bold = true)
            rows.forEachIndexed { i, (l, v) ->
                val by = top + 56f + i * 15f
                c.drawText(l.uppercase(), M + 14f, by, lp)
                c.drawText(v, M + 62f, by, vp)
            }

            val chart = RectF(M + CW * 0.42f, top + 16f, M + CW - 14f, top + cardH - 24f)
            if (pts.size >= 2) {
                lineChart(w, pts.map { Dates.epochDay(it.date) to it.value }, photoDays, chart)
                val dp = paint(p.muted, 7f)
                c.drawText(Dates.short(first.date), chart.left, chart.bottom + 13f, dp)
                w.rightText(Dates.short(last.date), dp, chart.right, chart.bottom + 13f)
            } else {
                c.drawText("One entry in this period", chart.left, chart.centerY(), paint(p.muted, 8.5f, italic = true))
            }
            w.y = top + cardH + 12f
        }
    }

    private fun lineChart(w: PageWriter, pts: List<Pair<Long, Double>>, photoDays: List<Long>, r: RectF) {
        val c = w.c
        val p = w.p
        val x0 = pts.first().first
        val x1 = max(pts.last().first, x0 + 1)
        var y0 = pts.minOf { it.second }
        var y1 = pts.maxOf { it.second }
        if (y1 - y0 < 1e-6) { y0 -= 1; y1 += 1 }
        val pad = (y1 - y0) * 0.12
        y0 -= pad; y1 += pad
        fun px(x: Long) = r.left + (x - x0).toFloat() / (x1 - x0).toFloat() * r.width()
        fun py(y: Double) = r.bottom - ((y - y0) / (y1 - y0)).toFloat() * r.height()

        val grid = fill(p.grid)
        for (i in 0..3) {
            val gy = r.top + r.height() * i / 3f
            c.drawRect(r.left, gy, r.right, gy + 0.4f, grid)
        }
        val ticks = fill(p.purple)
        photoDays.filter { it in x0..x1 }.forEach { d -> c.drawRect(px(d) - 0.5f, r.bottom - 5f, px(d) + 0.5f, r.bottom, ticks) }
        val path = Path()
        pts.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y)) }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.6f; color = p.gold
            strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        })
        if (pts.size <= 60) pts.forEach { (x, y) -> c.drawCircle(px(x), py(y), 1.6f, fill(p.gold)) }
        val lp = paint(p.muted, 6.5f)
        w.rightText(fmtNum(y1 - pad), lp, r.left - 3f, r.top + 6f)
        w.rightText(fmtNum(y0 + pad), lp, r.left - 3f, r.bottom)
    }

    // ---------- Training ----------

    private fun e1rm(s: SetRow): Double = Records.oneRepMax(s)

    private fun training(w: PageWriter, snap: Snapshot, o: ReportOptions) {
        val sets = snap.sets.filter { it.date >= o.from && it.date <= o.to }
        val workouts = sets.map { it.date }.distinct().size
        w.section(
            "Training",
            if (sets.isEmpty()) "No workouts in this period."
            else "$workouts workouts · ${sets.size} sets · volume ${fmtNum(snap.weight(sets.sumOf { it.weightKg * it.reps }), 0)} ${snap.weightUnit}"
        )
        if (sets.isEmpty()) return
        val p = w.p
        val cols = floatArrayOf(M + 8f, M + CW * 0.50f, M + CW * 0.63f, M + CW * 0.86f)
        val head = paint(p.gold, 7f, bold = true, tracking = 0.18f)
        fun header() {
            listOf("Exercise", "Sessions", "Best set", "Est. 1RM").forEachIndexed { i, h -> w.c.drawText(h.uppercase(), cols[i], w.y + 10f, head) }
            w.c.drawRect(M, w.y + 15f, M + CW, w.y + 15.6f, fill(p.hairline))
            w.y += 22f
        }
        header()
        val rowP = paint(p.text, 9f)
        val boldP = paint(p.text, 9f, bold = true)
        val rows = sets.groupBy { it.exerciseId }.entries
            .sortedWith(compareByDescending<Map.Entry<Long, List<SetRow>>> { e -> e.value.map { it.date }.distinct().size }
                .thenBy { snap.exercises[it.key]?.name ?: "" })
        rows.forEachIndexed { i, (exId, exSets) ->
            if (w.ensure(17f)) header()
            val c = w.c
            if (i % 2 == 0) c.drawRect(M, w.y - 2f, M + CW, w.y + 13f, fill(p.card))
            val cat = snap.categoryOf(exId)
            if (cat != null) c.drawCircle(M + 3f, w.y + 5.5f, 2f, fill(cat.colour or 0xFF000000.toInt()))
            val name = snap.exercises[exId]?.name ?: "Exercise #$exId"
            var shown = name
            while (rowP.measureText(shown) > cols[1] - cols[0] - 8f && shown.length > 4) shown = shown.dropLast(2) + "…"
            c.drawText(shown, cols[0], w.y + 9f, boldP)
            c.drawText(exSets.map { it.date }.distinct().size.toString(), cols[1], w.y + 9f, rowP)
            val best = exSets.maxByOrNull { e1rm(it) }?.takeIf { e1rm(it) > 0 }
            val bestText = if (best != null) describeSet(snap, best.weightKg, best.reps, best.distance, best.durationSec)
            else exSets.maxByOrNull { it.distance + it.durationSec }?.let { describeSet(snap, it.weightKg, it.reps, it.distance, it.durationSec) } ?: "—"
            c.drawText(bestText, cols[2], w.y + 9f, rowP)
            c.drawText(best?.let { "${fmtNum(snap.weight(e1rm(it)), 1)} ${snap.weightUnit}" } ?: "—", cols[3], w.y + 9f, rowP)
            w.y += 15f
        }
    }

    // ---------- Daily log ----------

    private fun workoutSeconds(start: String, end: String): Long = Dates.secondsBetween(start, end)

    private fun daily(w: PageWriter, snap: Snapshot, o: ReportOptions, days: List<String>, progress: (String) -> Unit) {
        w.section("Daily log", if (days.isEmpty()) "Nothing logged in this period." else "${days.size} days")
        val p = w.p
        val startDay = days.firstOrNull()?.let { Dates.epochDay(it) } ?: 0L
        val small = paint(p.gold, 7f, bold = true, tracking = 0.2f)
        val text = paint(p.text, 9.5f)
        val bold = paint(p.text, 9.5f, bold = true)
        val muted = paint(p.muted, 8.5f)
        val italic = paint(p.muted, 8.5f, italic = true)

        days.forEachIndexed { index, d ->
            if (index % 5 == 0) progress("Creating PDF… day ${index + 1} of ${days.size}")
            val photos = snap.photosByDate[d].orEmpty().take(o.photosPerDay.coerceIn(0, 4))
            val recs = snap.recordsByDate[d].orEmpty().sortedWith(compareBy({ defOrder(snap, it.name) }, { it.time }))
            val sets = snap.setsByDate[d].orEmpty()
            val comments = snap.workoutComments[d].orEmpty()

            val n = photos.size
            val boxW = if (n == 0) 0f else min(150f, (CW - (n - 1) * 10f) / n)
            val boxH = boxW * 4f / 3f
            // Keep the date header together with its photos.
            w.ensure(34f + (if (n > 0) boxH + 24f else 0f) + 16f)

            fun header(continued: Boolean) {
                val title = Dates.long(d) + if (continued) "  (continued)" else ""
                w.c.drawText(title, M, w.y + 16f, paint(p.text, if (continued) 11f else 15f, serif = true))
                w.rightText("DAY ${Dates.epochDay(d) - startDay + 1}", small, M + CW, w.y + 14f)
                w.y += if (continued) 24f else 30f
            }
            fun line(h: Float) { if (w.ensure(h)) header(true) }

            header(false)
            if (n > 0) {
                photos.forEachIndexed { i, ph ->
                    val left = M + i * (boxW + 10f)
                    drawPhoto(w, snap, ph, RectF(left, w.y, left + boxW, w.y + boxH), o.highQuality)
                    if (ph.pose.isNotBlank()) w.c.drawText(ph.pose.uppercase(), left, w.y + boxH + 11f, small)
                }
                val more = (snap.photosByDate[d]?.size ?: 0) - n
                if (more > 0) w.rightText("+$more more", muted, M + CW, w.y + boxH + 11f)
                w.y += boxH + 22f
            }

            if (recs.isNotEmpty()) {
                line(30f)
                w.c.drawText("BODY", M, w.y + 8f, small)
                w.y += 14f
                recs.forEach { r ->
                    line(14f)
                    val prev = snap.recordsByName[r.name]?.lastOrNull { it.date < r.date }
                    w.c.drawText(r.name, M, w.y + 9f, text)
                    prev?.let { w.c.drawText("${fmtSigned(r.value - it.value)} since ${Dates.short(it.date)}", M + CW * 0.45f, w.y + 9f, muted) }
                    w.rightText("${fmtNum(r.value)} ${r.unit}".trim(), bold, M + CW, w.y + 9f)
                    w.y += 14f
                }
                w.y += 6f
            }

            if (sets.isNotEmpty() || comments.isNotEmpty()) {
                line(30f)
                w.c.drawText("WORKOUT", M, w.y + 8f, small)
                val total = snap.workoutTimes[d]?.sumOf { workoutSeconds(it.start, it.end) } ?: 0L
                val info = listOfNotNull(
                    if (total > 0) "Duration ${fmtDuration(total.toInt())}" else null,
                    if (sets.isNotEmpty()) "${sets.size} sets" else null,
                    if (sets.isNotEmpty()) "Volume ${fmtNum(snap.weight(sets.sumOf { it.weightKg * it.reps }), 0)} ${snap.weightUnit}" else null
                ).joinToString("  ·  ")
                w.rightText(info, muted, M + CW, w.y + 8f)
                w.y += 16f
                comments.forEach { cm ->
                    wrap("“$cm”", italic, CW).forEach { l -> line(12f); w.c.drawText(l, M, w.y + 9f, italic); w.y += 12f }
                }
                sets.groupBy { it.exerciseId }.entries.sortedBy { e -> e.value.minOf { it.id } }.forEach { (exId, exSets) ->
                    line(28f)
                    val cat = snap.categoryOf(exId)
                    if (cat != null) w.c.drawCircle(M + 3f, w.y + 6f, 2.4f, fill(cat.colour or 0xFF000000.toInt()))
                    w.c.drawText(snap.exercises[exId]?.name ?: "Exercise #$exId", M + 10f, w.y + 9.5f, bold)
                    w.y += 14f
                    val setText = exSets.mapIndexed { i, s ->
                        "${i + 1}. " + describeSet(snap, s.weightKg, s.reps, s.distance, s.durationSec) + if (s.isPr) " (PR)" else ""
                    }.joinToString("     ")
                    wrap(setText, text, CW - 10f).forEach { l -> line(12.5f); w.c.drawText(l, M + 10f, w.y + 9f, text); w.y += 12.5f }
                    exSets.filter { !it.comment.isNullOrBlank() }.forEach { s ->
                        wrap("“${s.comment}”", italic, CW - 10f).forEach { l -> line(12f); w.c.drawText(l, M + 10f, w.y + 9f, italic); w.y += 12f }
                    }
                    w.y += 4f
                }
            }

            if (photos.isEmpty() && recs.isEmpty() && sets.isEmpty() && comments.isEmpty()) {
                line(14f)
                w.c.drawText("Photos on this day aren't included (photos per day is set to 0).", M, w.y + 9f, italic)
                w.y += 14f
            }
            w.y += 6f
            if (w.y + 14f < BOTTOM) w.c.drawRect(M, w.y, M + CW, w.y + 0.6f, fill(p.hairline))
            w.y += 14f
        }
    }
}
