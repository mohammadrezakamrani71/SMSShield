# AGENTS.md — SMSShield Coding & Architecture Guide

This guide defines the core architectural invariants, privacy rules, database migration requirements, and build/CI conventions for **SMSShield** (سپر پیامک).

**Official Persian App Slogan:**
«بیا کمک کنیم تا از شر پیامکهای مزاحم خلاص شویم»

---

## 1. Module Structure

SMSShield is a two-module Gradle project (`settings.gradle.kts`):

- **`:core-engine`** — Pure Kotlin/JVM module (`org.jetbrains.kotlin.jvm`, `kotlinx-serialization-json`). Contains all SMS classification, keyword/pattern matching, smart-learning logic, and pure business rules. Must remain 100% free of Android framework dependencies so unit tests run fast on the JVM.
- **`:app`** — Android application module (`com.smsshield.app`, `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`) built with **Jetpack Compose**, **Material 3**, and full **RTL** (`android:supportsRtl="true"`) Persian-first UX. Handles `NotificationListenerService`, Room persistence, Share Sheet intake, and Storage Access Framework (SAF) backup/restore.

---

## 2. Strict Zero-Permission Privacy Invariant

SMSShield operates strictly offline with a **zero-permission policy** (`app/src/main/AndroidManifest.xml`):

- **NEVER** add `<uses-permission>` for `INTERNET`, `READ_SMS`, `RECEIVE_SMS`, `READ_CONTACTS`, or any external storage permission (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, etc.).
- **Data Intake & Export:** Use only Android **Share Sheet** (`ACTION_SEND` / `ACTION_SEND_MULTIPLE` for `text/plain`), **Storage Access Framework (SAF)** for local backup/restore, and the system-bound `SmsNotificationListener` (`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`).
- Keep `android:allowBackup="false"` on `<application>` so local rules and quarantined messages never leak to cloud backups.

---

## 3. Room Database Migration Chain

`AppDatabase` (`app/src/main/kotlin/com/smsshield/app/data/database/AppDatabase.kt`) is currently at **`version = 4`**:

- Always preserve the full forward migration chain:
  - `MIGRATION_1_2` (adds `contentHash` column + index on `quarantine_messages`)
  - `MIGRATION_2_3` (creates `blocked_senders` table + unique index; adds `hitCount` to `custom_rules`)
  - `MIGRATION_3_4` (adds `feedbackState` to `quarantine_messages`; creates `training_samples` and `learned_features` tables)
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
