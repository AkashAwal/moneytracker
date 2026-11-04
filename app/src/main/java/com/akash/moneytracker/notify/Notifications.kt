package com.akash.moneytracker.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.akash.moneytracker.MainActivity
import com.akash.moneytracker.R

object Notifications {
    private const val CHANNEL_REMINDER = "reminder"
    private const val CHANNEL_SUMMARY = "summary"
    private const val CHANNEL_SMS = "sms"

    private const val ID_REMINDER = 1
    private const val ID_SUMMARY = 2
    private const val ID_SMS = 3

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(CHANNEL_REMINDER, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_SUMMARY, "Weekly summary", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_SMS, "Bank SMS suggestions", NotificationManager.IMPORTANCE_DEFAULT),
        )
        // Amounts must not show on a locked screen; the system shows the generic version there instead.
        channels.forEach { it.lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE }
        manager.createNotificationChannels(channels)
    }

    /** Android 13+ needs the user to allow notifications; earlier versions only need them not switched off. */
    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun postReminder(context: Context, text: String) =
        post(context, ID_REMINDER, CHANNEL_REMINDER, "Money Tracker", text, openAddSheet = true)

    fun postSummary(context: Context, text: String) =
        post(context, ID_SUMMARY, CHANNEL_SUMMARY, "Your week in money", text, openAddSheet = false)

    fun postSuggestion(context: Context, text: String) =
        post(context, ID_SMS, CHANNEL_SMS, "New payment spotted", text, openAddSheet = false)

    @SuppressLint("MissingPermission") // canPost() below checks the permission
    private fun post(context: Context, id: Int, channel: String, title: String, text: String, openAddSheet: Boolean) {
        if (!canPost(context)) return
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openAddSheet) action = MainActivity.ACTION_QUICK_ADD
        }
        val tap = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // What anyone glancing at a locked phone sees: no amounts, no merchants.
        val lockScreenVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Money Tracker")
            .setContentText("Unlock your phone to see this")
            .build()

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreenVersion)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
