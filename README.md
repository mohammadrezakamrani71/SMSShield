# SMSShield | سپر پیامک

**Offline, lightweight, zero-permission Iranian SMS spam notification filter for Android.**  
**فیلتر آفلاین، سبک و بدون دسترسی پیامک برای حذف اعلان‌های پیامک‌های تبلیغاتی ایرانی در اندروید.**

---

## English Overview

**SMSShield** protects Android users from Iranian promotional/spam SMS notifications **without requesting `READ_SMS`, `RECEIVE_SMS`, `READ_CONTACTS`, external storage, or `INTERNET` permissions**. Instead, it uses Android's `NotificationListenerService` to inspect incoming SMS notifications locally on-device, classify them with a pure Kotlin Persian/Iranian heuristic scoring engine, and safely quarantine or dismiss spam notifications.

### Key Highlights
- **Zero Sensitive Permissions:** Only declares `BIND_NOTIFICATION_LISTENER_SERVICE`. 100% offline (no `INTERNET` or storage permissions; uses Android Storage Access Framework for `.json` files).
- **Safe-First Architecture:**
  - **Learning Mode (default on first launch):** Logs `SPAM` and `SUSPICIOUS` messages to the local Quarantine without dismissing any notification.
  - **Active Mode:** Dismisses confirmed `SPAM` notifications with thread-safe `sbn.key` race protection while preserving OTPs, bank transactions, whitelisted senders, truncated previews, and legitimate service messages.
- **Sender Blocking (`EXACT` & `PREFIX`):** Block Iranian gateways/senders (automatically normalizing `+98`, `0098`, `98`, and Persian/Arabic digits) directly from Quarantine or Settings, with live per-rule hit counters.
- **Local Backup & Restore:** Export and restore all custom keywords, blocked senders, whitelist entries, and sensitivity settings to/from a local `.json` file via Android's Storage Access Framework (`CreateDocument` / `OpenDocument`).
- **Community Keyword Packs & Gamified Shield Studio:** Track your total spam caught, earn Shield Contributor ranks, export `.smsshield.json` or `SMSSHIELD-PACK:v1:...` share codes, import curated offline starter packs or community packs with a mandatory checkbox preview, and share packs on GitHub Discussions.

### Persistent Signing & Seamless APK Upgrades
- Configure `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` in **Repository Settings → Secrets and variables → Actions**.
- Once configured, **every** tagged release (`v*`, including `-alpha` and `-beta` pre-releases) is built with `assembleRelease` and signed with your persistent keystore, allowing seamless in-place APK upgrades without signature conflicts or data loss.

---

## معرفی فارسی

**سپر پیامک (SMSShield)** یک فیلتر کاملاً آفلاین و سبک برای مدیریت و حذف اعلان‌های پیامک‌های تبلیغاتی در ایران است که **هیچ‌گونه دسترسی `INTERNET`، `READ_SMS`، `RECEIVE_SMS`، `READ_CONTACTS` یا حافظه خارجی درخواست نمی‌کند**.

### ویژگی‌های جدید نسخه v0.3.0
- **بلاک سرشماره (دقیق و پیشوندی):** امکان مسدودسازی سرشماره‌ها به‌صورت تطابق دقیق (`550021`) یا پیشوندی (`550021*`) از تنظیمات یا با یک لمس در صفحه قرنطینه، همراه با یکسان‌سازی خودکار `+98` / `0098` / `98` و اعداد فارسی.
- **پشتیبان‌گیری و بازیابی محلی:** ذخیره و بازیابی کلیدواژه‌ها، سرشماره‌های مسدودشده، لیست مجاز و آستانه حساسیت در فایل `.json` بدون نیاز به دسترسی حافظه یا اینترنت.
- **پک‌های کلیدواژه و استودیو پک من:** مشاهده آمار شکار اسپم و رتبه کاربری، ساخت و اشتراک‌گذاری پک با کد `SMSSHIELD-PACK:v1:...` یا فایل `.smsshield.json`، وارد کردن پک‌های آماده آفلاین یا پک‌های کاربران با پیش‌نمایش تیک‌دار (Checkbox) و تشخیص خودکار موارد تکراری.
- **به‌روزرسانی بدون حذف برنامه:** با تنظیم کلیدهای امضای پایدار (`KEYSTORE_BASE64` و رمزها) در GitHub Secrets، تمام نسخه‌های تگ‌شده (حتی پیش‌انتشارهای alpha/beta) با یک کلید ثابت امضا می‌شوند تا آپدیت مستقیم روی نسخه قبلی بدون از دست رفتن داده‌ها انجام شود.

## License
MIT License — see [LICENSE](LICENSE).
