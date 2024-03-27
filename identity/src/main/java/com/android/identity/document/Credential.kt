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
package com.android.identity.document

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.DataItem
import com.android.identity.crypto.CertificateChain
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.SecureArea
import com.android.identity.util.ApplicationData
import com.android.identity.util.Logger
import com.android.identity.util.SimpleApplicationData
import com.android.identity.util.Timestamp

/**
 * A credential.
 *
 * To create an instance of this type, an application must use [Document.createCredential].
 * At this point, the [Credential] is not certified. In order to use the [Credential]
 * for provisioning and presentation, call [Credential.certify] to certify the credential.
 */
class Credential {
    /**
     * The alias for the authentication key associated with this credential.
     *
     * This can be used together with the [SecureArea] returned by [secureArea]
     */
    lateinit var alias: String
        private set

    /**
     * The domain of the credential.
     *
     * This returns the domain set when the credential was created.
     */
    lateinit var domain: String
        private set

    /**
     * How many time the credential in the slot has been used.
     */
    var usageCount = 0
        private set

    /**
     * The issuer-provided data associated with the credential.
     *
     * @throws IllegalStateException if the credential is not yet certified.
     */
    private var _issuerProvidedData: ByteArray? = null
    val issuerProvidedData: ByteArray
        get() = _issuerProvidedData
            ?: throw IllegalStateException("This credential is not yet certified")

    /**
     * The point in time the issuer-provided data is valid from.
     *
     * @throws IllegalStateException if the credential is not yet certified.
     */
    private var _validFrom: Timestamp? = null
    val validFrom: Timestamp
        get() = _validFrom
            ?: throw IllegalStateException("This credential is not yet certified")

    /**
     * The point in time the issuer-provided data is valid until.
     *
     * @throws IllegalStateException if the credential is not yet certified.
     */
    private var _validUntil: Timestamp? = null
    val validUntil: Timestamp
        get() = _validUntil
            ?: throw IllegalStateException("This credential is not yet certified")

    /**
     * Indicates whether the credential has been certified yet.
     */
    var isCertified: Boolean = false
        private set

    private lateinit var privateApplicationData: SimpleApplicationData

    /**
     * Application specific data.
     *
     * Use this object to store additional data an application may want to associate
     * with the credential. Setters and associated getters are
     * enumerated in the [ApplicationData] interface.
     */
    val applicationData: ApplicationData
        get() = privateApplicationData

    /**
     * The credential counter.
     *
     * This is the value of the Document's Credential Counter
     * at the time this credential was created.
     */
    var credentialCounter: Long = 0
        private set

    /**
     * The X.509 certificate chain for the authentication key associated with this credential.
     *
     * The application should send this credential to the issuer which should create issuer-provided
     * data (e.g. an MSO if using ISO/IEC 18013-5:2021) using the credential as the `DeviceKey`.
     */
    val attestation: CertificateChain
        get() = secureArea.getKeyInfo(alias).attestation

    /**
     * The secure area for the authentication key associated with this credential.
     *
     * This can be used together with the alias returned by [alias].
     */
    lateinit var secureArea: SecureArea

    private lateinit var document: Document

    internal var replacementAlias: String? = null
    internal var replacementForAlias: String? = null

    /**
     * Deletes the credential.
     *
     * After deletion, this object should no longer be used.
     */
    fun delete() {
        secureArea.deleteKey(alias)
        document.removeCredential(this)
    }

    /**
     * Increases usage count of the credential.
     */
    fun increaseUsageCount() {
        usageCount += 1
        document.saveDocument()
    }

    fun toCbor(): DataItem {
        val mapBuilder = CborMap.builder()
        mapBuilder.put("alias", alias)
            .put("domain", domain)
            .put("secureAreaIdentifier", secureArea.identifier)
            .put("usageCount", usageCount.toLong())
            .put("isCertified", isCertified)
            .put("applicationData", privateApplicationData.encodeAsCbor())
            .put("credentialCounter", credentialCounter)

        if (replacementForAlias != null) {
            mapBuilder.put("replacementForAlias", replacementForAlias!!)
        }
        if (replacementAlias != null) {
            mapBuilder.put("replacementAlias", replacementAlias!!)
        }

        if (isCertified) {
            mapBuilder.put("data", _issuerProvidedData!!)
                .put("validFrom", _validFrom!!.toEpochMilli())
                .put("validUntil", _validUntil!!.toEpochMilli())
        }

        return mapBuilder.end().build()
    }

