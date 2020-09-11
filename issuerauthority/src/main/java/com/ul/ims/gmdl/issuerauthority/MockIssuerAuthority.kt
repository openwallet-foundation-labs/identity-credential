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

package com.ul.ims.gmdl.issuerauthority

import android.content.Context
import android.util.Base64
import android.util.Log
import com.ul.ims.gmdl.cbordata.MdlDataIdentifiers
import com.ul.ims.gmdl.cbordata.cryptoUtils.CryptoUtils
import com.ul.ims.gmdl.cbordata.cryptoUtils.HashAlgorithms
import com.ul.ims.gmdl.cbordata.doctype.MdlDoctype
import com.ul.ims.gmdl.cbordata.model.UserCredential
import com.ul.ims.gmdl.cbordata.namespace.MdlNamespace
import com.ul.ims.gmdl.cbordata.response.IssuerSignedItem
import com.ul.ims.gmdl.cbordata.security.CoseKey
import com.ul.ims.gmdl.cbordata.security.CoseSign1
import com.ul.ims.gmdl.cbordata.security.IssuerNameSpaces
import com.ul.ims.gmdl.cbordata.security.SigStructure
import com.ul.ims.gmdl.cbordata.security.mso.MobileSecurityObject
import com.ul.ims.gmdl.cbordata.security.mso.ValidityInfo
import com.ul.ims.gmdl.cbordata.security.namespace.MsoMdlNamespace
import com.ul.ims.gmdl.cbordata.utils.BitmapUtils
import com.ul.ims.gmdl.cbordata.utils.CborUtils
import com.ul.ims.gmdl.cbordata.utils.DateUtils
import com.ul.ims.gmdl.issuerauthority.certificate.Certificate
import com.ul.ims.gmdl.issuerauthority.certificate.IACACertificate
import com.ul.ims.gmdl.issuerauthority.data.Credential
import com.ul.ims.gmdl.issuerauthority.data.CredentialRepository
import com.ul.ims.gmdl.issuerauthority.data.IssuerAuthorityDatabase
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import kotlin.random.Random

class MockIssuerAuthority private constructor(val context: Context) : IIssuerAuthority {

    companion object {
        const val LOG_TAG = "MockIssuerAuthority"
        const val PROVISION_CHALLENGE = "MockIssuerAuthorityChallenge"

        @Volatile
        private var INSTANCE: MockIssuerAuthority? = null

        fun getInstance(context: Context): MockIssuerAuthority =
            INSTANCE
                ?: synchronized(this) {
                    INSTANCE
                        ?: MockIssuerAuthority(
                            context
                        ).also {
                            INSTANCE = it
                        }
                }
    }

    private val dateSigned = DateUtils.getTimeOfLastUpdate()
    private var userCredential = UserCredential.Builder()
        .useStaticData(context.resources, dateSigned)
        .build()
    private val expectedAlg = byteArrayOf(
        0x06.toByte(),
        0x08.toByte(),
        0x2A.toByte(),
        0x86.toByte(),
        0x48.toByte(),
        0xCE.toByte(),
        0x3D.toByte(),
        0x04.toByte(),
        0x03.toByte(),
        0x02.toByte()
    )

    private val privateKey = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgWLDnTyrsk6GmK7zD" +
            "ASOTycHAhpPHI/XwhW7kAh75Qx6hRANCAAQzNsegde6jgOSGAob414kiB+WOeXvE" +
            "di9MgL1LdAMDzVKIj1wR29IlQzfVFole2tM3TnIiOWZ2PcYoaAibXR/E"


    private lateinit var credentialRepository: CredentialRepository

    init {
        createRepository()
    }

    private fun createRepository() = runBlocking {
        credentialRepository = CredentialRepository.getInstance(
            IssuerAuthorityDatabase.getInstance(context.applicationContext).credentialDao()
        )
    }

