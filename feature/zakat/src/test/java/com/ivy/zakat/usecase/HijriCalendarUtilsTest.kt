package com.ivy.zakat.usecase

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual as shouldBeGreaterThanOrEqualInt
import io.kotest.matchers.ints.shouldBeLessThanOrEqual as shouldBeLessThanOrEqualInt
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.Test

private const val MillisPerDay = 86_400_000L

/**
 * One Hijri lunar year varies between 354 and 355 days; these tests assert
 * the bounds rather than a single exact value.
 */
class HijriCalendarUtilsTest {

    @Test
    fun `hawl end is between 354 and 355 days after a typical start`() {
        val start = knownDateMillis(year = 2025, month = 1, day = 1)

        val end = HijriCalendarUtils.hawlEndDateMillis(start, offsetDays = 0)

        val days = (end - start) / MillisPerDay
        days shouldBeGreaterThanOrEqual 354L
        days shouldBeLessThanOrEqual 355L
    }

    @Test
    fun `positive hijri offset shifts the hawl end forward`() {
        val start = knownDateMillis(year = 2025, month = 1, day = 1)

        val endNoOffset = HijriCalendarUtils.hawlEndDateMillis(start, offsetDays = 0)
        val endPlusOne = HijriCalendarUtils.hawlEndDateMillis(start, offsetDays = 1)

        val delta = endPlusOne - endNoOffset
        // Shifting the start by +1 day shifts the end by roughly +1 day (0 or 1 — depends on Hijri
        // month length at the boundary).
        delta shouldBeGreaterThanOrEqual 0L
        delta shouldBeLessThanOrEqual MillisPerDay
    }

    @Test
    fun `negative hijri offset shifts the hawl end backward`() {
        val start = knownDateMillis(year = 2025, month = 1, day = 1)

        val endNoOffset = HijriCalendarUtils.hawlEndDateMillis(start, offsetDays = 0)
        val endMinusOne = HijriCalendarUtils.hawlEndDateMillis(start, offsetDays = -1)

        val delta = endNoOffset - endMinusOne
        delta shouldBeGreaterThanOrEqual 0L
        delta shouldBeLessThanOrEqual MillisPerDay
    }

    @Test
    fun `isHawlComplete is false when nisab reached less than 350 days ago`() {
        val reached = System.currentTimeMillis() - (350L * MillisPerDay)

        HijriCalendarUtils.isHawlComplete(reached, offsetDays = 0).shouldBeFalse()
    }

    @Test
    fun `isHawlComplete is true when nisab reached more than 360 days ago`() {
        val reached = System.currentTimeMillis() - (360L * MillisPerDay)

        HijriCalendarUtils.isHawlComplete(reached, offsetDays = 0).shouldBeTrue()
    }

    @Test
    fun `daysRemainingInHawl is non-negative`() {
        val reached = System.currentTimeMillis() - (370L * MillisPerDay)

        val remaining = HijriCalendarUtils.daysRemainingInHawl(reached, offsetDays = 0)

        remaining shouldBe 0
    }

    @Test
    fun `daysRemainingInHawl approximates the gap until hawl end`() {
        val reached = System.currentTimeMillis() - (100L * MillisPerDay)

        val remaining = HijriCalendarUtils.daysRemainingInHawl(reached, offsetDays = 0)

        // ~254 days remaining give-or-take calendar boundary effects.
        remaining.shouldBeGreaterThanOrEqualInt(250)
        remaining.shouldBeLessThanOrEqualInt(256)
    }

    @Test
    fun `totalHawlDays is around 354-355`() {
        val reached = knownDateMillis(year = 2025, month = 1, day = 1)

        val total = HijriCalendarUtils.totalHawlDays(reached, offsetDays = 0)

        total.shouldBeGreaterThanOrEqualInt(354)
        total.shouldBeLessThanOrEqualInt(355)
    }

    private fun knownDateMillis(year: Int, month: Int, day: Int): Long {
        return java.time.LocalDate.of(year, month, day).toEpochDay() * MillisPerDay
    }
}
