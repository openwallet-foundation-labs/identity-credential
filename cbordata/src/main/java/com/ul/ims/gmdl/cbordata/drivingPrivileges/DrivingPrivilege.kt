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

package com.ul.ims.gmdl.cbordata.drivingPrivileges

import android.icu.util.Calendar
import android.util.Log
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.MajorType
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import com.ul.ims.gmdl.cbordata.credential.CategoryCodesEnum
import com.ul.ims.gmdl.cbordata.utils.CborUtils
import com.ul.ims.gmdl.cbordata.utils.DateUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DrivingPrivilege private constructor(
    val vehicleCategory : CategoryCodesEnum,
    val issueDate : Calendar?,
    val expiryData : Calendar?) {

    companion object {
        const val VEHICLE_CATEGORY_CODE = "vehicle_category_code"
        const val ISSUE_DATE = "issue_date"
        const val EXPIRY_DATE = "expiry_date"
        const val LOG_TAG = "DrivingPrivilege"
    }

    fun toDataItem(): DataItem {
        val drivingPrivMap = Map()

        // vehicle_category_code
        drivingPrivMap.put(
            UnicodeString(VEHICLE_CATEGORY_CODE),
            UnicodeString(vehicleCategory.toString()))

        //issue_date
        issueDate?.let {iDate ->
            drivingPrivMap.put(
                UnicodeString(ISSUE_DATE),
                CborUtils.dateToUnicodeString(iDate))
        }

        //expiry_date
        expiryData?.let {eDate ->
            drivingPrivMap.put(
                UnicodeString(EXPIRY_DATE),
                CborUtils.dateToUnicodeString(eDate))
        }

        return drivingPrivMap
    }

    fun encode() : ByteArray? {
        val baos = ByteArrayOutputStream()

        return try {
            // Use non Canonical encoder in order to maintain the order in the Cbor Map
            CborEncoder(baos).nonCanonical().encode(toDataItem())

            baos.toByteArray()
        } catch (ex: CborException) {
            Log.e(DrivingPrivileges.TAG, ex.message, ex)
            null
        }
    }

    class Builder {
        private var vehicleCategory = CategoryCodesEnum.B
        private var issueDate : Calendar? = null
        private var expiryData : Calendar? = null

        fun setVehicleCategory(vehicleCategory : CategoryCodesEnum) = apply {
            this.vehicleCategory = vehicleCategory
        }

        fun setIssueDate(date : Calendar) = apply {
            this.issueDate = date
        }

        fun setExpiryDate(date: Calendar) = apply {
            this.expiryData = date
        }

        fun fromCborBytes(encoded : ByteArray?) = apply {
            val bais = ByteArrayInputStream(encoded)
            try {
                val dataItems = CborDecoder(bais).decode()
                if (dataItems.isNotEmpty()) {
                    if (dataItems[0].majorType == MajorType.MAP) {
                        fromCborDataItem(dataItems[0] as Map)
                    }
                }
            } catch (ex: CborException) {
                Log.e(LOG_TAG, ex.message, ex)
            }
        }

        fun fromCborDataItem(drivingPriv : DataItem) = apply {
            if (drivingPriv.majorType == MajorType.MAP) {
                val drivingPriviMap = drivingPriv as Map
                drivingPriviMap.keys.forEach {key ->
                    if (key.majorType == MajorType.UNICODE_STRING) {
                        val value = drivingPriviMap.get(key)
                        when ((key as UnicodeString).string) {
                            VEHICLE_CATEGORY_CODE -> {
                                if (value.majorType == MajorType.UNICODE_STRING) {
                                    vehicleCategory = CategoryCodesEnum.valueOf(
                                        (value as UnicodeString).string)
                                }
                            }
                            ISSUE_DATE -> {
                                issueDate = decodeCborDate(value)
                            }
                            EXPIRY_DATE -> {
                                expiryData = decodeCborDate(value)
                            }
                        }
                    } else {
                        Log.e(LOG_TAG, "Driving Privilege map must have keys as " +
                                "Unicode String")
                    }
                }
            } else {
                Log.e(LOG_TAG, "Driving Privileges sub item must be a cbor map")
            }
        }

        private fun decodeCborDate(value : DataItem) : Calendar? {
            if (value.majorType == MajorType.UNICODE_STRING) {
                return DateUtils.getCalendarDate(
                    (value as UnicodeString).string,
                    DateUtils.RFC3339_FORMAT_FULL_DATE
                )
            }

            return null
        }

        fun build() : DrivingPrivilege {
            return DrivingPrivilege(
                vehicleCategory,
                issueDate,
                expiryData
            )
        }
    }
}