    private fun generateIssuerSignedItemsList(): IssuerNameSpaces {
        val builder = IssuerNameSpaces.Builder()

        // Create a list with the possible digest ids and shuffle it
        val digestIds = mutableListOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
        digestIds.shuffle()

        val list = mutableListOf<IssuerSignedItem>()
        val familyNameIsiBuilder = IssuerSignedItem.Builder()
        val givenNamesIsiBuilder = IssuerSignedItem.Builder()
        val dateOfBirthIsiBuilder = IssuerSignedItem.Builder()
        val dateOfIssueIsiBuilder = IssuerSignedItem.Builder()
        val dateOfExpiryIsiBuilder = IssuerSignedItem.Builder()
        val issuingCountryIsiBuilder = IssuerSignedItem.Builder()
        val issuingAuthorityIsiBuilder = IssuerSignedItem.Builder()
        val licenseNumberIsiBuilder = IssuerSignedItem.Builder()
        val categoriesOfVehiclesIsiBuilder = IssuerSignedItem.Builder()
        val portraitIsiBuilder = IssuerSignedItem.Builder()
        val ageOver18 = IssuerSignedItem.Builder()
        val ageOver21 = IssuerSignedItem.Builder()

        //family name IssuerSignedItem
        familyNameIsiBuilder.setDigestId(digestIds[0])
            .setRandomValue(randomValue())
            .setElementIdentifier(MdlDataIdentifiers.FAMILY_NAME.identifier)
        userCredential.familyName?.let {
            familyNameIsiBuilder.setElementValue(it)
        }
        list.add(familyNameIsiBuilder.build())

        //given names IssuerSignedItem
        givenNamesIsiBuilder.setDigestId(digestIds[1])
            .setRandomValue(randomValue())
            .setElementIdentifier(MdlDataIdentifiers.GIVEN_NAMES.identifier)
        userCredential.givenNames?.let {
            givenNamesIsiBuilder.setElementValue(it)
        }
        list.add(givenNamesIsiBuilder.build())

        //date of birth IssuerSignedItem
        dateOfBirthIsiBuilder.setDigestId(digestIds[2])
            .setRandomValue(randomValue())
            .setElementIdentifier(MdlDataIdentifiers.DATE_OF_BIRTH.identifier)
        userCredential.dateOfBirth?.let {
            dateOfBirthIsiBuilder.setElementValue(it)
        }
        list.add(dateOfBirthIsiBuilder.build())

        //date of issue IssuerSignedItem
        dateOfIssueIsiBuilder.setDigestId(digestIds[3])
            .setRandomValue(randomValue())
            .setElementIdentifier(MdlDataIdentifiers.DATE_OF_ISSUE.identifier)
        userCredential.dateOfIssue?.let {
            dateOfIssueIsiBuilder.setElementValue(it)
        }
        list.add(dateOfIssueIsiBuilder.build())

        //date of expiry IssuerSignedItem
        dateOfExpiryIsiBuilder.setDigestId(digestIds[4])
            .setRandomValue(randomValue())
            .setElementIdentifier(MdlDataIdentifiers.DATE_OF_EXPIRY.identifier)
        userCredential.dateOfExpiry?.let {
            dateOfExpiryIsiBuilder.setElementValue(it)
        }
        list.add(dateOfExpiryIsiBuilder.build())

        //issuing country IssuerSignedItem
        issuingCountryIsiBuilder.setDigestId(digestIds[5])
            .setRandomValue(randomValue())
            .setElementIdentifier(MdlDataIdentifiers.ISSUING_COUNTRY.identifier)
        userCredential.issuingCountry?.let {
            issuingCountryIsiBuilder.setElementValue(it)
        }
        list.add(issuingCountryIsiBuilder.build())

        //issuing authority IssuerSignedItem
        issuingAuthorityIsiBuilder.setDigestId(digestIds[6])
            .setRandomValue(randomValue())
            .setElementIdentifier(MdlDataIdentifiers.ISSUING_AUTHORITY.identifier)
        userCredential.issuingAuthority?.let {
            issuingAuthorityIsiBuilder.setElementValue(it)
        }
        list.add(issuingAuthorityIsiBuilder.build())

        //license number IssuerSignedItem
        licenseNumberIsiBuilder.setDigestId(digestIds[7])
            .setRandomValue(randomValue())
            .setElementIdentifier(MdlDataIdentifiers.LICENSE_NUMBER.identifier)
        userCredential.licenseNumber?.let {
            licenseNumberIsiBuilder.setElementValue(it)
        }
        list.add(licenseNumberIsiBuilder.build())

        //categories Of Vehicles IssuerSignedItem
        categoriesOfVehiclesIsiBuilder.setDigestId(digestIds[8])
            .setRandomValue(randomValue())
            .setElementIdentifier(MdlDataIdentifiers.CATEGORIES_OF_VEHICLES.identifier)
        userCredential.categoriesOfVehicles?.let {
            categoriesOfVehiclesIsiBuilder.setElementValue(it)
        }
        list.add(categoriesOfVehiclesIsiBuilder.build())

        //image IssuerSignedItem
        portraitIsiBuilder
            .setDigestId(digestIds[9])
            .setRandomValue(randomValue())
            .setElementIdentifier(MdlDataIdentifiers.PORTRAIT_OF_HOLDER.identifier)

        val image = BitmapUtils.encodeBitmap(userCredential.portraitOfHolder)
        image?.let {
            portraitIsiBuilder
                .setElementValue(it)
        }
        list.add(portraitIsiBuilder.build())

        // age over 18
        ageOver18.setDigestId(digestIds[10])
            .setRandomValue(randomValue())
            .setElementIdentifier(MdlDataIdentifiers.AGE_OVER_18.identifier)
        userCredential.ageOver18?.let {
            ageOver18.setElementValue(it)
        }
        list.add(ageOver18.build())

        // age over 21
        ageOver21.setDigestId(digestIds[11])
            .setRandomValue(randomValue())
            .setElementIdentifier(MdlDataIdentifiers.AGE_OVER_21.identifier)
        userCredential.ageOver21?.let {
            ageOver21.setElementValue(it)
        }
        list.add(ageOver21.build())

        // list must be sorted by digest id
        list.sortedBy { it.digestId }

        builder.setNamespace(MdlNamespace.namespace, list)

        return builder.build()
    }

