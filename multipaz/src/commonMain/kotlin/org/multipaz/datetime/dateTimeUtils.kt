package org.multipaz.datetime

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Styles used when formatting time or dates.
 *
 * Note that the style is a function of the platform the app is running on and
 * might vary slightly from one platform to another or even on the same platform
 * from one release to another. An app should not make any assumptions about the
 * format.
 */
enum class FormatStyle {
    /** Short style, typically numeric. */
    SHORT,

    /** Medium style with some detail. */
    MEDIUM,

    /** Long style with a lot of detail. */
    LONG,

    /** Full style with the most detail. */
    FULL,
}

/**
 * Formats a localized string representing a [LocalDate].
 *
 * @param dateStyle the amount of detail to include for the date.
 * @return the localized string.
 */
expect fun LocalDate.formatLocalized(
    dateStyle: FormatStyle = FormatStyle.MEDIUM,
): String

/**
 * Formats a localized string representing a [LocalDateTime].
 *
 * @param dateStyle the amount of detail to include for the date component.
 * @param timeStyle the amount of detail to include for the time component.
 * @return the localized string.
 */
expect fun LocalDateTime.formatLocalized(
    dateStyle: FormatStyle = FormatStyle.MEDIUM,
    timeStyle: FormatStyle = FormatStyle.MEDIUM,
): String
