# Room and Compose ship their own consumer rules.

# Release hardening: leave no debug logging behind, and flatten the package layout so the shipped
# class names say as little as possible about how the app is organised.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
-repackageclasses ''
-allowaccessmodification

# WorkManager stores worker class names; keep them stable so scheduled reminders survive an app update.
-keepnames class com.akash.moneytracker.notify.ReminderWorker
-keepnames class com.akash.moneytracker.notify.WeeklySummaryWorker
