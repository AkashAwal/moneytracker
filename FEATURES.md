# Money Tracker: Feature List

Personal-use Android app for a small group of people. Sideloaded APK (no Play Store). All data is stored on the device only, with no server and no cloud sync.

## Stack
Kotlin, Jetpack Compose + Material 3, Room, DataStore, MVVM (ViewModel + StateFlow), hand-drawn Canvas charts, built-in `PdfDocument`, org.json for backups.

## Core (requested)
- [x] Add, edit and delete transactions (expense and income), each with date and time
- [x] Custom categories (name, icon, color, type), created from the add sheet via "+ New"
- [x] Charts and graphs: category donut, day-by-day bars, month-over-month comparison
- [x] Analytics: spending by week of month and weekday, priciest and quietest called out; calendar heatmap
- [x] Analytics: top items by spend
- [x] Monthly savings goal prompt
- [x] Persistent daily spend limit card: `(income - savings_goal - fixed_expenses - spent_so_far) / days_remaining`, recalculated daily, red when exceeded
- [x] The daily limit is optional: a cross on the card removes it (and the forecast, and its Settings entries); Home shows a "Track a daily spending limit" prompt that brings it back. It is the only way back, and the goal and income are kept
- [x] PDF export (monthly report: summary, charts, patterns, top items, all transactions)
- [x] PNG export of each chart card (via the share sheet)

## High value, low effort
- [x] Quick-add: home-screen widget, quick-settings tile and launcher shortcut
- [x] Recurring transactions (rent, subscriptions, salary); upcoming bills are set aside before the daily limit
- [x] Per-category monthly budgets with a warning at 80% and when over
- [x] Income tracking
- [x] Search and filter (text, type, category, date range, amount)
- [x] Biometric or PIN app lock (uses the phone's own screen lock; locks after 1 minute away)
- [x] Dark mode, with a System / Light / Dark switch in Settings → Appearance
- [ ] Currency setting (skipped: the app is INR only)
- [x] Undo on delete (the message clears itself after 5 seconds, or swipe it away)

## Nice to have
- [x] Streaks and no-spend day counter; a "Spent nothing today" button counts a quiet day as logged so it doesn't break the streak
- [x] Weekly summary notification (Sundays 8 PM)
- [x] Daily "log your expenses" reminder (time is configurable; skipped if you already logged today)
- [x] Notes on a transaction (receipt photo: not planned)
- [x] Tags in addition to categories (add sheet, filter, search and an Insights card)
- [x] CSV export
- [ ] Multiple accounts or wallets (not planned)
- [x] End-of-month forecast ("at this pace you'll end at X")

## Local-only / sideload specifics
- [x] JSON export and import of the full database (backup, restore, move to a new phone)
- [x] Signed release APK for sharing with the group; manual updates (dist/MoneyTracker-1.0.0.apk)
- [x] Bank-text suggestions: share a bank text to the app (or paste it) and confirm the suggestion. No SMS permission, because Play Protect blocks sideloaded apps that request it

## Out of scope
- Cloud sync and a server
- Play Store release

## Security hardening (v1.2.0)
- [x] Password-protected backups (AES-256-GCM, PBKDF2 600k iterations), tamper detection
- [x] Strict validation of restored backups, CSV formula-injection defence, bank-text import limits
- [x] Android backup/transfer disabled, tap-jacking and task-hijack protection, strong-biometric app lock
- [x] Lock-screen notifications and widget hide amounts; exported files auto-deleted
- [x] Dependency checksum verification; signed release with published certificate fingerprint
- [x] SECURITY.md with threat model and known limits; README with screenshots
