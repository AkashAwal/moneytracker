package com.akash.moneytracker.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM")
private val dayYearFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")

/** yyyyMM, used to remember which month the savings goal belongs to. */
val LocalDate.monthKey: Int get() = year * 100 + monthValue

fun YearMonth.epochRange(): LongRange = atDay(1).toEpochDay()..atEndOfMonth().toEpochDay()

fun YearMonth.label(): String = format(monthFormat)

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

/** "2:30 PM" from minutes after midnight. */
fun timeLabel(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(timeFormat)

fun nowMinuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

/** "Today", "Yesterday", or "Mon, 3 Nov" (with the year when it isn't this one). */
fun dayLabel(epochDay: Long, today: LocalDate): String {
    val date = LocalDate.ofEpochDay(epochDay)
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> if (date.year == today.year) date.format(dayFormat) else date.format(dayYearFormat)
    }
}