    private fun randomValue(): ByteArray {
        return Random.nextBytes(16)
    }

    override fun getProvisionChallenge(): ByteArray {
        return PROVISION_CHALLENGE.toByteArray()
    }

    override suspend fun getIssuerSignedData(publicKey: PublicKey): ByteArray? {
        // Generate a list of Issuer Signed Items
        val issuerNameSpaces = generateIssuerSignedItemsList()
        val issuerSignedItemsList = issuerNameSpaces.nameSpaces[MdlNamespace.namespace]

        // Generate the Mobile Security Object
        val encodedMso = issuerSignedItemsList?.let {
            generateMsoForPublicKey(it, publicKey)
        }

        // Sign the MSO
        val coseSign1 = encodedMso?.let {
            signMso(it)
        }

        // Store the list of Issuer Signed Items with the hash of its signature
        coseSign1?.let { cSign1 ->
            val hashByteArray = CryptoUtils.generateHash(HashAlgorithms.SHA_256, cSign1.encode())
            hashByteArray?.let { hashBA ->
                credentialRepository.insert(Credential(hashBA, issuerNameSpaces.encode()))
            }
        }

        return coseSign1?.encode()
    }

    override suspend fun getIssuerNamespaces(issuerAuth: ByteArray): IssuerNameSpaces? {
        // Calculate the hash of the signature
        val hashByteArray = CryptoUtils.generateHash(HashAlgorithms.SHA_256, issuerAuth)

        // Retrieve the IssuerNamespaces from storage
        val issuerNSByteArray = hashByteArray?.let { hashBA ->
            credentialRepository.loadById(hashBA)
        }

        issuerNSByteArray?.let {
            return IssuerNameSpaces.Builder().decode(it).build()
        }

        return null
    }

    override fun getCredentials(): UserCredential? {
        return userCredential
    }

