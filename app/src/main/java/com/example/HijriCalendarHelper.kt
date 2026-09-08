package com.example

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Robust, timezone-aware Hijri (Islamic) Calendar calculation.
 * Accurately calculates the Hijri date across all years (past, present, and future)
 * without hardcoded dates or unreliable fixed day offsets.
 */
object HijriCalendarHelper {

    val ISLAMIC_MONTHS = listOf(
        "MUHARRAM", "SAFAR", "RABI' AL-AWWAL", "RABI' AL-THANI",
        "JUMADA AL-ULA", "JUMADA AL-AKHIRAH", "RAJAB", "SHA'BAN",
        "RAMADAN", "SHAWWAL", "DHU AL-QI'DAH", "DHU AL-HIJJAH"
    )

    data class HijriDate(
        val day: Int,
        val month: Int, // 1-12
        val year: Int
    ) {
        val monthName: String
            get() = ISLAMIC_MONTHS.getOrNull(month - 1) ?: "RAMADAN"

        fun format(): String = "$day $monthName $year AH"
    }

    /**
     * Calculates the accurate Hijri date for the given timezone and timestamp.
     * Uses Java 8 HijrahChronology when available, falling back to Android ICU IslamicCalendar,
     * and finally the exact astronomical civil Hijri algorithmic conversion.
     */
    fun getHijriDate(timeZoneId: String, timestampMs: Long = System.currentTimeMillis()): HijriDate {
        // Method 1: java.time.chrono.HijrahDate (Umm al-Qura calendar, Java 8 / Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val zoneId = try {
                    java.time.ZoneId.of(timeZoneId)
                } catch (e: Exception) {
                    java.time.ZoneId.systemDefault()
                }
                val instant = java.time.Instant.ofEpochMilli(timestampMs)
                val zonedDateTime = instant.atZone(zoneId)
                val localDate = zonedDateTime.toLocalDate()
                val hijrahDate = java.time.chrono.HijrahChronology.INSTANCE.date(localDate)

                val year = hijrahDate.get(java.time.temporal.ChronoField.YEAR)
                val month = hijrahDate.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
                val day = hijrahDate.get(java.time.temporal.ChronoField.DAY_OF_MONTH)

                if (year in 1..2500 && month in 1..12 && day in 1..30) {
                    return HijriDate(day, month, year)
                }
            } catch (ignored: Throwable) {}
        }

        // Method 2: android.icu.util.IslamicCalendar (Android N+, API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val icuTz = android.icu.util.TimeZone.getTimeZone(timeZoneId)
                val icuCal = android.icu.util.IslamicCalendar()
                icuCal.timeZone = icuTz
                icuCal.timeInMillis = timestampMs

                val day = icuCal.get(android.icu.util.Calendar.DAY_OF_MONTH)
                val month = icuCal.get(android.icu.util.Calendar.MONTH) + 1 // ICU month is 0-based
                val year = icuCal.get(android.icu.util.Calendar.YEAR)

                if (year in 1..2500 && month in 1..12 && day in 1..30) {
                    return HijriDate(day, month, year)
                }
            } catch (ignored: Throwable) {}
        }

        // Method 3: Deterministic astronomical civil Hijri algorithm based on Julian Day Number
        return calculateCivilHijriDate(timeZoneId, timestampMs)
    }

    /**
     * Standard Astronomical Islamic Civil Calendar conversion based on Julian Day Number (JDN).
     * Computes the exact Hijri date accurately for any year, taking into account the 30-year leap cycle.
     */
    fun calculateCivilHijriDate(timeZoneId: String, timestampMs: Long = System.currentTimeMillis()): HijriDate {
        val tz = try {
            TimeZone.getTimeZone(timeZoneId)
        } catch (e: Exception) {
            TimeZone.getDefault()
        }
        val cal = Calendar.getInstance(tz).apply { timeInMillis = timestampMs }
        val gYear = cal.get(Calendar.YEAR)
        val gMonth = cal.get(Calendar.MONTH) + 1 // 1..12
        val gDay = cal.get(Calendar.DAY_OF_MONTH)

        // Gregorian to Julian Day Number
        val a = (14 - gMonth) / 12
        val y = gYear + 4800 - a
        val m = gMonth + 12 * a - 3
        val jdn = gDay + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045

        // JDN to Hijri date conversion
        // Epoch of Islamic civil calendar: July 16, 622 CE (JDN 1948440)
        val epochJdn = 1948440
        val l = jdn - epochJdn + 10632
        val n = (l - 1) / 10631
        val l1 = l - 10631 * n + 354
        val j = ((10985 - l1) / 5316) * ((50 * l1) / 17719) + (l1 / 5670) * ((43 * l1) / 15238)
        val l2 = l1 - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29
        val hMonth = (24 * l2) / 709
        val hDay = l2 - (709 * hMonth) / 24
        val hYear = 30 * n + j - 30

        val safeDay = hDay.coerceIn(1, 30)
        val safeMonth = hMonth.coerceIn(1, 12)
        return HijriDate(safeDay, safeMonth, hYear)
    }

    /**
     * Formats the Hijri date for the user's stored timezone or device default.
     */
    fun getFormattedHijriString(context: Context? = null, timeZoneId: String? = null): String {
        val resolvedTz = timeZoneId
            ?: context?.let { QadhaStorageHelper(it).getTimeZoneId() }
            ?: TimeZone.getDefault().id

        return getHijriDate(resolvedTz).format()
    }
}
