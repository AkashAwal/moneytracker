package com.akash.moneytracker.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.akash.moneytracker.R
import com.akash.moneytracker.data.TransactionWithCategory
import com.akash.moneytracker.data.TxType
import com.akash.moneytracker.domain.MonthInsights
import com.akash.moneytracker.domain.Money
import com.akash.moneytracker.domain.PeriodStat
import com.akash.moneytracker.domain.timeLabel
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Monthly report as a multi-page A4 PDF, drawn directly with Android's PdfDocument. */
object PdfReport {
    private const val W = 595
    private const val H = 842
    private const val M = 40f
    private const val CONTENT_W = W - 2 * M

    private val INK = Color.parseColor("#1D1D1F")
    private val GREY = Color.parseColor("#6E6E73")
    private val HAIR = Color.parseColor("#E8E8ED")
    private val BLUE = Color.parseColor("#0071E3")
    private val AMBER = Color.parseColor("#F5A623")
    private val GREEN = Color.parseColor("#1E9E5A")
    private val RED = Color.parseColor("#E5393B")

    fun build(
        context: Context,
        insights: MonthInsights,
        rows: List<TransactionWithCategory>,
        userName: String?,
        out: File,
    ) {
        val inter = ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.SANS_SERIF
        val monthName = insights.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())

        fun paint(size: Float, color: Int = INK, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = inter
                textSize = size
                this.color = color
                isFakeBoldText = bold
                textAlign = align
            }

        val w = Writer("Money Tracker · $monthName ${insights.month.year}", paint(8f, GREY, align = Paint.Align.CENTER))

        fun Writer.text(s: String, x: Float, p: Paint) = c.drawText(s, x, y, p)
        fun Writer.fit(s: String, maxWidth: Float, p: TextPaint) =
            TextUtils.ellipsize(s, p, maxWidth, TextUtils.TruncateAt.END).toString()
        fun Writer.rule() {
            c.drawRect(M, y, W - M, y + 0.6f, Paint().apply { color = HAIR })
        }
        fun Writer.section(title: String) {
            ensure(40f)
            y += 26f
            text(title, M, paint(14f, INK, bold = true))
            y += 10f
        }

        w.newPage()

