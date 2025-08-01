package org.multipaz.compose.datetime

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import org.multipaz.datetime.FormatStyle
import org.multipaz.datetime.formatLocalized
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Formats a string to display a point in time as a date and time string.
 *
 * @param instant the point in time to display.
 * @param timeZone the timezone to use for displaying the point in time.
 * @param dateStyle the amount of data to include in the date component.
 * @param timeStyle the amount of date to include in the time component.
 * @param includeDurationFromNow if `true`, will include a textual representation of how
 *   far in the past or future the point in time is relative to the current time.
 * @param durationFromNowColor the color to use for the duration from now component.
 */
@Composable
fun formattedDateTime(
    instant: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    dateStyle: FormatStyle = FormatStyle.MEDIUM,
    timeStyle: FormatStyle = FormatStyle.MEDIUM,
    includeDurationFromNow: Boolean = false,
    durationFromNowColor: Color = MaterialTheme.colorScheme.secondary
 ): AnnotatedString {
    return buildAnnotatedString {
        append(instant.toLocalDateTime(timeZone).formatLocalized(dateStyle, timeStyle))
        if (includeDurationFromNow) {
            withStyle(style = SpanStyle(color = durationFromNowColor)) {
                append(" — ")
                append(durationFromNowText(instant))
            }
        }
    }
}
