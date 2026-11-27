# SMSShield | سپر پیامک

**Offline, lightweight, zero-permission Iranian SMS spam notification filter for Android.**  
**فیلتر آفلاین، سبک و بدون دسترسی پیامک برای حذف اعلان‌های پیامک‌های تبلیغاتی ایرانی در اندروید.**

---

## English Overview

**SMSShield** protects Android users from Iranian promotional/spam SMS notifications **without requesting `READ_SMS`, `RECEIVE_SMS`, `READ_CONTACTS`, or `INTERNET` permissions**. Instead, it uses Android's `NotificationListenerService` to inspect incoming SMS notifications locally on-device, classify them with a pure Kotlin Persian/Iranian heuristic scoring engine, and safely quarantine or dismiss spam notifications.

### Key Highlights
- **Zero Sensitive Permissions:** Only declares `BIND_NOTIFICATION_LISTENER_SERVICE`. Completely offline (no `INTERNET` permission).
- **Safe-First Architecture:**
  - **Learning Mode (default on first launch):** Logs `SPAM` and `SUSPICIOUS` messages to the local Quarantine without dismissing any notification.
  - **Active Mode:** Dismisses confirmed `SPAM` notifications while preserving OTPs, bank transactions, whitelisted senders, truncated previews, and legitimate service messages.
- **Iranian SMS Scoring Engine (`:core-engine`):**
  - Full Persian/Arabic character, digit, ZWNJ, and diacritic normalization.
  - Hard safe exceptions for OTP/verification codes, bank transactions (`ریال`/`تومان`), manual whitelist, and truncated previews.
  - Weighted scoring for `لغو 11`, bulk gateway senders (`1000`/`2000`/`3000`/`5000`/`9000`), URLs/short links, phone numbers, capped promotional keywords (root-matched to prevent double-counting), custom user keywords, and negative service weights.
- **Minimal Persian RTL UI:** Built with Jetpack Compose (Material 3) across 3 screens: Dashboard (`داشبورد`), Quarantine (`قرنطینه`), and Settings (`تنظیمات`).

### Build & Download APK
- Every push to `main` runs unit tests (`:core-engine:test`) and builds a Debug APK via GitHub Actions (**Android CI & APK Build**).
- Download the latest APK from the **Actions** tab → latest green **Android CI & APK Build** run → **Artifacts** → `SMSShield-Debug-APK`.

---

## معرفی فارسی

**سپر پیامک (SMSShield)** یک فیلتر کاملاً آفلاین و سبک برای مدیریت و حذف اعلان‌های پیامک‌های تبلیغاتی در ایران است که **هیچ‌گونه دسترسی `INTERNET`، `READ_SMS`، `RECEIVE_SMS` یا `READ_CONTACTS` درخواست نمی‌کند**. این برنامه صرفاً از طریق سرویس `NotificationListenerService` اعلان‌های پیامک را به‌صورت محلی روی گوشی تحلیل کرده و پیامک‌های تبلیغاتی را شناسایی می‌کند.

### ویژگی‌های کلیدی
- **حریم خصوصی حداکثری:** بدون دسترسی اینترنت و بدون دسترسی مستقیم به صندوق پیامک‌ها یا مخاطبین.
- **حالت یادگیری (Learning Mode):** در اولین اجرا فعال است و بدون حذف اعلان‌ها، پیامک‌های تبلیغاتی و مشکوک را در بخش قرنطینه ثبت می‌کند تا از دقت فیلتر مطمئن شوید.
- **سپر فعال (Active Mode):** اعلان پیامک‌های تبلیغاتی قطعی را حذف می‌کند، در حالی که رمزهای پویا (OTP)، تراکنش‌های بانکی، فرستنده‌های لیست مجاز، متن‌های ناقص و پیامک‌های خدماتی (سفارش، مرسوله، تحویل، قبض و...) همواره ایمن می‌مانند.
- **رابط کاربری فارسی و راست‌به‌چپ (RTL):** طراحی‌شده با Jetpack Compose و Material 3 شامل سه صفحه: داشبورد، قرنطینه و تنظیمات.

## License
MIT License — see [LICENSE](LICENSE).
