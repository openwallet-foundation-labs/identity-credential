/*
 * Copyright (C) 2019 Google LLC
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

package com.ul.ims.gmdl.cbordata.security.mso

import android.icu.util.Calendar
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.MajorType
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import com.ul.ims.gmdl.cbordata.utils.CborUtils
import com.ul.ims.gmdl.cbordata.utils.DateUtils

/**
 *
 * ValidityInfo = {
 *  "signed" : tdate,
 *  "validFrom" : tdate,
 *  "validUntil" : tdate,
 *   ? "expectedUpdate" : tdate
 *   }
 *
 * **/

class ValidityInfo private constructor(
    val signed: Calendar,
    val validFrom: Calendar,
    val validUntil: Calendar,
    val expectedUpdate: Calendar?
) {
    companion object {
        const val SIGNED = "signed"
        const val VALID_FROM = "validFrom"
        const val VALID_UNTIL = "validUntil"
        const val EXPECTED_UPDATE = "expectedUpdate"
        const val LOG_TAG = "ValidityInfo"
    }

    fun toDataItem(): DataItem {
        val validityInfo = Map()

        // signed
        validityInfo.put(
            UnicodeString(SIGNED),
            CborUtils.dateTimeToUnicodeString(signed)
        )

        // validFrom
        validityInfo.put(
            UnicodeString(VALID_FROM),
            CborUtils.dateTimeToUnicodeString(validFrom)
        )

        // validUntil
        validityInfo.put(
            UnicodeString(VALID_UNTIL),
            CborUtils.dateTimeToUnicodeString(validUntil)
        )

        // expectedUpdate
        expectedUpdate?.let { eu ->
            validityInfo.put(
                UnicodeString(EXPECTED_UPDATE),
                CborUtils.dateTimeToUnicodeString(expectedUpdate)
            )
        }

        return validityInfo
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ValidityInfo

        if (signed != other.signed) return false
        if (validFrom != other.validFrom) return false
        if (validUntil != other.validUntil) return false
        if (expectedUpdate != other.expectedUpdate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signed.hashCode()
        result = 31 * result + validFrom.hashCode()
        result = 31 * result + validUntil.hashCode()
        result = 31 * result + (expectedUpdate?.hashCode() ?: 0)
        return result
    }

    class Builder {
        private var signed: Calendar? = null
        private var validFrom: Calendar? = null
        private var validUntil: Calendar? = null
        private var expectedUpdate: Calendar? = null

        fun setSigned(dateTime: Calendar) = apply {
            this.signed = dateTime
        }

        fun setValidFrom(dateTime: Calendar) = apply {
            this.validFrom = dateTime
        }

        fun setValidUntil(dateTime: Calendar) = apply {
            this.validUntil = dateTime
        }

        fun setExpectedUpdate(dateTime: Calendar) = apply {
            this.expectedUpdate = dateTime
        }

        fun fromDataItem(dataItem: DataItem) = apply {
            if (dataItem.majorType == MajorType.MAP) {
                val dataItemMap = dataItem as Map

                // signed
                val signedKey = UnicodeString(SIGNED)
                if (dataItemMap.keys.contains(signedKey)) {
                    signed = DateUtils.cborDecodeCalendar(
                        decodeUnicodeString(
                            dataItemMap.get(signedKey)
                        )
                    )
                }

                // validFrom
                val validFromKey = UnicodeString(VALID_FROM)
                if (dataItemMap.keys.contains(validFromKey)) {
                    validFrom = DateUtils.cborDecodeCalendar(
                        decodeUnicodeString(
                            dataItemMap.get(validFromKey)
                        )
                    )
                }

                // validUntil
                val validUntilKey = UnicodeString(VALID_UNTIL)
                if (dataItemMap.keys.contains(validUntilKey)) {
                    validUntil = DateUtils.cborDecodeCalendar(
                        decodeUnicodeString(
                            dataItemMap.get(validUntilKey)
                        )
                    )
                }

                // expectedUpdate
                val expectedUpdateKey = UnicodeString(EXPECTED_UPDATE)
                if (dataItemMap.keys.contains(expectedUpdateKey)) {
                    expectedUpdate = DateUtils.cborDecodeCalendar(
                        decodeUnicodeString(
                            dataItemMap.get(expectedUpdateKey)
                        )
                    )
                }
            }
        }

        private fun decodeUnicodeString(dataItem : DataItem) : String {
            if (dataItem.majorType == MajorType.UNICODE_STRING) {
                return (dataItem as UnicodeString).string
            }

            throw RuntimeException("DataItem is not a UnicodeString Cbor")
        }

        /**
         *
         * The timestamp of ‘validFrom’ shall be equal or later than the ‘signed’ element.
         * The ‘validUntil’ element contains the timestamp after which the mDL data is no longer
         * valid. The value of the timstamp shall be later than the ‘validFrom’ element.
         *
         * **/
        fun build(): ValidityInfo {
            signed?.let { sig ->
                validFrom?.let { vf ->
                    validUntil?.let { vu ->
                        return ValidityInfo(sig, vf, vu, expectedUpdate)
                    }
                }
            }
            throw RuntimeException("Mandatory fields not set")
        }
    }
}