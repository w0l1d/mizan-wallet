package com.ivy.zakat.usecase

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

private const val MillisPerDay = 86400000L
private const val ApproximateLunarYearDays = 354L

object HijriCalendarUtils {

    fun todayHijri(offsetDays: Int = 0): HijrahDate {
        val today = LocalDate.now().plusDays(offsetDays.toLong())
        return HijrahDate.from(today)
    }

    fun formatHijri(hijriDate: HijrahDate): String {
        val day = hijriDate.get(ChronoField.DAY_OF_MONTH)
        val month = hijriDate.get(ChronoField.MONTH_OF_YEAR)
        val year = hijriDate.get(ChronoField.YEAR)
        return "$day ${getHijriMonthName(month)} $year"
    }

    fun hawlEndDateMillis(startMillis: Long, offsetDays: Int = 0): Long {
        return try {
            val startLocal = LocalDate.ofEpochDay(startMillis / MillisPerDay)
                .plusDays(offsetDays.toLong())
            val hijriStart = HijrahDate.from(startLocal)
            val hijriEnd = hijriStart.plus(1, ChronoUnit.YEARS)
            val endLocal = LocalDate.from(hijriEnd)
            endLocal.toEpochDay() * MillisPerDay
        } catch (_: Exception) {
            startMillis + (ApproximateLunarYearDays * MillisPerDay)
        }
    }

    fun isHawlComplete(nisabReachedMillis: Long, offsetDays: Int = 0): Boolean {
        val endMillis = hawlEndDateMillis(nisabReachedMillis, offsetDays)
        return System.currentTimeMillis() >= endMillis
    }

    fun daysRemainingInHawl(nisabReachedMillis: Long, offsetDays: Int = 0): Int {
        val endMillis = hawlEndDateMillis(nisabReachedMillis, offsetDays)
        val diff = endMillis - System.currentTimeMillis()
        return (diff / MillisPerDay).toInt().coerceAtLeast(0)
    }

    fun totalHawlDays(nisabReachedMillis: Long, offsetDays: Int = 0): Int {
        val endMillis = hawlEndDateMillis(nisabReachedMillis, offsetDays)
        return ((endMillis - nisabReachedMillis) / MillisPerDay).toInt().coerceAtLeast(1)
    }

    fun daysElapsedInHawl(nisabReachedMillis: Long): Int {
        val diff = System.currentTimeMillis() - nisabReachedMillis
        return (diff / MillisPerDay).toInt().coerceAtLeast(0)
    }

    fun epochMillisToHijri(epochMillis: Long, offsetDays: Int = 0): HijrahDate {
        val local = LocalDate.ofEpochDay(epochMillis / MillisPerDay)
            .plusDays(offsetDays.toLong())
        return HijrahDate.from(local)
    }

    fun formatEpochMillisAsGregorian(epochMillis: Long): String {
        val local = LocalDate.ofEpochDay(epochMillis / MillisPerDay)
        return "${local.dayOfMonth}/${local.monthValue}/${local.year}"
    }

    @Suppress("MagicNumber")
    fun getHijriMonthName(month: Int): String = when (month) {
        1 -> "Muharram"
        2 -> "Safar"
        3 -> "Rabi al-Awwal"
        4 -> "Rabi al-Thani"
        5 -> "Jumada al-Ula"
        6 -> "Jumada al-Thani"
        7 -> "Rajab"
        8 -> "Sha'ban"
        9 -> "Ramadan"
        10 -> "Shawwal"
        11 -> "Dhul Qi'dah"
        12 -> "Dhul Hijjah"
        else -> ""
    }
}
