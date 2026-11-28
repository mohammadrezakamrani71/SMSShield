package com.smsshield.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PersianDateFormatterTest {

    @Test
    fun `converts known Gregorian dates to exact Jalali dates`() {
        // Nowruz 1403 (2024 is leap year -> March 20, 2024 = 1403/01/01)
        assertEquals(JalaliDate(1403, 1, 1), PersianDateFormatter.gregorianToJalali(2024, 3, 20))
        // Nowruz 1404 (March 21, 2025 = 1404/01/01)
        assertEquals(JalaliDate(1404, 1, 1), PersianDateFormatter.gregorianToJalali(2025, 3, 21))
        // November 27, 2026 = 1405/09/06
        assertEquals(JalaliDate(1405, 9, 6), PersianDateFormatter.gregorianToJalali(2026, 11, 27))
    }

    @Test
    fun `formats same-day and historical timestamps with Persian digits`() {
        val tz = TimeZone.getTimeZone("Asia/Tehran")
        val targetCal = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 27, 14, 25, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val sameDayNow = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 27, 18, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val laterDayNow = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 29, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val todayStr = PersianDateFormatter.formatTimestamp(
            timestampMillis = targetCal.timeInMillis,
            nowMillis = sameDayNow.timeInMillis,
            timeZone = tz
        )
        assertEquals("امروز • ۱۴:۲۵", todayStr)

        val fullStr = PersianDateFormatter.formatTimestamp(
            timestampMillis = targetCal.timeInMillis,
            nowMillis = laterDayNow.timeInMillis,
            timeZone = tz
        )
        assertEquals("۱۴۰۵/۰۹/۰۶ • ۱۴:۲۵", fullStr)
        assertTrue(fullStr.none { it in '0'..'9' })
    }
}
