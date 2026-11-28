# AGENTS.md — SMSShield Coding & Architecture Guide

This guide defines the core architectural invariants, privacy rules, database migration requirements, and build/CI conventions for **SMSShield** (سپر پیامک) — currently **`v0.5.0`**.

**Official Persian App Slogan:**
«بیا کمک کنیم تا از شر پیامکهای مزاحم خلاص شویم»

---

## 1. Module Structure

SMSShield is a two-module Gradle project (`settings.gradle.kts`):

- **`:core-engine`** — Pure Kotlin/JVM module (`org.jetbrains.kotlin.jvm`, `kotlinx-serialization-json`). Contains all SMS classification, Offer vs. Nuisance categorization (`SpamCategory`), promo/discount code extraction (`DiscountCodeExtractor`), sender-pattern learning (`PersianTokenizer`), mixed-sender overblocking guardrails (`isLikelyMixedSender`), and interactive Tap-to-Teach token inspection (`inspectMessageTokens`). Must remain 100% free of `android.*` imports so unit tests run fast on the JVM.
- **`:app`** — Android application module (`com.smsshield.app`, `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`, `versionCode = 5`, `versionName = "0.5.0"`) built with **Jetpack Compose**, **Material 3**, and full **RTL** (`android:supportsRtl="true"`) Persian-first UX. Handles `NotificationListenerService` with privacy-safe live diagnostics (`ListenerDiagnostics`), Room persistence, Live Message Simulator («آزمایش پیامک»), reusable `TapToTeachDialog`, Share Sheet intake, and Storage Access Framework (SAF) backup/restore.

---

## 2. Strict Zero-Permission Privacy Invariant

SMSShield operates strictly offline with a **zero-permission policy** (`app/src/main/AndroidManifest.xml`):

- **NEVER** add `<uses-permission>` entries (`NO INTERNET`, `READ_SMS`, `RECEIVE_SMS`, `READ_CONTACTS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `QUERY_ALL_PACKAGES`, or external storage permissions).
- **Battery Optimization Diagnostics:** Use `PowerManager.isIgnoringBatteryOptimizations(context.packageName)` (requires zero permissions) and open `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (with fallback to `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) — **never** `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- **Data Intake & Export:** Use only Android **Share Sheet** (`ACTION_SEND` / `ACTION_SEND_MULTIPLE` for `text/plain`), **Storage Access Framework (SAF)** for local backup/restore, and the system-bound `SmsNotificationListener` (`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`). Only inspect notifications from recognized `NotificationParser.SMS_PACKAGES`.
- Keep `android:allowBackup="false"` on `<application>` so local rules and quarantined messages never leak to cloud backups.

---

## 3. Room Database Migration Chain

`AppDatabase` (`app/src/main/kotlin/com/smsshield/app/data/database/AppDatabase.kt`) is currently at **`version = 5`**:

- Always preserve the full forward migration chain:
  - `MIGRATION_1_2` (adds `contentHash` column + index on `quarantine_messages`)
  - `MIGRATION_2_3` (creates `blocked_senders` table + unique index; adds `hitCount` to `custom_rules`)
  - `MIGRATION_3_4` (adds `feedbackState` to `quarantine_messages`; creates `training_samples` and `learned_features` tables)
  - `MIGRATION_4_5` (adds `category` with default `'SPAM_NUISANCE'` and nullable `discountCode` to `quarantine_messages`)
- **NEVER** use `fallbackToDestructiveMigration()` or destructive schema resets.
- Any schema change must increment `version`, append a new `MIGRATION_N_N+1` registered in `.addMigrations(...)`, and add a corresponding Robolectric/Room migration test in `RoomMigrationAndRepositoryTest.kt`.

---

## 4. Build & CI Rules

- **Toolchain:** JDK 17 (`temurin` in CI; `JavaVersion.VERSION_17` / `jvmToolchain(17)`) and **Gradle 8.9**.
- **Gradle Invocation:** Run `gradle` directly (there is no Gradle wrapper checked in):
  - `gradle :core-engine:test`
  - `gradle :app:testDebugUnitTest`
  - `gradle :app:assembleDebug`
- **Annotation Processing:** Use **KSP** (`com.google.devtools.ksp`, e.g., `ksp("androidx.room:room-compiler:...")`) — **never** `kapt`.
- **CI & Release Workflows:**
  - `.github/workflows/android-build.yml` (`Android CI & APK Build`) runs unit tests for both modules and builds the debug APK on pushes and pull requests to `main`.
  - `.github/workflows/release.yml` (`Release APK`) builds signed release APKs on `v*` tags using the persistent signing secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) so users can upgrade APKs in-place without losing local Room data. Stable tags fail fast if these secrets are missing; never hardcode or commit signing credentials.
