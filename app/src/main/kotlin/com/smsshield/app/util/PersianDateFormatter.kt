package com.smsshield.app.util

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class JalaliDate(
    val year: Int,
    val month: Int,
    val day: Int
)

object PersianDateFormatter {

    fun gregorianToJalali(gYear: Int, gMonth: Int, gDay: Int): JalaliDate {
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

        var gy = gYear - 1600
        val gm = gMonth - 1
        val gd = gDay - 1

        var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
        for (i in 0 until gm) {
            gDayNo += gDaysInMonth[i]
        }
        if (gm > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) {
            gDayNo++
        }
        gDayNo += gd

        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        var jm = 0
        while (jm < 11 && jDayNo >= jDaysInMonth[jm]) {
            jDayNo -= jDaysInMonth[jm]
            jm++
        }
        val jd = jDayNo + 1

        return JalaliDate(year = jy, month = jm + 1, day = jd)
    }

    fun formatTimestamp(
        timestampMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val targetCal = Calendar.getInstance(timeZone).apply { timeInMillis = timestampMillis }
        val nowCal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }

        val hour = targetCal.get(Calendar.HOUR_OF_DAY)
        val minute = targetCal.get(Calendar.MINUTE)
        val timePart = String.format(Locale.US, "%02d:%02d", hour, minute)

        val isSameDay = targetCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
            targetCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

        val formatted = if (isSameDay) {
            "امروز • $timePart"
        } else {
            val jalali = gregorianToJalali(
                gYear = targetCal.get(Calendar.YEAR),
                gMonth = targetCal.get(Calendar.MONTH) + 1,
                gDay = targetCal.get(Calendar.DAY_OF_MONTH)
            )
            val datePart = String.format(Locale.US, "%04d/%02d/%02d", jalali.year, jalali.month, jalali.day)
            "$datePart • $timePart"
        }

        return toPersianDigits(formatted)
    }

    fun toPersianDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(('۰'.code + (ch.code - '0'.code)).toChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}
