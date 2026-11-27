# SMSShield Architecture & Implementation Plan

## 1. Project Overview
SMSShield is an offline, zero-permission Android notification filter tailored for Iranian SMS traffic. It is structured as a multi-module Gradle Kotlin DSL project targeting JDK 17, Kotlin 2.0.21 (Compose Compiler Gradle plugin), AGP 8.7.2 (`compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`), Room via KSP, and Jetpack Compose Material 3 with Persian RTL layout.

## 2. Module Architecture

### `:core-engine` (Pure Kotlin/JVM)
- Package: `com.smsshield.engine`
- Zero `android.*` imports; fast JVM unit testing via JUnit 4.
- Models (`com.smsshield.engine.model`): `SmsInput`, `SmsVerdict` (`SAFE`, `SUSPICIOUS`, `SPAM`), `ClassificationResult`.
- `PersianNormalizer`: Unifies Arabic/Persian letters (`ي/ى/ئ -> ی`, `ك -> ک`, `ة -> ه`, `أ/إ -> ا`), converts Persian (`۰-۹`) and Arabic-Indic (`٠-٩`) digits to ASCII (`0-9`), replaces ZWNJ (`\u200C`) with a single space, strips invisible chars (`\u200B`, `\u200E`, `\u200F`, `\uFEFF`) and Arabic diacritics (`\u064B-\u0652`), lowercases Latin text, and collapses whitespace.
- 4-Layer Pipeline (`SpamRules`, `ScoringEngine`, `SpamClassifier`):
  - Layer 2 Safe Exceptions (`SAFE`, score `0`): truncated text guard, manual user whitelist, OTP + 4–8 digit code, and bank transaction + currency (`ریال`/`تومان`) without auto-whitelisting sender names.
  - Layer 3 & 4 Weighted Scoring: `لغو\s*[=:]?\s*11` (+45), bulk gateway sender (+15), URLs/short links (+20), phone numbers (+15), root-grouped promotional keywords capped at +55, custom user keywords, and negative service weights (-25, floored at 0).
  - Configurable `spamThreshold` (default 75): `>= spamThreshold` -> `SPAM`, `>= spamThreshold - 25` -> `SUSPICIOUS`, else `SAFE`.

### `:app` (Android Application)
- Strict Manifest: ONLY `BIND_NOTIFICATION_LISTENER_SERVICE` on `SmsNotificationListener`. Zero `INTERNET`, `READ_SMS`, `RECEIVE_SMS`, or `READ_CONTACTS`.
- Notification Layer (`com.smsshield.app.notification`): `NotificationParser` (skips `FLAG_GROUP_SUMMARY`, extracts `MessagingStyle` -> `EXTRA_BIG_TEXT` -> `EXTRA_TEXT` with ellipsis truncation detection), `DeduplicationHelper` (SHA-256 5-minute window), `SmsNotificationListener` (coroutine IO, Learning Mode vs Active Mode).
- Persistence (`com.smsshield.app.data.database`): Room DB (`AppDatabase`, `QuarantineEntity`, `WhitelistEntity`, `CustomRuleEntity`, DAOs, `ShieldRepository`) + `ShieldPreferences`.
- Minimal 3-Screen Persian RTL Compose UI (`com.smsshield.app.ui`): Dashboard (`داشبورد`), Quarantine (`قرنطینه`), and Settings (`تنظیمات`).

## 3. CI/CD Pipeline
- GitHub Actions workflow `.github/workflows/android-build.yml` runs on `push`/`pull_request` to `main` and `workflow_dispatch`, using JDK 17 (`temurin`) and `gradle/actions/setup-gradle@v4` (`gradle-version: '8.9'`) to run `gradle :core-engine:test`, build `gradle :app:assembleDebug`, and upload `SMSShield-Debug-APK`.
