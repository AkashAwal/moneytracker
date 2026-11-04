package com.akash.moneytracker.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.akash.moneytracker.domain.epochRange
import com.akash.moneytracker.export.Exporter
import com.akash.moneytracker.export.PdfReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.akash.moneytracker.data.TransactionWithCategory
import com.akash.moneytracker.domain.MonthInsights
import com.akash.moneytracker.domain.Money
import com.akash.moneytracker.domain.PeriodStat
import com.akash.moneytracker.domain.label
import com.akash.moneytracker.ui.components.AppCard
import com.akash.moneytracker.ui.components.CategoryBadge
import com.akash.moneytracker.ui.components.EmptyState
import com.akash.moneytracker.ui.components.Hairline
import com.akash.moneytracker.ui.theme.extra
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AnalyticsScreen(transactions: List<TransactionWithCategory>?, userName: String?) {
    if (transactions == null) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val currentMonth = remember { YearMonth.from(today) }
    val earliest = remember(transactions) {
        transactions.minOfOrNull { it.tx.epochDay }?.let { YearMonth.from(LocalDate.ofEpochDay(it)) } ?: currentMonth
    }
    var month by remember { mutableStateOf(currentMonth) }
    var selectedDay by remember(month) { mutableStateOf<Int?>(null) }
    val insights = remember(transactions, month) { MonthInsights.compute(transactions, month, today) }

    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Insights", style = MaterialTheme.typography.headlineLarge)
                    IconButton(
                        enabled = insights.spent > 0 && !exporting,
                        onClick = {
                            exporting = true
                            scope.launch {
                                try {
                                    val rows = transactions.filter { it.tx.epochDay in month.epochRange() }
                                    val file = withContext(Dispatchers.IO) {
                                        Exporter.newFile(context, "money-tracker-$month.pdf").also {
                                            PdfReport.build(context, insights, rows, userName, it)
                                        }
                                    }
                                    Exporter.share(context, file, "application/pdf", "Share report")
                                } finally {
                                    exporting = false
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Export PDF report")
                    }
                }
                MonthSwitcher(
                    month = month,
                    canGoBack = month.isAfter(earliest),
                    canGoForward = month.isBefore(currentMonth),
                    onBack = { month = month.minusMonths(1) },
                    onForward = { month = month.plusMonths(1) },
                )
            }
        }

        if (insights.spent == 0L) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    EmptyState("📊", "No spending in ${month.label()}", "Expenses you log will show up here as charts.")
                }
            }
            return@LazyColumn
        }

        item { SummaryCard(insights) }
        item { CategoryCard(insights) }
        item {
            DailyCard(insights, selectedDay) { selectedDay = it }
        }
        item { CalendarCard(insights, selectedDay) { selectedDay = it } }
        item { WhenCard(insights) }
        item { TopItemsCard(insights) }
        if (insights.tags.isNotEmpty()) item { TagsCard(insights) }
    }
}

@Composable
private fun MonthSwitcher(
    month: YearMonth,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = canGoBack) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous month")
        }
        Text(month.label(), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Next month")
        }
    }
}

/**
 * A chart card with a share button that exports just the card as a PNG. The button sits outside the
 * recorded layer so it doesn't appear in the image.
 */
