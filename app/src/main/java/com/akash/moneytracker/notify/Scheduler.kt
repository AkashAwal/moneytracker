package com.akash.moneytracker.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.akash.moneytracker.MoneyTrackerApp
import com.akash.moneytracker.computeHome
import com.akash.moneytracker.data.Settings
import com.akash.moneytracker.domain.Money
import com.akash.moneytracker.domain.WeeklySummary
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Daily and weekly notifications run as chains of one-off jobs: each run posts its notification and then
 * schedules the next one. WorkManager keeps them across reboots, so nothing needs to stay running.
 */
object Scheduler {
    private const val DAILY = "daily_reminder"
    private const val WEEKLY = "weekly_summary"
    private const val WEEKLY_HOUR = 20

    /** Brings the scheduled jobs in line with the current settings; safe to call whenever they change. */
    fun sync(context: Context, s: Settings) {
        val work = WorkManager.getInstance(context)
        if (s.reminderEnabled) scheduleDaily(context, s.reminderMinute, ExistingWorkPolicy.REPLACE)
        else work.cancelUniqueWork(DAILY)
        if (s.weeklySummaryEnabled) scheduleWeekly(context, ExistingWorkPolicy.REPLACE)
        else work.cancelUniqueWork(WEEKLY)
    }

    fun scheduleDaily(context: Context, minuteOfDay: Int, policy: ExistingWorkPolicy) {
        val delay = delayUntilNext(LocalDateTime.now(), minuteOfDay)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(DAILY, policy, request)
    }

    fun scheduleWeekly(context: Context, policy: ExistingWorkPolicy) {
        val delay = delayUntilNextSunday(LocalDateTime.now(), WEEKLY_HOUR)
        val request = OneTimeWorkRequestBuilder<WeeklySummaryWorker>().setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(WEEKLY, policy, request)
    }

    /** Time until the next occurrence of [minuteOfDay]; a moment that's already (nearly) passed means tomorrow. */
    fun delayUntilNext(now: LocalDateTime, minuteOfDay: Int): Duration {
        var target = now.toLocalDate().atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
        if (!target.isAfter(now.plusMinutes(1))) target = target.plusDays(1)
        return Duration.between(now, target)
    }

    fun delayUntilNextSunday(now: LocalDateTime, hour: Int): Duration {
        var target = now.toLocalDate().atTime(hour, 0)
        while (target.dayOfWeek != DayOfWeek.SUNDAY || !target.isAfter(now.plusMinutes(1))) target = target.plusDays(1)
        return Duration.between(now, target)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MoneyTrackerApp
        val settings = app.settingsStore.settings.first()
        if (!settings.reminderEnabled) return Result.success()

        val today = LocalDate.now()
        val all = app.repository.allOnce()
        // A "spent nothing today" check-in counts as logging, so the reminder stays quiet after one.
        val loggedToday = all.any { it.tx.epochDay == today.toEpochDay() && it.tx.recurringId == null } ||
            today.toEpochDay() in settings.checkInDays
        if (!loggedToday) {
            val home = computeHome(settings, all, today, app.repository.planningOnce())
            val text = when {
                home.streaks.loggingStreak > 1 ->
                    "Log something, or mark a no-spend day, to keep your ${home.streaks.loggingStreak}-day streak going 🔥"
                home.budget != null && !home.budget.overToday -> "You can still spend ${Money.format(home.budget.leftToday)} today. Anything to log?"
                else -> "Anything to log today?"
            }
            Notifications.postReminder(applicationContext, text)
        }
        Scheduler.scheduleDaily(applicationContext, settings.reminderMinute, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}

class WeeklySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MoneyTrackerApp
        if (!app.settingsStore.settings.first().weeklySummaryEnabled) return Result.success()

        val summary = WeeklySummary.compute(app.repository.allOnce(), LocalDate.now())
        if (!summary.isEmpty) Notifications.postSummary(applicationContext, summary.message())
        Scheduler.scheduleWeekly(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}
