package org.multipaz.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AirportShuttle
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.AirportShuttle
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Emergency
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import org.multipaz.documenttype.Icon

/**
 * Extension function to get an [ImageVector] for an icon.
 */
fun Icon.getDefaultImageVector(): ImageVector {
    return when (this) {
        Icon.PERSON -> Icons.Default.Person
        Icon.TODAY -> Icons.Default.Today
        Icon.DATE_RANGE -> Icons.Default.DateRange
        Icon.CALENDAR_CLOCK -> Icons.Default.CalendarToday   // TODO: CalendarClock not available
        Icon.ACCOUNT_BALANCE -> Icons.Default.AccountBalance
        Icon.NUMBERS -> Icons.Default.Numbers
        Icon.ACCOUNT_BOX -> Icons.Default.AccountBox
        Icon.DIRECTIONS_CAR -> Icons.Default.DirectionsCar
        Icon.LANGUAGE -> Icons.Default.Language
        Icon.EMERGENCY -> Icons.Default.Emergency
        Icon.PLACE -> Icons.Default.Place
        Icon.SIGNATURE -> Icons.Default.Draw                 // TODO: Signature not available
        Icon.MILITARY_TECH -> Icons.Default.MilitaryTech
        Icon.STARS -> Icons.Default.Stars
        Icon.FACE -> Icons.Default.Face
        Icon.FINGERPRINT -> Icons.Default.Fingerprint
        Icon.EYE_TRACKING -> Icons.Default.Visibility        // TODO: EyeTracking not available
        Icon.AIRPORT_SHUTTLE -> Icons.Default.AirportShuttle
    }
}

/**
 * Extension function to get an [ImageVector] for an icon.
 */
fun Icon.getOutlinedImageVector(): ImageVector {
    return when (this) {
        Icon.PERSON -> Icons.Outlined.Person
        Icon.TODAY -> Icons.Outlined.Today
        Icon.DATE_RANGE -> Icons.Outlined.DateRange
        Icon.CALENDAR_CLOCK -> Icons.Outlined.CalendarToday   // TODO: CalendarClock not available
        Icon.ACCOUNT_BALANCE -> Icons.Outlined.AccountBalance
        Icon.NUMBERS -> Icons.Outlined.Numbers
        Icon.ACCOUNT_BOX -> Icons.Outlined.AccountBox
        Icon.DIRECTIONS_CAR -> Icons.Outlined.DirectionsCar
        Icon.LANGUAGE -> Icons.Outlined.Language
        Icon.EMERGENCY -> Icons.Outlined.Emergency
        Icon.PLACE -> Icons.Outlined.Place
        Icon.SIGNATURE -> Icons.Outlined.Draw                 // TODO: Signature not available
        Icon.MILITARY_TECH -> Icons.Outlined.MilitaryTech
        Icon.STARS -> Icons.Outlined.Stars
        Icon.FACE -> Icons.Outlined.Face
        Icon.FINGERPRINT -> Icons.Outlined.Fingerprint
        Icon.EYE_TRACKING -> Icons.Outlined.Visibility        // TODO: EyeTracking not available
        Icon.AIRPORT_SHUTTLE -> Icons.Outlined.AirportShuttle
    }
}