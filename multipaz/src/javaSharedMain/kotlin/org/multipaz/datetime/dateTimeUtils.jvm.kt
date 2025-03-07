package org.multipaz.datetime

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.multipaz.datetime.FormatStyle as MultipazFormsatStyle

actual fun LocalDate.formatLocalized(
    dateStyle: MultipazFormsatStyle
): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(dateStyle.toJavaFormatStyle())
    return this.toJavaLocalDate().format(formatter)
}

actual fun LocalDateTime.formatLocalized(
    dateStyle: MultipazFormsatStyle,
    timeStyle: MultipazFormsatStyle
): String {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(
        dateStyle.toJavaFormatStyle(),
        timeStyle.toJavaFormatStyle()
    )
    return this.toJavaLocalDateTime()
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

private fun MultipazFormsatStyle.toJavaFormatStyle(): FormatStyle {
    return when (this) {
        MultipazFormsatStyle.SHORT -> FormatStyle.SHORT
        MultipazFormsatStyle.MEDIUM -> FormatStyle.MEDIUM
        MultipazFormsatStyle.LONG -> FormatStyle.LONG
        MultipazFormsatStyle.FULL -> FormatStyle.FULL
    }
}