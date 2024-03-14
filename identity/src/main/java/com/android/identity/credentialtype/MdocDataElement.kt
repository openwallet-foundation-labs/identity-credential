/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.identity.credentialtype

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.Tagged
import com.android.identity.cbor.DiagnosticOption
import com.android.identity.util.Logger
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Class containing the metadata of a data element of an mDoc
 * Credential Type
 *
 * @param attribute the generic metadata.
 * @param mandatory a mDoc specific indication whether the
 * data element is mandatory
 */
data class MdocDataElement(
    val attribute: CredentialAttribute,
    val mandatory: Boolean
) {
    companion object {
        private const val TAG = "MdocDataElement"
    }

    /**
     * Renders the value of this data element as a string.
     *
     * The returned value is suitable for displaying in an user interface.
     *
     * This method never throws an exception, if an error happens it falls back
     * to returning the CBOR rendered as diagnostics using [Cbor.toDiagnostics]
     * using the flag [DiagnosticOption.BSTR_PRINT_LENGTH].
     *
     * @param value the value, as a CBOR data item
     * @param timeZone the time zone to use, for rendering date-time. It is never used
     * for rendering a full-date.
     * @param trueFalseStrings a pair with the strings to use for false and true.
     * @return a string representing the value.
     */
    fun renderValue(
        value: DataItem,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        trueFalseStrings: Pair<String, String> = Pair("false", "true")
    ): String {
        val diagnosticsOptions = setOf(DiagnosticOption.BSTR_PRINT_LENGTH)
        val ret = try {
            when (attribute.type) {
                // TODO: translations? maybe take the strings to use for true/false as a param
                CredentialAttributeType.Boolean -> {
                    if (value.asBoolean)
                        trueFalseStrings.second
                    else
                        trueFalseStrings.first
                }

                CredentialAttributeType.ComplexType -> Cbor.toDiagnostics(value)

                CredentialAttributeType.Date -> {
                    // value can be either tdate or full-date
                    val tagNumber = (value as Tagged).tagNumber
                    val dt = when (tagNumber) {
                        Tagged.FULL_DATE_STRING -> {
                            LocalDateTime(
                                LocalDate.parse(value.asTagged.asTstr),
                                LocalTime(0, 0, 0)
                            )
                        }
                        Tagged.DATE_TIME_STRING -> {
                            val pointInTime = Instant.parse(value.asTagged.asTstr)
                            pointInTime.toLocalDateTime(timeZone)
                        }
                        else -> {
                            throw IllegalArgumentException("Unexpected tag $tagNumber")
                        }
                    }
                    // TODO: use DateTimeFormat in kotlinx-datetime 0.6.0 when released
                    String.format(
                        "%04d-%02d-%02d", dt.year, dt.monthNumber, dt.dayOfMonth
                    )
                }

                CredentialAttributeType.DateTime -> {
                    // value can be either tdate or full-date
                    val tagNumber = (value as Tagged).tagNumber
                    val dt = when (tagNumber) {
                        Tagged.FULL_DATE_STRING -> {
                            LocalDateTime(
                                LocalDate.parse(value.asTagged.asTstr),
                                LocalTime(0, 0, 0)
                            )
                        }
                        Tagged.DATE_TIME_STRING -> {
                            val pointInTime = Instant.parse(value.asTagged.asTstr)
                            pointInTime.toLocalDateTime(timeZone)
                        }
                        else -> {
                            throw IllegalArgumentException("Unexpected tag $tagNumber")
                        }
                    }
                    // TODO: use DateTimeFormat in kotlinx-datetime 0.6.0 when released
                    String.format(
                        "%04d-%02d-%02d %02d:%02d:%02d",
                        dt.year, dt.monthNumber, dt.dayOfMonth, dt.time.hour, dt.time.minute, dt.time.second
                    )
                }

                is CredentialAttributeType.IntegerOptions -> {
                    val option = attribute.type.options.find { it.value == value.asNumber.toInt() }
                    option?.displayName ?: value.asNumber.toString()
                }
                CredentialAttributeType.Number -> value.asNumber.toString()
                CredentialAttributeType.Picture -> Cbor.toDiagnostics(value, diagnosticsOptions)
                CredentialAttributeType.String -> value.asTstr
                is CredentialAttributeType.StringOptions -> {
                    val option = attribute.type.options.find { it.value == value.asTstr }
                    option?.displayName ?: value.asTstr
                }
            }
        } catch (e: Throwable) {
            val diagnosticsString = Cbor.toDiagnostics(value, diagnosticsOptions)
            Logger.w(TAG, "Error decoding value $diagnosticsString for data element " +
                    "${attribute.identifier}, falling back to diagnostics", e)
            diagnosticsString
        }

        return ret
    }
}