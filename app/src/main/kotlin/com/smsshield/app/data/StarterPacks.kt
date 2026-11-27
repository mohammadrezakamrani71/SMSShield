package com.smsshield.app.data

import com.smsshield.engine.BlockedSenderItem
import com.smsshield.engine.KeywordRuleItem
import com.smsshield.engine.RulePack
import com.smsshield.engine.model.SenderMatchType

object StarterPacks {

    val CURATED_PACKS: List<RulePack> = listOf(
        RulePack(
            schemaVersion = 1,
            packName = "پک ضدتبلیغات جشنواره‌ای و بانکی",
            authorName = "تیم سپر پیامک",
            description = "شناسایی پیامک‌های تبلیغاتی جشنواره‌های بانکی، قرعه‌کشی امتیاز باشگاه مشتریان و اعتبار خرید.",
            category = "بانکی و جشنواره‌ای",
            caughtCount = 128,
            keywords = listOf(
                KeywordRuleItem("باشگاه مشتریان", 20),
                KeywordRuleItem("امتیاز قرعه کشی", 25),
                KeywordRuleItem("اعتبار هدیه", 25),
                KeywordRuleItem("جایزه نقدی", 25),
                KeywordRuleItem("شانس برنده شدن", 25),
                KeywordRuleItem("افتتاح حساب غیرحضوری", 20)
            ),
            blockedSenders = listOf(
                BlockedSenderItem("9810008500*", SenderMatchType.PREFIX)
            )
        ),
        RulePack(
            schemaVersion = 1,
            packName = "پک کلینیک‌ها، زیبایی و فروشگاه‌ها",
            authorName = "تیم سپر پیامک",
            description = "مسدودسازی تبلیغات کلینیک‌های زیبایی، لیزر، دندانپزشکی، بوتیک‌ها و فروشگاه‌های فصلی.",
            category = "کلینیک و خدمات",
            caughtCount = 94,
            keywords = listOf(
                KeywordRuleItem("لیزر موهای زائد", 30),
                KeywordRuleItem("کلینیک زیبایی", 25),
                KeywordRuleItem("کاشت مو", 25),
                KeywordRuleItem("ایمپلنت اقساطی", 25),
                KeywordRuleItem("حراج فصل", 25),
                KeywordRuleItem("کالکشن جدید", 20),
                KeywordRuleItem("تخفیف ویژه عید", 25)
            ),
            blockedSenders = emptyList()
        ),
        RulePack(
            schemaVersion = 1,
            packName = "پک املاک، وام و سرمایه‌گذاری",
            authorName = "تیم سپر پیامک",
            description = "فیلتر پیامک‌های پیش‌فروش مسکن، وام فوری با سند، سیگنال بورس و ارز دیجیتال.",
            category = "املاک، وام و سرمایه‌گذاری",
            caughtCount = 76,
            keywords = listOf(
                KeywordRuleItem("پیش فروش آپارتمان", 30),
                KeywordRuleItem("وام فوری", 30),
                KeywordRuleItem("بدون ضامن", 25),
                KeywordRuleItem("سود تضمینی", 30),
                KeywordRuleItem("سیگنال رایگان", 30),
                KeywordRuleItem("ارز دیجیتال", 25),
                KeywordRuleItem("خرید زمین", 25)
            ),
            blockedSenders = emptyList()
        )
    )
}