    /**
     * The credential that will replace this credential once certified or `null` if no
     * credential is designated to replace this credential.
     */
    val replacement: Credential?
        get() = document.pendingCredentials.firstOrNull { it.alias == replacementAlias }
            .also {
                if (it == null && replacementAlias != null) {
                    Logger.w(
                        TAG, "Pending credential with alias $replacementAlias which " +
                                "is intended to replace this credential does not exist"
                    )
                }
            }

    /**
     * The credential that will be replaced by this credential once it's been certified.
     */
    val replacementFor: Credential?
        get() = document.certifiedCredentials.firstOrNull { it.alias == replacementForAlias }
            .also {
                if (it == null && replacementForAlias != null) {
                    Logger.w(
                        TAG, "Credential with alias $replacementForAlias which " +
                            "is intended to be replaced does not exist"
                    )
                }
            }

    /**
     * Certifies the credential.
     *
     * @param issuerProvidedAuthenticationData the issuer-provided static authentication data.
     * @param validFrom the point in time before which the data is not valid.
     * @param validUntil the point in time after which the data is not valid.
     */
    fun certify(
        issuerProvidedAuthenticationData: ByteArray,
        validFrom: Timestamp,
        validUntil: Timestamp
    ) {
        check(!isCertified) { "Credential is already certified" }
        isCertified = true
        _issuerProvidedData = issuerProvidedAuthenticationData
        _validFrom = validFrom
        _validUntil = validUntil

        replacementFor?.delete()
        replacementForAlias = null

        document.certifyPendingCredential(this)
    }

    companion object {
        const val TAG = "Credential"

        internal fun create(
            alias: String,
            domain: String,
            secureArea: SecureArea,
            createKeySettings: CreateKeySettings,
            asReplacementFor: Credential?,
            document: Document
        ) = Credential().run {
            this.alias = alias
            this.domain = domain
            this.secureArea = secureArea
            this.secureArea.createKey(alias, createKeySettings)
            replacementForAlias = asReplacementFor?.alias
            this.document = document
            privateApplicationData = SimpleApplicationData { document.saveDocument() }
            credentialCounter = document.credentialCounter
            this
        }

        internal fun fromCbor(
            dataItem: DataItem,
            document: Document
        ) = Credential().apply {
            val map = dataItem
            alias = map["alias"].asTstr
            domain = map["domain"].asTstr
            val secureAreaIdentifier = map["secureAreaIdentifier"].asTstr
            secureArea = document.secureAreaRepository.getImplementation(secureAreaIdentifier)
                ?: throw IllegalStateException("Unknown Secure Area $secureAreaIdentifier")
            usageCount = map["usageCount"].asNumber.toInt()
            isCertified = map["isCertified"].asBoolean

            if (isCertified) {
                _issuerProvidedData = map["data"].asBstr
                _validFrom = Timestamp.ofEpochMilli(map["validFrom"].asNumber)
                _validUntil = Timestamp.ofEpochMilli(map["validUntil"].asNumber)
            } else {
                _issuerProvidedData = null
                _validFrom = null
                _validUntil = null
            }

            replacementAlias = map.getOrNull("replacementAlias")?.asTstr
            replacementForAlias = map.getOrNull("replacementForAlias")?.asTstr
            val applicationDataDataItem = map["applicationData"]
            check(applicationDataDataItem is Bstr) { "applicationData not found or not byte[]" }
            this.document = document
            privateApplicationData = SimpleApplicationData
                .decodeFromCbor(applicationDataDataItem.value) {
                    document.saveDocument()
                }
            credentialCounter = map["credentialCounter"].asNumber
        }
    }
}