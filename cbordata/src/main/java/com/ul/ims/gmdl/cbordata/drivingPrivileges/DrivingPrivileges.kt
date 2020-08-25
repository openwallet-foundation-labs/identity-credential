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

import android.util.Log
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.MajorType
import com.ul.ims.gmdl.cbordata.credential.CategoryCodesEnum
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.utils.DateUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*

class DrivingPrivileges private constructor(
    val drivingPrivileges : LinkedList<DrivingPrivilege>
) : AbstractCborStructure() {

    companion object {
        const val TAG = "DrivingPrivileges"
        const val DRIVING_PRIVILEGES = "driving_privileges"
    }

    override fun encode() : ByteArray {
        val baos = ByteArrayOutputStream()
        val drivingPrivArray = toCborDataItem()

        return try {
            // Use non Canonical encoder in order to maintain the order in the Cbor Map
            CborEncoder(baos).encode(drivingPrivArray)

            baos.toByteArray()
        } catch (ex: CborException) {
            Log.e(TAG, ex.message, ex)
            byteArrayOf()
        }
    }

    fun toCborDataItem() : DataItem {
        val cborDataItemArr = Array()

        drivingPrivileges.forEach { drivingPriv->
            cborDataItemArr.add(drivingPriv.toDataItem())
        }

        return cborDataItemArr
    }

    class Builder {
        // Using LinkedList to preserve the insertion order
        private var drivingPrivileges = LinkedList<DrivingPrivilege>()

        fun fromCborBytes(cborBytes : ByteArray) = apply {
            val bais = ByteArrayInputStream(cborBytes)

            try {
                val decoded = CborDecoder(bais).decode()
                decodeCborDataItem(decoded[0])
            } catch (ex: CborException) {
                Log.e(TAG, ex.message, ex)
            }
        }

        fun fromCborDataItem(dpDataItem : DataItem) = apply {
            decodeCborDataItem(dpDataItem)
        }

        fun addDrivingPrivilege(drivingPrivilege: DrivingPrivilege) = apply {
            drivingPrivileges.add(drivingPrivilege)
        }

        fun useHardcodedData() = apply {
            var drivingPrivilege =
                DrivingPrivilege.Builder()
                    .setVehicleCategory(CategoryCodesEnum.A)
                    .setExpiryDate(DateUtils.genExpiryDate())
                    .setIssueDate(DateUtils.genIssueDate())
                    .build()
            drivingPrivileges.add(drivingPrivilege)

            drivingPrivilege =
                DrivingPrivilege.Builder()
                    .setVehicleCategory(CategoryCodesEnum.B)
                    .setExpiryDate(DateUtils.genExpiryDate())
                    .build()
            drivingPrivileges.add(drivingPrivilege)

            drivingPrivilege =
                DrivingPrivilege.Builder()
                    .setVehicleCategory(CategoryCodesEnum.C)
                    .setIssueDate(DateUtils.genIssueDate())
                    .build()
            drivingPrivileges.add(drivingPrivilege)

            drivingPrivilege =
                DrivingPrivilege.Builder()
                    .setVehicleCategory(CategoryCodesEnum.D)
                    .build()
            drivingPrivileges.add(drivingPrivilege)
        }

        private fun decodeCborDataItem(dpDataItem : DataItem) {
            if (dpDataItem.majorType == MajorType.ARRAY) {
                val dpArray = dpDataItem as Array
                dpArray.dataItems.forEach {dp ->
                    drivingPrivileges.add(DrivingPrivilege.Builder()
                        .fromCborDataItem(dp)
                        .build()
                    )
                }
            } else {
                Log.e(TAG, "driving_privileges must be a cbor array")
            }
        }

        fun build() : DrivingPrivileges {
            return DrivingPrivileges(
                drivingPrivileges
            )
        }
    }
}