package com.smsshield.engine

import com.smsshield.engine.learning.AdaptiveSpamLearner
import com.smsshield.engine.learning.SampleLabel
import com.smsshield.engine.learning.TrainingSampleInput
import com.smsshield.engine.model.SmsInput
import com.smsshield.engine.model.SmsVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartLearningEngineTest {

    private val classifier = SpamClassifier()

    @Test
    fun `tokenizer removes Persian stop-words and numeric tokens so OTP digits are never learned`() {
        val tokens = com.smsshield.engine.learning.PersianTokenizer.tokenize(
            "سلام مشتری گرامی کد ۴۸۲۹۱۰ برای شما با مبلغ ۵۰۰۰۰۰ ارسال شد کلینیک زیبایی"
        )

        assertFalse("سلام" in tokens)
        assertFalse("مشتری" in tokens)
        assertFalse("گرامی" in tokens)
        assertFalse("برای" in tokens)
        assertFalse("482910" in tokens)
        assertFalse("500000" in tokens)
        assertTrue("کلینیک" in tokens)
        assertTrue("زیبایی" in tokens)
    }

    @Test
    fun `cold-start spam-only learning weights repeated phrases and keeps single-occurrence noise neutral`() {
        val samples = listOf(
            TrainingSampleInput("30001111", "کلینیک زیبایی لیزر موهای زائد نوبت دهی سریع", SampleLabel.SPAM),
            TrainingSampleInput("30002222", "کلینیک زیبایی لیزر موهای زائد بهترین دستگاه", SampleLabel.SPAM),
            TrainingSampleInput("30003333", "کلینیک زیبایی با کادر مجرب کلمه_تکی_نویز", SampleLabel.SPAM)
        )

        val (model, report) = AdaptiveSpamLearner.train(samples)
        assertEquals(3, model.totalSpamSamples)
        assertEquals(0, model.totalSafeSamples)

        val repeatedWeight = model.features["کلینیک زیبایی"]?.weight ?: 0.0
        val singleNoiseWeight = model.features["نویز"]?.weight ?: 0.0
        assertTrue(repeatedWeight > 0.5)
        assertEquals(0.0, singleNoiseWeight, 0.0001)

        val eval = AdaptiveSpamLearner.evaluate(
            SmsInput("30009999", "مراجعه به کلینیک زیبایی لیزر موهای زائد"),
            model
        )
        assertTrue(eval.scoreDelta > 0)
        assertTrue(eval.scoreDelta <= AdaptiveSpamLearner.MAX_SCORE_DELTA)
        assertTrue(report.topSpamPhrases.any { it.phrase == "کلینیک زیبایی" })
    }

    @Test
    fun `two-class learning shifts positively for spam patterns and negatively for safe corrections`() {
        val samples = listOf(
            TrainingSampleInput("50001", "سیگنال ارز دیجیتال سود تضمینی ماهانه b2n.ir/x لغو 11", SampleLabel.SPAM),
            TrainingSampleInput("50002", "سیگنال ارز دیجیتال با سود تضمینی b2n.ir/y لغو 11", SampleLabel.SPAM),
            TrainingSampleInput("50003", "کانال سیگنال ارز دیجیتال سود تضمینی b2n.ir/z لغو 11", SampleLabel.SPAM),
            TrainingSampleInput("9121111111", "جلسه هماهنگی تیم فنی فردا ساعت ده در شرکت", SampleLabel.SAFE),
            TrainingSampleInput("9122222222", "جلسه هماهنگی پروژه تیم فنی برگزار میشود", SampleLabel.SAFE),
            TrainingSampleInput("9123333333", "یادآوری جلسه هماهنگی تیم فنی", SampleLabel.SAFE)
        )

        val (model, report) = AdaptiveSpamLearner.train(samples)

        val spamEval = AdaptiveSpamLearner.evaluate(
            SmsInput("50009", "دوره سیگنال ارز دیجیتال با سود تضمینی"),
            model
        )
        val safeEval = AdaptiveSpamLearner.evaluate(
            SmsInput("9124444444", "جلسه هماهنگی تیم فنی ساعت چهار"),
            model
        )

        assertTrue(spamEval.scoreDelta > 0)
        assertTrue(safeEval.scoreDelta < 0)
        assertTrue(spamEval.scoreDelta in AdaptiveSpamLearner.MIN_SCORE_DELTA..AdaptiveSpamLearner.MAX_SCORE_DELTA)
        assertTrue(safeEval.scoreDelta in AdaptiveSpamLearner.MIN_SCORE_DELTA..AdaptiveSpamLearner.MAX_SCORE_DELTA)
        assertTrue(report.topSafePhrases.isNotEmpty())
    }

    @Test
    fun `minimum sample gate returns zero delta on tiny sample sets`() {
        val tinySamples = listOf(
            TrainingSampleInput("50001", "کلینیک زیبایی لیزر", SampleLabel.SPAM),
            TrainingSampleInput("50002", "کلینیک زیبایی لیزر", SampleLabel.SPAM)
        )
        val (model, _) = AdaptiveSpamLearner.train(tinySamples)
        val eval = AdaptiveSpamLearner.evaluate(SmsInput("50003", "کلینیک زیبایی لیزر"), model)
        assertEquals(0, eval.scoreDelta)
    }

    @Test
    fun `safety guardrail prevents learnedModel from overriding OTP, Bank, Whitelist, or Truncated SMS`() {
        val samples = List(6) { idx ->
            TrainingSampleInput(
                sender = "3000$idx",
                body = "کلینیک زیبایی لیزر موهای زائد بوتاکس b2n.ir/a$idx لغو 11",
                label = SampleLabel.SPAM
            )
        }
        val (model, _) = AdaptiveSpamLearner.train(samples)

        // 1. OTP stays SAFE
        val otpResult = classifier.classify(
            input = SmsInput("30001234", "کد ورود کلینیک زیبایی: ۴۸۲۹۱۰"),
            learnedModel = model
        )
        assertEquals(SmsVerdict.SAFE, otpResult.verdict)
        assertEquals(0, otpResult.score)

        // 2. Bank transaction stays SAFE
        val bankResult = classifier.classify(
            input = SmsInput("30009999", "برداشت ۵۰۰,۰۰۰ ریال از حساب کلینیک زیبایی مانده ۱۲,۰۰۰,۰۰۰ ریال"),
            learnedModel = model
        )
        assertEquals(SmsVerdict.SAFE, bankResult.verdict)
        assertEquals(0, bankResult.score)

        // 3. Whitelisted sender stays SAFE
        val whitelistResult = classifier.classify(
            input = SmsInput("30005555", "کلینیک زیبایی لیزر موهای زائد بوتاکس لغو 11"),
            whitelistedSenders = setOf("30005555"),
            learnedModel = model
        )
        assertEquals(SmsVerdict.SAFE, whitelistResult.verdict)
        assertEquals(0, whitelistResult.score)

        // 4. Truncated message stays SAFE
        val truncatedResult = classifier.classify(
            input = SmsInput("30006666", "کلینیک زیبایی لیزر موهای زائد...", isPossiblyTruncated = true),
            learnedModel = model
        )
        assertEquals(SmsVerdict.SAFE, truncatedResult.verdict)
        assertEquals(0, truncatedResult.score)
    }

    @Test
    fun `generates behavioral insights and excludes existing keywords from suggestions`() {
        val samples = listOf(
            TrainingSampleInput("30001111", "کلینیک زیبایی لیزر موهای زائد b2n.ir/1 لغو 11", SampleLabel.SPAM),
            TrainingSampleInput("30002222", "کلینیک زیبایی لیزر موهای زائد b2n.ir/2 لغو 11", SampleLabel.SPAM),
            TrainingSampleInput("30003333", "کلینیک زیبایی لیزر موهای زائد b2n.ir/3", SampleLabel.SPAM),
            TrainingSampleInput("30004444", "کلینیک زیبایی کاشت ابرو", SampleLabel.SPAM)
        )

        val (_, report) = AdaptiveSpamLearner.train(
            samples = samples,
            existingCustomKeywords = setOf("کلینیک زیبایی")
        )

        assertTrue(report.behavioralInsights.any { it.contains("لینک") && it.contains("۷۵٪") })
        assertTrue(report.behavioralInsights.any { it.contains("لغو ۱۱") && it.contains("۵۰٪") })
        assertFalse("کلینیک زیبایی" in report.suggestedKeywords)
        assertTrue(report.suggestedKeywords.isNotEmpty())
    }
}
