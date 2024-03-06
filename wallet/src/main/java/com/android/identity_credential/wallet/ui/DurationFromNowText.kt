package com.android.identity_credential.wallet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.android.identity_credential.wallet.R
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.plus
import kotlinx.datetime.yearsUntil
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Format time interval to the time instant in human-readable way, such as
 * "3 days and 5 hours ago" or "4 months and 3 days from now".
 *
 * [instant] time instant in the past or future
 * [now] time now, only exposed for testing
 */
@Composable
fun durationFromNowText(instant: Instant, now: Instant = Clock.System.now()): String {
    val tick = remember { mutableIntStateOf(0) }
    tick.intValue  // reading value ensures update when tick changes
    val (text, updateAt) = if (instant > now) {
        futureRawTextAndUpdateTime(instant, now)
    } else {
        pastRawTextAndUpdateTime(instant, now)
    }
    val delayDuration = maxOf(updateAt.minus(now), 16.milliseconds)
    SideEffect {
        Timer().schedule(delayDuration.inWholeMilliseconds) {
            tick.intValue++
        }
    }
    return if (instant > now) {
        stringResource(R.string.duration_time_from_now, text)
    } else {
        stringResource(R.string.duration_time_ago, text)
    }
}

/**
 * Formats text for duration from time instant in the past and computes the time instant when
 * this text has to be updated.
 */
@Composable
internal fun pastRawTextAndUpdateTime(past: Instant, now: Instant): Pair<String, Instant> {
    val (majorUnit, majorCount) = selectMajorUnit(past, now)
    if (majorUnit == null) {
        return Pair(stringResource(R.string.duration_now), past.plus(1.seconds))
    }
    val majorStr = pluralStringResource(
        majorUnit.pluralResourceId, majorCount.toInt(), majorCount
    )
    val minorUnit = majorUnit.minorUnit()
        ?: return Pair(majorStr, majorUnit.addTo(past, majorCount + 1))
    val minorUnitInstance = majorUnit.addTo(now, -majorCount)
    val minorCount = minorUnit.wholeOf(past, minorUnitInstance)
    val update = minorUnit.addTo(majorUnit.addTo(past, majorCount), minorCount + 1)
    if (minorCount != 0L) {
        val minorStr = pluralStringResource(
            minorUnit.pluralResourceId, minorCount.toInt(), minorCount
        )
        return Pair(stringResource(majorUnit.combinerResourceId, majorStr, minorStr), update)
    }
    return Pair(majorStr, update)
}

/**
 * Formats text for duration to time instant the future and computes the time instant when this
 * text has to be updated.
 */
@Composable
internal fun futureRawTextAndUpdateTime(future: Instant, now: Instant): Pair<String, Instant> {
    val (majorUnit, majorCount) = selectMajorUnit(now, future)
    if (majorUnit == null) {
        return Pair(stringResource(R.string.duration_now), future)
    }
    val majorStr = pluralStringResource(
        majorUnit.pluralResourceId, majorCount.toInt(), majorCount
    )
    val minorUnit = majorUnit.minorUnit()
        ?: return Pair(majorStr, majorUnit.addTo(future, -majorCount))
    val minorUnitInstance = majorUnit.addTo(now, majorCount)
    val minorCount = minorUnit.wholeOf(minorUnitInstance, future)
    val update = minorUnit.addTo(majorUnit.addTo(future, -majorCount), -minorCount)
    if (minorCount != 0L) {
        val minorStr = pluralStringResource(
            minorUnit.pluralResourceId, minorCount.toInt(), minorCount
        )
        return Pair(stringResource(majorUnit.combinerResourceId, majorStr, minorStr), update)
    }
    return Pair(majorStr, update)
}

internal sealed class DurationUnit(
    val pluralResourceId: Int,
    val combinerResourceId: Int) {

    internal abstract fun minorUnit(): DurationUnit?

    internal abstract fun addTo(instant: Instant, num: Long): Instant
    internal abstract fun wholeOf(past: Instant, future: Instant): Long
}

internal object Second : DurationUnit(R.plurals.duration_second, 0) {
    override fun minorUnit(): DurationUnit? = null

    override fun addTo(instant: Instant, num: Long): Instant = instant.plus(num.seconds)

    override fun wholeOf(past: Instant, future: Instant): Long = future.minus(past).inWholeSeconds
}

internal object Minute : DurationUnit(R.plurals.duration_minute, R.string.duration_minutes_and_seconds) {
    override fun minorUnit(): DurationUnit = Second

    override fun addTo(instant: Instant, num: Long): Instant = instant.plus(num.minutes)

    override fun wholeOf(past: Instant, future: Instant): Long = future.minus(past).inWholeMinutes
}

internal object Hour : DurationUnit(R.plurals.duration_hour, R.string.duration_hours_and_minutes) {
    override fun minorUnit(): DurationUnit = Minute

    override fun addTo(instant: Instant, num: Long): Instant = instant.plus(num.hours)

    override fun wholeOf(past: Instant, future: Instant): Long = future.minus(past).inWholeHours
}

internal object Day : DurationUnit(R.plurals.duration_day, R.string.duration_days_and_hours) {
    override fun minorUnit(): DurationUnit = Hour

    override fun addTo(instant: Instant, num: Long): Instant = instant.plus(num.days)

    override fun wholeOf(past: Instant, future: Instant): Long = future.minus(past).inWholeDays
}

internal object Week : DurationUnit(R.plurals.duration_week, R.string.duration_weeks_and_days) {
    override fun minorUnit(): DurationUnit = Day

    override fun addTo(instant: Instant, num: Long): Instant = instant.plus((7*num).days)

    override fun wholeOf(past: Instant, future: Instant): Long = future.minus(past).inWholeDays / 7
}

internal object Month : DurationUnit(R.plurals.duration_month, R.string.duration_months_and_days) {
    override fun minorUnit(): DurationUnit = Day

    override fun addTo(instant: Instant, num: Long): Instant =
        instant.plus(DateTimePeriod(months = num.toInt()), TimeZone.currentSystemDefault())

    override fun wholeOf(past: Instant, future: Instant): Long =
        past.monthsUntil(future, TimeZone.currentSystemDefault()).toLong()
}

internal object Year : DurationUnit(R.plurals.duration_year, R.string.duration_years_and_months) {
    override fun minorUnit(): DurationUnit = Month

    override fun addTo(instant: Instant, num: Long): Instant =
        instant.plus(DateTimePeriod(years = num.toInt()), TimeZone.currentSystemDefault())

    override fun wholeOf(past: Instant, future: Instant): Long =
        past.yearsUntil(future, TimeZone.currentSystemDefault()).toLong()
}

private val units = arrayOf(Year, Month, Week, Day, Hour, Minute, Second)

private fun selectMajorUnit(past: Instant, future: Instant): Pair<DurationUnit?, Long> {
    for (majorUnit in units) {
        val majorCount = majorUnit.wholeOf(past, future)
        if (majorCount != 0L) {
            return Pair(majorUnit, majorCount)
        }
    }
    return Pair(null, 0)
}