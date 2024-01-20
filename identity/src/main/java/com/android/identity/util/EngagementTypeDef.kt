package com.android.identity.util

import com.android.identity.util.EngagementTypeDef.TypeDef.ENGAGEMENT_METHOD_NFC_NEGOTIATED_HANDOVER
import com.android.identity.util.EngagementTypeDef.TypeDef.ENGAGEMENT_METHOD_NFC_STATIC_HANDOVER
import com.android.identity.util.EngagementTypeDef.TypeDef.ENGAGEMENT_METHOD_NOT_ENGAGED
import com.android.identity.util.EngagementTypeDef.TypeDef.ENGAGEMENT_METHOD_QR_CODE
import com.android.identity.util.EngagementTypeDef.TypeDef.ENGAGEMENT_METHOD_REVERSE
import com.android.identity.util.EngagementTypeDef.TypeDef.ENGAGEMENT_METHOD_UNATTENDED

/**
 * Engagement Type Definitions
 */
enum class EngagementTypeDef(val engagementValue: Int) {
    NOT_ENGAGED(ENGAGEMENT_METHOD_NOT_ENGAGED), // default, uninitialized variable (not a valid engagement type)
    QR_CODE(ENGAGEMENT_METHOD_QR_CODE),
    NFC_STATIC_HANDOVER(ENGAGEMENT_METHOD_NFC_STATIC_HANDOVER),
    NFC_NEGOTIATED_HANDOVER(ENGAGEMENT_METHOD_NFC_NEGOTIATED_HANDOVER),
    REVERSE(ENGAGEMENT_METHOD_REVERSE),
    UNATTENDED(ENGAGEMENT_METHOD_UNATTENDED),
    ;

    /**
     * Int definitions for all the engagement method types.
     *
     * These used to live in com.android.identity.android.mdoc.deviceretrieval.VerificationHelper
     */
    object TypeDef {
        const val ENGAGEMENT_METHOD_NOT_ENGAGED = 0
        const val ENGAGEMENT_METHOD_QR_CODE = 1
        const val ENGAGEMENT_METHOD_NFC_STATIC_HANDOVER = 2
        const val ENGAGEMENT_METHOD_NFC_NEGOTIATED_HANDOVER = 3
        const val ENGAGEMENT_METHOD_REVERSE = 4
        const val ENGAGEMENT_METHOD_UNATTENDED = 5
    }

    companion object {
        /**
         * Return an EngagementTypeDef according to name
         */
        fun fromString(name: String) = values().firstOrNull { it.name == name } ?: NOT_ENGAGED

        /**
         * Return an EngagementTypeDef according to engagement value
         */
        fun fromValue(value: Int?) =
            values().firstOrNull { it.engagementValue == value } ?: NOT_ENGAGED
    }
}