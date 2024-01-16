package com.android.identity.wallet.util

enum class EngagementType {
    NFC,
    QR,
    UNATTENDED,
    NONE // default, uninitialized variable (not a valid engagement type)

    ;

    companion object {
        fun fromString(name: String) = values().firstOrNull { it.name == name } ?: NONE
    }
}