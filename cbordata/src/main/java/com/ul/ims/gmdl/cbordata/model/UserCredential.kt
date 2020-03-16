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

package com.ul.ims.gmdl.cbordata.model

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.icu.util.Calendar
import android.os.Parcelable
import androidx.security.identity.EntryNamespace
import androidx.security.identity.ResultNamespace
import com.ul.ims.gmdl.cbordata.MdlDataIdentifiers
import com.ul.ims.gmdl.cbordata.R
import com.ul.ims.gmdl.cbordata.doctype.MdlDoctype
import com.ul.ims.gmdl.cbordata.drivingPrivileges.DrivingPrivileges
import com.ul.ims.gmdl.cbordata.response.BleTransferResponse
import com.ul.ims.gmdl.cbordata.utils.BitmapUtils
import com.ul.ims.gmdl.cbordata.utils.DateUtils
import kotlinx.android.parcel.Parcelize

@Parcelize
class UserCredential private constructor(
    val familyName: String?,
    val givenNames: String?,
    val dateOfBirth: Calendar?,
    val dateOfIssue: Calendar?,
    val dateOfExpiry: Calendar?,
    val issuingCountry: String?,
    val issuingAuthority: String?,
    val licenseNumber: String?,
    val categoriesOfVehicles: DrivingPrivileges?,
    val portraitOfHolder: Bitmap?,
    val issuerDataAuthentication : Boolean?,
    val deviceSign : Boolean?
) : Parcelable {

    companion object {
        const val CREDENTIAL_NAME = "mdlUserCredential"
    }

    fun getCredentialsForProvisioning(accessControlProfileIds: Collection<Int>): EntryNamespace {
        val credentialsBuilder = EntryNamespace.Builder(MdlDoctype.docType)
        familyName?.let {
            credentialsBuilder.addStringEntry(
                MdlDataIdentifiers.FAMILY_NAME.identifier,
                accessControlProfileIds,
                it
            )
        }
        givenNames?.let {
            credentialsBuilder.addStringEntry(
                MdlDataIdentifiers.GIVEN_NAMES.identifier,
                accessControlProfileIds,
                it
            )
        }
        dateOfBirth?.let {
            credentialsBuilder.addDateTimeEntry(
                MdlDataIdentifiers.DATE_OF_BIRTH.identifier,
                accessControlProfileIds,
                it
            )
        }
        dateOfIssue?.let {
            credentialsBuilder.addDateTimeEntry(
                MdlDataIdentifiers.DATE_OF_ISSUE.identifier,
                accessControlProfileIds,
                it
            )
        }
        dateOfExpiry?.let {
            credentialsBuilder.addDateTimeEntry(
                MdlDataIdentifiers.DATE_OF_EXPIRY.identifier,
                accessControlProfileIds,
                it
            )
        }
        issuingCountry?.let {
            credentialsBuilder.addStringEntry(
                MdlDataIdentifiers.ISSUING_COUNTRY.identifier,
                accessControlProfileIds,
                it
            )
        }
        issuingAuthority?.let {
            credentialsBuilder.addStringEntry(
                MdlDataIdentifiers.ISSUING_AUTHORITY.identifier,
                accessControlProfileIds,
                it
            )
        }
        licenseNumber?.let {
            credentialsBuilder.addStringEntry(
                MdlDataIdentifiers.LICENSE_NUMBER.identifier,
                accessControlProfileIds,
                it
            )
        }
        categoriesOfVehicles?.let {
            credentialsBuilder.addEntry(
                MdlDataIdentifiers.CATEGORIES_OF_VEHICLES.identifier,
                accessControlProfileIds,
                it.encode()
            )
        }
        BitmapUtils.encodeBitmap(portraitOfHolder)?.let {
            credentialsBuilder.addBytestringEntry(
                MdlDataIdentifiers.PORTRAIT_OF_HOLDER.identifier,
                accessControlProfileIds,
                it
            )
        }

        return credentialsBuilder.build()
    }

    class Builder {
        private var familyName: String? = null
        private var givenNames: String? = null
        private var dateOfBirth: Calendar? = null
        private var dateOfIssue: Calendar? = null
        private var dateOfExpiry: Calendar? = null
        private var issuingCountry: String? = null
        private var issuingAuthority: String? = null
        private var licenseNumber: String? = null
        private var categoriesOfVehicles: DrivingPrivileges? = null
        private var portraitOfHolder: Bitmap? = null
        private var issuerDataAuthentication : Boolean? = null
        private var deviceSign : Boolean? = null

        fun useStaticData(res: Resources) = apply {
            familyName = "do Nascimento"
            givenNames = "Edson Arantes"
            dateOfBirth = DateUtils.getDateOfBirth()
            dateOfIssue = DateUtils.getDateOfIssue()
            dateOfExpiry = DateUtils.getDateOfExpiry()
            issuingCountry = "US"
            issuingAuthority = "Google"
            licenseNumber = "5094962111"
            categoriesOfVehicles = DrivingPrivileges.Builder().useHardcodedData().build()

            val portrait = BitmapUtils.decodeBitmapResource(res, R.drawable.img_pele_portrait)
            portraitOfHolder = portrait
        }

        fun fromResultNamespace(resultNamespace: ResultNamespace) = apply {
            familyName = resultNamespace.getStringEntry(MdlDataIdentifiers.FAMILY_NAME.identifier)
            givenNames = resultNamespace.getStringEntry(MdlDataIdentifiers.GIVEN_NAMES.identifier)
            dateOfBirth =
                resultNamespace.getDateTimeEntry(MdlDataIdentifiers.DATE_OF_BIRTH.identifier)
            dateOfIssue =
                resultNamespace.getDateTimeEntry(MdlDataIdentifiers.DATE_OF_ISSUE.identifier)
            dateOfExpiry =
                resultNamespace.getDateTimeEntry(MdlDataIdentifiers.DATE_OF_EXPIRY.identifier)
            issuingCountry = resultNamespace.getStringEntry(MdlDataIdentifiers.ISSUING_COUNTRY.identifier)
            issuingAuthority = resultNamespace.getStringEntry(MdlDataIdentifiers.ISSUING_AUTHORITY.identifier)
            licenseNumber = resultNamespace.getStringEntry(MdlDataIdentifiers.LICENSE_NUMBER.identifier)
            resultNamespace.getEntry(MdlDataIdentifiers.CATEGORIES_OF_VEHICLES.identifier)?.let {
                categoriesOfVehicles = DrivingPrivileges.Builder().fromCborBytes(it).build()
            }
            val portrait = resultNamespace.getBytestringEntry(MdlDataIdentifiers.PORTRAIT_OF_HOLDER.identifier)
            portrait?.let {
                portraitOfHolder = BitmapFactory.decodeByteArray(it, 0, it.size)
            }

        }

        fun setIssuerDataAuthentication(status : Boolean?) = apply {
            this.issuerDataAuthentication = status
        }

        fun setDeviceSign(status : Boolean?) = apply {
            this.deviceSign = status
        }

        // Convert a structure received as part of a BLE transfer into a UserCredential Obj needed to display the
        // credentials
        fun fromBleTransferResponse(response: BleTransferResponse) = apply {
            val items = response.arrIssuerSignedItem
            items?.forEach {
                when (it.elementIdentifier) {
                    MdlDataIdentifiers.FAMILY_NAME.identifier -> {
                        val elementValue = it.elementValue as? String
                        elementValue?.let { element ->
                            familyName = element
                        }
                    }

                    MdlDataIdentifiers.GIVEN_NAMES.identifier -> {
                        val elementValue = it.elementValue as? String
                        elementValue?.let { element ->
                            givenNames = element
                        }
                    }

                    MdlDataIdentifiers.DATE_OF_BIRTH.identifier -> {
                        val elementValue = it.elementValue as? Calendar
                        elementValue?.let { element ->
                            dateOfBirth = element
                        }
                    }

                    MdlDataIdentifiers.DATE_OF_ISSUE.identifier -> {
                        val elementValue = it.elementValue as? Calendar
                        elementValue?.let { element ->
                            dateOfIssue = element
                        }
                    }

                    MdlDataIdentifiers.DATE_OF_EXPIRY.identifier -> {
                        val elementValue = it.elementValue as? Calendar
                        elementValue?.let { element ->
                            dateOfExpiry = element
                        }
                    }

                    MdlDataIdentifiers.ISSUING_COUNTRY.identifier -> {
                        val elementValue = it.elementValue as? String
                        elementValue?.let { element ->
                            issuingCountry = element
                        }
                    }

                    MdlDataIdentifiers.ISSUING_AUTHORITY.identifier -> {
                        val elementValue = it.elementValue as? String
                        elementValue?.let { element ->
                            issuingAuthority = element
                        }
                    }

                    MdlDataIdentifiers.LICENSE_NUMBER.identifier -> {
                        val elementValue = it.elementValue as? String
                        elementValue?.let { element ->
                            licenseNumber = element
                        }
                    }

                    MdlDataIdentifiers.CATEGORIES_OF_VEHICLES.identifier -> {
                        val elementValue = it.elementValue as? DrivingPrivileges
                        elementValue?.let { element ->
                            categoriesOfVehicles = element
                        }
                    }

                    MdlDataIdentifiers.PORTRAIT_OF_HOLDER.identifier -> {
                        val elementValue = it.elementValue as? ByteArray
                        elementValue?.let { element ->
                            portraitOfHolder = BitmapUtils.decodeBitmapBytes(element)
                        }
                    }
                }
            }
        }

        fun build(): UserCredential {
            return UserCredential(
                familyName,
                givenNames,
                dateOfBirth,
                dateOfIssue,
                dateOfExpiry,
                issuingCountry,
                issuingAuthority,
                licenseNumber,
                categoriesOfVehicles,
                portraitOfHolder,
                issuerDataAuthentication,
                deviceSign
            )
        }
    }
}