        // Header
        w.y = M + 20f
        w.text("Money Tracker", M, paint(12f, GREY, bold = true))
        w.y += 28f
        w.text("$monthName ${insights.month.year}", M, paint(26f, INK, bold = true))
        w.y += 18f
        val who = userName?.takeIf { it.isNotBlank() }?.let { "Report for $it · " } ?: ""
        w.text("${who}Generated ${LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy"))}", M, paint(10f, GREY))

        // Summary tiles
        w.y += 26f
        val net = insights.income - insights.spent
        val tiles = listOf(
            Triple("Spent", Money.format(insights.spent), INK),
            Triple("Income", Money.format(insights.income), GREEN),
            Triple("Net", Money.format(net), if (net < 0) RED else INK),
        )
        val tileW = (CONTENT_W - 2 * 12f) / 3
        tiles.forEachIndexed { i, (label, value, color) ->
            val left = M + i * (tileW + 12f)
            val box = RectF(left, w.y, left + tileW, w.y + 58f)
            w.c.drawRoundRect(box, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.parseColor("#F5F5F7") })
            w.c.drawText(label, left + 14f, w.y + 22f, paint(10f, GREY))
            w.c.drawText(value, left + 14f, w.y + 44f, paint(17f, color, bold = true))
        }
        w.y += 58f
        if (insights.prevSpent > 0) {
            val change = ((insights.spent - insights.prevSpent) * 100.0 / insights.prevSpent).roundToInt()
            val prev = insights.month.minusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            w.y += 18f
            val msg = when {
                change == 0 -> "Same spending as $prev."
                change < 0 -> "${-change}% less than $prev."
                else -> "$change% more than $prev."
            }
            w.text(msg, M, paint(10f, if (change <= 0) GREEN else RED))
        }

        // Spending by category: donut + legend
        w.section("Spending by category")
        w.ensure(150f)
        val donutTop = w.y + 4f
        val donutSize = 120f
        val ring = RectF(M + 14f, donutTop + 14f, M + 14f + donutSize - 28f, donutTop + donutSize - 14f)
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 22f }
        var start = -90f
        insights.slices.forEach { s ->
            arc.color = s.color.toInt()
            val sweep = 360f * s.total / insights.spent
            w.c.drawArc(ring, start, (sweep - 1.5f).coerceAtLeast(0.5f), false, arc)
            start += sweep
        }
        val legendX = M + donutSize + 24f
        var ly = donutTop + 18f
        val legendText = paint(10f)
        val legendGrey = paint(10f, GREY, align = Paint.Align.RIGHT)
        insights.slices.forEach { s ->
            w.c.drawCircle(legendX + 4f, ly - 3f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.color.toInt() })
            w.c.drawText(s.name, legendX + 16f, ly, legendText)
            w.c.drawText(Money.format(s.total), W - M - 40f, ly, paint(10f, INK, bold = true, align = Paint.Align.RIGHT))
            w.c.drawText("${(s.total * 100.0 / insights.spent).roundToInt()}%", W - M, ly, legendGrey)
            ly += 17f
        }
        w.y = maxOf(donutTop + donutSize, ly) + 6f

        // Day by day
        w.section("Day by day")
        w.ensure(120f)
        val chartH = 80f
        val max = (insights.daily.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val slot = CONTENT_W / insights.daily.size
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE }
        val base = w.y + chartH
        insights.daily.forEachIndexed { i, v ->
            if (v > 0 && i < insights.daysCounted) {
                val h = (chartH * v / max).coerceAtLeast(2f)
                val left = M + i * slot + slot * 0.2f
                w.c.drawRoundRect(RectF(left, base - h, left + slot * 0.6f, base), 2f, 2f, barPaint)
            }
        }
        w.c.drawRect(M, base, W - M, base + 0.6f, Paint().apply { color = HAIR })
        val axis = paint(8f, GREY)
        listOf(1, 8, 15, 22, insights.daily.size).forEach { d ->
            val x = M + (d - 1) * slot
            w.c.drawText(d.toString(), if (d == insights.daily.size) x - 6f else x, base + 12f, axis)
        }
        w.y = base + 16f

        // When you spend
        if (insights.hasPatterns) {
            w.section("When you spend (average per day)")
            fun bars(label: String, stats: List<PeriodStat>) {
                w.ensure(24f + stats.size * 17f)
                w.y += 14f
                w.text(label, M, paint(10f, GREY, bold = true))
                val top = stats.maxOfOrNull { it.avgPerDay }?.coerceAtLeast(1L) ?: 1L
                stats.forEach { s ->
                    w.y += 17f
                    w.c.drawText(s.label, M, w.y, paint(10f))
                    val trackL = M + 80f
                    val trackR = W - M - 56f
                    w.c.drawRoundRect(RectF(trackL, w.y - 8f, trackR, w.y), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EFEFF4") })
                    val fill = trackL + (trackR - trackL) * s.avgPerDay / top
                    val color = when {
                        stats.size > 1 && s.avgPerDay == top -> AMBER
                        stats.size > 1 && s.avgPerDay == stats.minOf { it.avgPerDay } -> GREEN
                        else -> BLUE
                    }
                    w.c.drawRoundRect(RectF(trackL, w.y - 8f, fill.coerceAtLeast(trackL + 6f), w.y), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
                    w.c.drawText(Money.format(s.avgPerDay), W - M, w.y, paint(10f, INK, align = Paint.Align.RIGHT))
                }
                w.y += 6f
            }
            bars("Through the month", insights.weeks)
            bars("By weekday", insights.weekdays)
        }

        // Top items
        if (insights.items.isNotEmpty()) {
            w.section("Top items")
            insights.items.forEach { item ->
                w.ensure(20f)
                w.y += 16f
                w.text(w.fit(item.title, 300f, paint(10f)), M, paint(10f))
                w.c.drawText(if (item.count == 1) "1 time" else "${item.count} times", M + 330f, w.y, paint(10f, GREY))
                w.c.drawText(Money.format(item.total), W - M, w.y, paint(10f, INK, bold = true, align = Paint.Align.RIGHT))
            }
        }

        // All transactions
        w.section("All transactions (${rows.size})")
        val head = paint(9f, GREY, bold = true)
        val dateFmt = DateTimeFormatter.ofPattern("d MMM")
        w.y += 6f
        w.c.drawText("Date", M, w.y, head)
        w.c.drawText("Time", M + 56f, w.y, head)
        w.c.drawText("Category", M + 116f, w.y, head)
        w.c.drawText("Note", M + 226f, w.y, head)
        w.c.drawText("Amount", W - M, w.y, paint(9f, GREY, bold = true, align = Paint.Align.RIGHT))
        w.y += 6f
        w.rule()
        val cell = paint(9.5f)
        rows.forEach { r ->
            if (w.y + 20f > H - M - 20f) {
                w.newPage()
                w.y += 10f
            }
            w.y += 16f
            val isIncome = r.tx.type == TxType.INCOME
            w.c.drawText(LocalDate.ofEpochDay(r.tx.epochDay).format(dateFmt), M, w.y, cell)
            w.c.drawText(timeLabel(r.tx.minuteOfDay), M + 56f, w.y, cell)
            w.c.drawText(w.fit(r.category.name, 100f, cell), M + 116f, w.y, cell)
            w.c.drawText(w.fit(r.tx.note, 190f, paint(9.5f, GREY)), M + 226f, w.y, paint(9.5f, GREY))
            w.c.drawText(
                (if (isIncome) "+" else "-") + Money.format(r.tx.amountPaise),
                W - M, w.y, paint(9.5f, if (isIncome) GREEN else INK, align = Paint.Align.RIGHT),
            )
        }

        w.close(out)
    }

    /** Tracks the current page and vertical position, starting a new page when content won't fit. */
    private class Writer(private val footer: String, private val footerPaint: Paint) {
        private val doc = PdfDocument()
        private var pageNo = 0
        private lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
            private set
        var y = M

        private fun finishCurrent() {
            c.drawText("$footer · Page $pageNo", W / 2f, H - 24f, footerPaint)
            doc.finishPage(page)
        }

        fun newPage() {
            if (pageNo > 0) finishCurrent()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page.canvas
            y = M
        }

        fun ensure(height: Float) {
            if (y + height > H - M - 20f) newPage()
        }

        fun close(out: File) {
            finishCurrent()
            out.outputStream().use { doc.writeTo(it) }
            doc.close()
        }
    }
}
