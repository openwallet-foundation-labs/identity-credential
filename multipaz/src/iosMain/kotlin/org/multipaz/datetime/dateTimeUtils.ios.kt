package org.multipaz.datetime

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toNSDateComponents
import platform.Foundation.NSCalendar
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterFullStyle
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSDateFormatterStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.localTimeZone

actual fun LocalDate.formatLocalized(
    dateStyle: FormatStyle,
): String {
    val dateFormatter = NSDateFormatter()
    dateFormatter.dateStyle = dateStyle.toNSDateFormatterStyle()
    dateFormatter.timeStyle = NSDateFormatterNoStyle
    dateFormatter.locale = NSLocale.currentLocale
    dateFormatter.timeZone = NSTimeZone.localTimeZone
    val components = this.toNSDateComponents()
    components.calendar = NSCalendar.currentCalendar
    return dateFormatter.stringFromDate(components.date!!)
}

actual fun LocalDateTime.formatLocalized(
    dateStyle: FormatStyle,
    timeStyle: FormatStyle
): String {
    val dateFormatter = NSDateFormatter()
    dateFormatter.dateStyle = dateStyle.toNSDateFormatterStyle()
    dateFormatter.timeStyle = timeStyle.toNSDateFormatterStyle()
    dateFormatter.locale = NSLocale.currentLocale
    dateFormatter.timeZone = NSTimeZone.localTimeZone
    val components = this.toNSDateComponents()
    components.calendar = NSCalendar.currentCalendar
    return dateFormatter.stringFromDate(components.date!!)
}

private fun FormatStyle.toNSDateFormatterStyle(): NSDateFormatterStyle {
    return when (this) {
        FormatStyle.SHORT -> NSDateFormatterShortStyle
        FormatStyle.MEDIUM -> NSDateFormatterMediumStyle
        FormatStyle.LONG -> NSDateFormatterLongStyle
        FormatStyle.FULL -> NSDateFormatterFullStyle
    }
}