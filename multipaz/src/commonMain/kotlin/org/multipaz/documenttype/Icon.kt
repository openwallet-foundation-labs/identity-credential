package org.multipaz.documenttype

/**
 * An enumeration of icons used to represent ISO mdoc data elements or JSON-based credential claims.
 *
 * @property iconName the icon name according to https://fonts.google.com/icons
 */
enum class Icon(
    val iconName: String
) {
    PERSON("person"),
    TODAY("today"),
    DATE_RANGE("date_range"),
    CALENDAR_CLOCK("calendar_clock"),
    ACCOUNT_BALANCE("account_balance"),
    NUMBERS("numbers"),
    ACCOUNT_BOX("account_box"),
    DIRECTIONS_CAR("directions_car"),
    LANGUAGE("language"),
    EMERGENCY("emergency"),
    PLACE("place"),
    SIGNATURE("signature"),
    MILITARY_TECH("military_tech"),
    STARS("stars"),
    FACE("face"),
    FINGERPRINT("fingerprint"),
    EYE_TRACKING("eye_tracking"),
    AIRPORT_SHUTTLE("airport_shuttle"),
    PANORAMA_WIDE_ANGLE("panorama_wide_angle"),
    IMAGE("image"),
    LOCATION_CITY("location_city"),
    DIRECTIONS("directions"),
    HOUSE("house"),
    FLAG("flag"),
    APARTMENT("apartment"),
    LANGUAGE_JAPANESE_KANA("language_japanese_kana")
}