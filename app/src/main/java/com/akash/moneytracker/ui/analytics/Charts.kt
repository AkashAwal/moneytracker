package com.akash.moneytracker.ui.analytics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import com.akash.moneytracker.domain.CategorySlice
import com.akash.moneytracker.domain.Money
import com.akash.moneytracker.domain.PeriodStat
import com.akash.moneytracker.ui.theme.extra
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import java.time.YearMonth

/** Ring chart of spending by category, with the total in the middle. */
@Composable
fun DonutChart(slices: List<CategorySlice>, total: Long, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val stroke = 28.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))

            val gap = if (slices.size > 1) 3f else 0f
            var start = -90f
            slices.forEach { s ->
                val sweep = 360f * s.total / total
                val drawn = (sweep - gap).coerceAtLeast(0.5f) * progress.value
                drawArc(
                    color = Color(s.color),
                    startAngle = start + gap / 2,
                    sweepAngle = drawn,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Money.format(total), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        }
    }
}

/** One bar per day of the month. Tap a bar to select it. */
@Composable
fun DailyBars(
    daily: List<Long>,
    daysCounted: Int,
    selectedDay: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val max = (daily.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val bar = MaterialTheme.colorScheme.primary
    val highlight = MaterialTheme.colorScheme.tertiary
    val future = MaterialTheme.colorScheme.surfaceContainerHighest
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val progress = remember { Animatable(0f) }
    LaunchedEffect(daily) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(daily.size) {
                    detectTapGestures { pos ->
                        val slot = size.width.toFloat() / daily.size
                        val day = (pos.x / slot).toInt() + 1
                        onSelect(if (day == selectedDay || day > daysCounted) null else day)
                    }
                },
        ) {
            val slot = size.width / daily.size
            val barWidth = slot * 0.62f
            val chartHeight = size.height - 2.dp.toPx()
            daily.forEachIndexed { i, value ->
                val day = i + 1
                val h = if (day > daysCounted) 3.dp.toPx() else {
                    (chartHeight * value / max * progress.value).coerceAtLeast(if (value > 0) 3.dp.toPx() else 0f)
                }
                if (h > 0f) {
                    drawRoundRect(
                        color = when {
                            day > daysCounted -> future
                            day == selectedDay -> highlight
                            else -> bar
                        },
                        topLeft = Offset(i * slot + (slot - barWidth) / 2, chartHeight - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth / 2.5f),
                    )
                }
            }
            drawLine(baseline, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val last = daily.size
            listOf(1, 8, 15, 22, last).forEach {
                Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A month laid out as a calendar, each day shaded by how much was spent. Tap a day to select it. */
@Composable
fun CalendarHeatmap(
    month: YearMonth,
    daily: List<Long>,
    daysCounted: Int,
    selectedDay: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val max = (daily.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val leading = month.atDay(1).dayOfWeek.value - 1 // weeks start on Monday
    val cells = List(leading) { 0 } + (1..daily.size).toList()
    val rows = cells.chunked(7)
    val accent = MaterialTheme.colorScheme.tertiary

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        rows.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val cellModifier = Modifier.weight(1f).aspectRatio(1f)
                    if (day == 0) {
                        Box(cellModifier)
                    } else {
                        val value = daily[day - 1]
                        val future = day > daysCounted
                        val shade = if (value > 0 && !future) 0.18f + 0.72f * (value.toFloat() / max) else 0f
                        val background = when {
                            future -> Color.Transparent
                            value > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = shade)
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val textColor = if (shade > 0.55f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        Box(
                            cellModifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(background)
                                .then(if (day == selectedDay) Modifier.border(2.dp, accent, RoundedCornerShape(8.dp)) else Modifier)
                                .clickable(enabled = !future) { onSelect(if (day == selectedDay) null else day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (future) MaterialTheme.colorScheme.outline else textColor,
                            )
                        }
                    }
                }
                // Pad a short last week so its cells keep the same width as the others.
                repeat(7 - week.size) { Box(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

/** Horizontal bars of per-day averages; the highest is amber and the lowest green. */
@Composable
fun AverageBars(stats: List<PeriodStat>, modifier: Modifier = Modifier) {
    val max = (stats.maxOfOrNull { it.avgPerDay } ?: 0L).coerceAtLeast(1L)
    val maxStat = stats.maxByOrNull { it.avgPerDay }
    val minStat = stats.minByOrNull { it.avgPerDay }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(stats) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.forEach { s ->
            val color = when {
                stats.size > 1 && s === maxStat -> MaterialTheme.colorScheme.tertiary
                stats.size > 1 && s === minStat -> MaterialTheme.extra.positive
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(76.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = (s.avgPerDay.toFloat() / max * progress.value).coerceIn(0.02f, 1f))
                            .clip(CircleShape)
                            .background(color),
                    )
                }
                Text(
                    Money.format(s.avgPerDay),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(72.dp),
                )
            }
        }
    }
}