@Composable
private fun InsightCard(
    title: String,
    subtitle: String?,
    fileName: String,
    footer: String,
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background.toArgb()

    Box(Modifier.fillMaxWidth()) {
        AppCard(
            Modifier
                .fillMaxWidth()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
        IconButton(
            onClick = {
                scope.launch {
                    Exporter.sharePng(context, layer.toImageBitmap().asAndroidBitmap(), fileName, footer, background)
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        ) {
            Icon(Icons.Outlined.Share, contentDescription = "Share chart as image", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun footer(i: MonthInsights) = "Money Tracker · ${i.month.label()}"

private fun fileName(i: MonthInsights, chart: String) = "money-tracker-$chart-${i.month}"

@Composable
private fun SummaryCard(i: MonthInsights) {
    val prevLabel = i.month.minusMonths(1).month.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Spent in ${i.month.month.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Money.format(i.spent), style = MaterialTheme.typography.displayMedium)
            if (i.prevSpent > 0) {
                val change = ((i.spent - i.prevSpent) * 100.0 / i.prevSpent).roundToInt()
                val less = change <= 0
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        change == 0 -> "Same as $prevLabel"
                        less -> "${abs(change)}% less than $prevLabel"
                        else -> "$change% more than $prevLabel"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (less) MaterialTheme.extra.positive else MaterialTheme.extra.negative,
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(i: MonthInsights) {
    InsightCard("Where it went", "Spending by category", fileName(i, "categories"), footer(i)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            DonutChart(i.slices, i.spent)
            Spacer(Modifier.height(16.dp))
        }
        i.slices.forEachIndexed { idx, s ->
            if (idx > 0) Hairline()
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(s.color)))
                Text("  ${s.emoji}  ${s.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(Money.format(s.total), style = MaterialTheme.typography.titleSmall)
                Text(
                    "  ${(s.total * 100.0 / i.spent).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DailyCard(i: MonthInsights, selectedDay: Int?, onSelect: (Int?) -> Unit) {
    val monthShort = i.month.month.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0])
    InsightCard(
        "Day by day",
        if (selectedDay == null) "Tap a bar · recurring bills left out" else "$selectedDay $monthShort · ${Money.format(i.daily[selectedDay - 1])}",
        fileName(i, "daily"),
        footer(i),
    ) {
        DailyBars(i.daily, i.daysCounted, selectedDay, onSelect)
    }
}

@Composable
private fun CalendarCard(i: MonthInsights, selectedDay: Int?, onSelect: (Int?) -> Unit) {
    val monthShort = i.month.month.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0])
    InsightCard(
        "Calendar",
        if (selectedDay == null) "Darker days are where you spent more · bills left out" else "$selectedDay $monthShort · ${Money.format(i.daily[selectedDay - 1])}",
        fileName(i, "calendar"),
        footer(i),
    ) {
        CalendarHeatmap(i.month, i.daily, i.daysCounted, selectedDay, onSelect)
    }
}

@Composable
private fun TagsCard(i: MonthInsights) {
    InsightCard("Tags", "Spending by tag, across categories", fileName(i, "tags"), footer(i)) {
        i.tags.forEachIndexed { idx, t ->
            if (idx > 0) Hairline()
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("#${t.tag}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (t.count == 1) "1 transaction" else "${t.count} transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(Money.format(t.total), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun WhenCard(i: MonthInsights) {
    InsightCard("When you spend", "Average per day · recurring bills left out", fileName(i, "patterns"), footer(i)) {
        if (!i.hasPatterns) {
            Text(
                "Log a few more days and your spending pattern will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@InsightCard
        }
        Text("Through the month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        AverageBars(i.weeks)
        Takeaway(extremesSentence(i.weeks, "stretch"))

        Spacer(Modifier.height(20.dp))
        Text("By weekday", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        AverageBars(i.weekdays)
        Takeaway(extremesSentence(i.weekdays, "day"))
    }
}

/** One-line summary under a chart; skipped when there isn't enough to compare. */
@Composable
private fun Takeaway(text: String) {
    if (text.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

/** "Days 8–14 is your priciest stretch (₹420 a day). Days 22–28 is the quietest (₹90 a day)." */
private fun extremesSentence(stats: List<PeriodStat>, noun: String): String {
    if (stats.size < 2) return ""
    val high = stats.maxBy { it.avgPerDay }
    val low = stats.minBy { it.avgPerDay }
    if (high.avgPerDay == low.avgPerDay) return "Your spending is even across the ${noun}s."
    return "${high.label} is your priciest $noun (${Money.format(high.avgPerDay)} a day). " +
        "${low.label} is the quietest (${Money.format(low.avgPerDay)} a day)."
}

@Composable
private fun TopItemsCard(i: MonthInsights) {
    InsightCard("Top items", "What you spent the most on", fileName(i, "top-items"), footer(i)) {
        i.items.forEachIndexed { idx, item ->
            if (idx > 0) Hairline()
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(item.emoji, MaterialTheme.colorScheme.primary, size = 36.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                        if (item.count == 1) "1 time" else "${item.count} times",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(Money.format(item.total), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