    private fun signMso(encodedMso: ByteArray): CoseSign1? {
        // Create a SigStructure
        val sigStruct = SigStructure.Builder()
            .setAlg(expectedAlg)
            .setPayloadData(encodedMso)
            .build()
        val encodedSig = sigStruct.encode()

        // load private key
        val privateKeyBytes = Base64.decode(privateKey, Base64.DEFAULT)
        val privateKey = CryptoUtils.decodePrivateKey(privateKeyBytes)

        // sign encodedSig
        privateKey?.let { pk ->
            val signedEncodedSig = CryptoUtils.signData(pk, encodedSig)
            signedEncodedSig?.let { sigStructure ->
                // Extract R, S from the Signature in order to convert it to Envelop
                val rs = CryptoUtils.extractRandSFromSignature(sigStructure)

                rs?.let { signature ->
                    // load ds certificate = ds.der
                    val certificate = loadCertificate(context)

                    certificate?.let { cert ->
                        val encodedCertificate = cert.certificate?.encoded

                        encodedCertificate?.let { encCert ->
                            // Create COSE_Sign1 Obj
                            val issuerAuth = CoseSign1.Builder()
                                .setAlgo(HashAlgorithms.SHA_256)
                                .setDsCertificateBytes(encCert)
                                .setPayload(encodedMso)
                                .setSignature(signature)
                                .setToBeSignedSigStructure(encodedSig)
                                .build()

                            issuerAuth.let {
                                Log.d(LOG_TAG, "issuerAuth Cbor Structure")
                                Log.d(LOG_TAG, CborUtils.cborPrettyPrint(it.encode()))
                                Log.d(LOG_TAG, it.encodeToString())
                            }

                            return issuerAuth
                        }
                    }
                }
            }
        }
        return null
    }

    private fun generateMsoForPublicKey(
        issuerSignedItemsList: List<IssuerSignedItem>,
        publicKey: PublicKey
    ): ByteArray? {
        val calculatedHashes = HashMap<Int, ByteArray>()

        // Calculate the hash of each IssuerSignedItem
        issuerSignedItemsList.forEach { isi ->

            Log.d(LOG_TAG, "IssuerSignedItem = ${isi.elementIdentifier}")
            Log.d(LOG_TAG, "Digest Id = ${isi.digestId}")
            Log.d(LOG_TAG, "Cbor Encoded = ${isi.encodeToString()}")
            val hash = CryptoUtils.generateHash(HashAlgorithms.SHA_256, isi.encode())
            hash?.let {
                Log.d(LOG_TAG, "Calculated Hash = " + encodeToString(it))
                calculatedHashes[isi.digestId] = it
            }
        }

        // Create a Cose_Key with the Device Key
        val holderDeviceKey = publicKey as? ECPublicKey
        var coseKey: CoseKey? = null

        holderDeviceKey?.let {
            val holderKeyBuilder = CoseKey.Builder()
            val curveId = 1

            val xco = CryptoUtils.toByteArrayUnsigned(it.w.affineX)
            val yco = CryptoUtils.toByteArrayUnsigned(it.w.affineY)
            holderKeyBuilder.setKeyType(2)
            holderKeyBuilder.setCurve(curveId, xco, yco, null)
            coseKey = holderKeyBuilder.build()
        }

        coseKey?.let { cKey ->
            // Create a MsoMdlNamespace Obj
            val msoMdlNamespace = MsoMdlNamespace(MdlNamespace.namespace, calculatedHashes)

            // Create Validity Info
            val validityInfo = ValidityInfo.Builder()
                .setSigned(dateSigned)
                .setValidFrom(dateSigned)
                .setValidUntil(DateUtils.getDateOfExpiry())
                .build()

            // Create the MSO Object
            val mSecurityObject = MobileSecurityObject.Builder()
                .setDigestAlgorithm(HashAlgorithms.SHA_256)
                .setDocumentType(MdlDoctype.docType)
                .setListOfNameSpaces(listOf(msoMdlNamespace))
                .setDeviceKey(cKey)
                .setValidityInfo(validityInfo)
                .build()

            Log.d(LOG_TAG, "Generated MSO")
            Log.d(LOG_TAG, mSecurityObject?.encodeToString() ?: "MSO null")

            return mSecurityObject?.encode()
        }
        return null
    }

    // Helper function to display a cbor structure in HEX
    fun encodeToString(encoded: ByteArray): String {
        val sb = StringBuilder(encoded.size * 2)

        for (b in encoded) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun loadCertificate(context: Context): Certificate? {
        return try {
            val certInputStream = context.assets.open("ds.der")

            IACACertificate(certInputStream)
        } catch (ex: IOException) {
            Log.e(LOG_TAG, ex.message, ex)
            null
        }
    }
}