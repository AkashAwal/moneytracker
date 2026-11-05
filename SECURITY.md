# Security

Money Tracker holds financial information, so it is built to share as little as possible, protect what it does keep,
and treat everything that comes *into* the app (backups, texts, intents) as untrusted. This page explains what is
protected, how, and where the limits are.

> **The short version:** the app has no internet access and no accounts, so there is no server to breach. Your data
> lives in the app's private storage, behind an optional fingerprint/PIN lock. Backups are encrypted with a password you
> choose. Anything the app reads from outside is validated before it is used.

---

## Threat model

| # | Threat | What protects you | Where to look |
|---|--------|-------------------|---------------|
| 1 | **Someone picks up your unlocked phone** | App lock using the phone's own fingerprint/face (strong biometrics only on Android 11+) or screen lock. Re-locks 30 s after you leave. Balances are hidden in the recent-apps view and from screenshots while the lock is on. The home-screen widget and lock-screen notifications show no amounts. | `lock/AppLock.kt`, `MainActivity.kt`, `widget/QuickAddWidget.kt`, `notify/Notifications.kt` |
| 2 | **Another app on the same phone** | Android's per-app sandbox. No component hands out data: the only exported entry points are the launcher activity, the widget, a "share text" target, and one service that Android restricts to the system (`BIND_QUICK_SETTINGS_TILE`). The file provider is not exported and only covers one cache folder. | `AndroidManifest.xml` |
| 3 | **Pulling the database over USB (`adb backup`) or through cloud/device-transfer backup** | `allowBackup="false"` plus empty data-extraction rules. The release build is not debuggable, so `adb shell run-as` is refused. | `AndroidManifest.xml`, `res/xml/data_extraction_rules.xml` |
| 4 | **A backup file ends up somewhere it shouldn't** (Drive, WhatsApp, a lost USB stick) | Backups are encrypted by default: AES-256-GCM, key from PBKDF2-HMAC-SHA256 with 600,000 iterations and a random salt. GCM authenticates the file, so a wrong password and a modified file are both rejected. | `data/BackupCrypto.kt` |
| 5 | **A crafted or tampered backup file** | A backup is treated as hostile input: 20 MB cap, hard limits on counts, amounts, dates, text length, tags; duplicate IDs and inconsistent references are refused; control characters and text-direction overrides are stripped; the restore runs in one database transaction so a failure changes nothing. | `data/Backup.kt` (`BackupLimits`) |
| 6 | **A forged bank text** | The app has no SMS permission and reads nothing on its own; you share or paste a text yourself. It never creates a transaction directly: it becomes a *suggestion* you must confirm. Text is sanitised and size-limited, absurd amounts are ignored, the pending list is capped at 50, and the text is never stored (only a hash). | `sms/SmsParser.kt`, `MainViewModel.importText` |
| 7 | **Spreadsheet formula injection** (a note like `=HYPERLINK(...)` surviving into an exported CSV) | Text cells starting with `=`, `+`, `-` or `@` are neutralised on export. | `Backup.toCsv` |
| 8 | **A tampered APK passed around the group** | Releases are signed with a private key kept off the repo. A re-signed copy can't update an existing install and, installed fresh, gets its own empty storage, so it can't read anyone's data. See [Verify your download](#verify-your-download). | `README.md` |
| 9 | **Look-alike overlays / tap-jacking** (another app drawing over the "Replace" button) | Touches are dropped while the window is obscured, and other apps' overlays are hidden on Android 12+. | `MainActivity.kt` |
| 10 | **Task hijacking** | The activity uses an empty task affinity. | `AndroidManifest.xml` |
| 11 | **Leftover copies of exports** | Files made for sharing (backups, CSV, PDF, chart images) are deleted from the app's cache after 10 minutes. | `export/Exporter.kt` |
| 12 | **Compromised build dependencies** | Every library the build uses is pinned by SHA-256 checksum; a swapped or tampered download fails the build. The dependency list is short and nothing is fetched at runtime. | `gradle/verification-metadata.xml` |

All of the above except the manifest settings is covered by automated tests (`app/src/test`, 81 tests), including
tampered ciphertext, weakened encryption parameters, hostile backups, formula injection and spoofed texts.

## What the app does *not* protect against

Being honest about limits matters more than a long feature list.

- **A rooted or otherwise compromised phone.** If malware has root, or someone can inject code into the running app,
  no app-level measure holds: the lock can be bypassed and the data read from memory or storage. Keep the phone
  updated and don't root it.
- **Accessibility-service malware and screen recorders.** These can read what's on screen while the app is open.
- **Someone watching over your shoulder** while the app is unlocked.
- **A forgotten backup password.** There's no recovery by design: that is what makes the encryption meaningful.
- **A weak backup password.** The app requires 8+ characters; the strength beyond that is up to you. A passphrase of
  several words is best.
- **Data at rest is not separately encrypted.** The database sits in the app's private storage, which Android already
  encrypts with the phone's lock-screen credentials (file-based encryption, mandatory since Android 10). A second layer
  inside the app would have to keep its key on the same phone, so it would add little against the threats above while
  adding size and breaking background features like the widget.
- **No screen lock set on the phone.** The app lock needs one; the switch explains this if it's missing.

## Verify your download

The app is distributed as an APK outside the Play Store, so check that what you installed is what was built here.

**Signing certificate fingerprint (SHA-256):**

```
94:AC:15:15:AB:21:D2:26:6E:6D:C4:27:40:EF:DD:52:D5:C4:BE:B7:0E:46:C3:7A:D2:C8:FB:B9:CD:6B:A5:3D
```

On a computer with the Android SDK:

```bash
apksigner verify --print-certs MoneyTracker-1.3.0.apk
# "Signer #1 certificate SHA-256 digest" must match the fingerprint above.
```

Compare the file's checksum with the one published in `dist/SHA256SUMS.txt` / the release notes:

```bash
sha256sum MoneyTracker-1.3.0.apk        # Windows: certutil -hashfile MoneyTracker-1.3.0.apk SHA256
```

If the fingerprint differs, **don't install it**. Android also refuses to install an update signed with a different
key over an existing install, which protects people who already have the app.

> **Keep the signing key safe.** `moneytracker-release.jks` and its properties file live outside the repository, in
> `~/.android/`. If it leaks, someone could publish updates that Android accepts; if it's lost, nobody can update in
> place. Keep an offline copy.

## Reporting a vulnerability

Please **don't open a public issue** for a security problem. Use GitHub's *Report a vulnerability* button on the
repository's **Security** tab (private advisory), and include what you found, how to reproduce it, and the version.

## Maintainer checklist before each release

- [ ] `./gradlew :app:testDebugUnitTest` passes
- [ ] `./gradlew :app:lintRelease` reports no new errors
- [ ] The built APK's manifest still shows `allowBackup=false`, not debuggable, no new exported components
  (`aapt2 dump xmltree --file AndroidManifest.xml app-release.apk`)
- [ ] `gradle/verification-metadata.xml` is up to date and the build passes with it enabled
- [ ] Signed with the release key; fingerprint above unchanged; new SHA-256 published
