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
package com.android.identity.credential

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.CborBuilder
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.MapBuilder
import com.android.identity.document.Document
import com.android.identity.document.DocumentStore
import com.android.identity.util.ApplicationData
import com.android.identity.util.Logger
import com.android.identity.util.SimpleApplicationData
import com.android.identity.util.Timestamp

/**
 * Base class for credentials.
 *
 * When a [Credential] is created, it is not certified. In order to use the [Credential]
 * for provisioning and presentation, call [Credential.certify] to certify the credential.
 *
 * When an application creates a new implementation of [Credential], it can be used by [Document]
 * by adding the new [Credential] implementation to a [CredentialFactory] (using the
 * [CredentialFactory.addCredentialImplementation] method) which is passed into [DocumentStore].
 *
 * Each concrete implementation should have a companion object with the following method to
 * generate a new credential instance of that type:
 * + fromCbor(dataItem: DataItem, document: Document): Credential
 *
 * For example, [SecureAreaBoundCredential] contains [SecureAreaBoundCredential.Companion.fromCbor]
 * which utilizes [deserialize]
 */
open class Credential protected constructor() {

    /**
     * Creates a new [Credential]
     *
     * @param asReplacementFor the credential this credential will replace, if not null
     * @param domain the domain of the credential
     */
    constructor(
        asReplacementFor: Credential?,
        domain: String
    ) : this() {
        Credential.apply { create(asReplacementFor, domain) }
    }

    companion object {
        const val TAG = "Credential"

        /**
         * Deserializes a credential for a particular document.
         *
         * @param dataItem the serialized credential.
         * @param document the document associated with the credential.
         * @return the credential object.
         */
        fun fromCbor(
            dataItem: DataItem,
            document: Document
        ) = Credential().apply { deserialize(dataItem, document) }
    }

    /**
     * A stable identifier for the Credential instance.
     */
    var identifier: String = ""
        protected set

    /**
     * The domain of the credential.
     *
     * This returns the domain set when the credential was created.
     */
    lateinit var domain: String
        protected set

    /**
     * How many times the credential in the slot has been used.
     */
    var usageCount = 0
        protected set

    /**
     * Indicates whether the credential has been certified yet.
     */
    var isCertified = false
        protected set

    /**
     * Indicates whether the credential has been invalidated.
     */
    open val isInvalidated: Boolean = false

    /**
     * Application specific data.
     *
     * Use this object to store additional data an application may want to associate
     * with the credential. Setters and associated getters are
     * enumerated in the [ApplicationData] interface.
     */
    val applicationData: ApplicationData
        get() = _applicationData
    private var _applicationData: SimpleApplicationData = SimpleApplicationData {  }

    /**
     * The issuer-provided data associated with the credential.
     *
     * @throws IllegalStateException if the credential is not yet certified.
     */
    val issuerProvidedData: ByteArray
        get() = _issuerProvidedData
            ?: throw IllegalStateException("This credential is not yet certified")
    private var _issuerProvidedData: ByteArray? = null

    /**
     * The point in time the issuer-provided data is valid from.
     *
     * @throws IllegalStateException if the credential is not yet certified.
     */
    val validFrom: Timestamp
        get() = _validFrom
            ?: throw IllegalStateException("This credential is not yet certified")
    private var _validFrom: Timestamp? = null

    /**
     * The point in time the issuer-provided data is valid until.
     *
     * @throws IllegalStateException if the credential is not yet certified.
     */
    val validUntil: Timestamp
        get() = _validUntil
            ?: throw IllegalStateException("This credential is not yet certified")
    private var _validUntil: Timestamp? = null

    /**
     * The credential counter.
     *
     * This is the value of the Document's Credential Counter
     * at the time this credential was created.
     */
    var credentialCounter: Long = 0
        protected set

    internal var document: Document? = null
        set(value) {
            field = value
            credentialCounter = document!!.credentialCounter
            _applicationData = SimpleApplicationData { document!!.saveDocument() }
            identifier = document!!.name + "_credential_" + credentialCounter
            replacementFor?.replacementIdentifier = this.identifier
        }
    internal var replacementIdentifier: String? = null
    var replacementForIdentifier: String? = null

    /**
     * The credential that will be replaced by this credential once it's been certified.
     */
    val replacementFor: Credential?
        get() = document!!.certifiedCredentials?.firstOrNull { it.identifier == replacementForIdentifier }
            .also {
                if (it == null && replacementForIdentifier != null) {
                    Logger.w(
                        TAG, "Credential with alias $replacementForIdentifier which " +
                                "is intended to be replaced does not exist"
                    )
                }
            }

    /**
     * The credential that will replace this credential once certified or `null` if no
     * credential is designated to replace this credential.
     */
    val replacement: Credential?
        get() = document?.pendingCredentials?.firstOrNull { it.identifier == replacementIdentifier }
            .also {
                if (it == null && replacementIdentifier != null) {
                    Logger.w(
                        TAG, "Pending credential with identifier $replacementIdentifier which " +
                                "is intended to replace this credential does not exist"
                    )
                }
            }

    /**
     * Deletes the credential.
     *
     * After deletion, this object should no longer be used.
     */
    open fun delete() {
        document?.removeCredential(this)
    }

    /**
     * Increases usage count of the credential.
     */
    fun increaseUsageCount() {
        usageCount += 1
        document?.saveDocument()
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
        check(document != null) { "Credential is not associated with a document" }
        isCertified = true
        _issuerProvidedData = issuerProvidedAuthenticationData
        _validFrom = validFrom
        _validUntil = validUntil

        replacementFor?.delete()
        replacementForIdentifier = null

        document!!.certifyPendingCredential(this)
    }

    /**
     * Utility function for when a credential is created for the first time. Called by [Credential]
     * constructor and constructors of inheriting classes.
     *
     * @param asReplacementFor the credential this credential will replace, if not null
     * @param domain the domain of the credential
     * @return the credential object.
     */
    protected fun create(
        asReplacementFor: Credential?,
        domain: String
    ): Credential {
        check(asReplacementFor?.replacement == null) {
            "The given credential already has an existing pending credential intending to replace it"
        }
        replacementForIdentifier = asReplacementFor?.identifier
        this.domain = domain
        return this
    }

    /**
     * Used by [Credential] implementation to add any additional information to the mapBuilder.
     *
     * Called by [toCbor].
     *
     * @param mapBuilder the mapBuilder which can be modified before being returned by [toCbor]
     */
    open fun addSerializedData(mapBuilder: MapBuilder<CborBuilder>) {}

    /**
     * Serializes the credential.
     *
     * @return a [DataItem] with all of the credential information.
     */
    fun toCbor(): DataItem {
        val mapBuilder = CborMap.builder()
        mapBuilder.put("identifier", identifier)
            .put("domain", domain)
            .put("usageCount", usageCount.toLong())
            .put("isCertified", isCertified)
            .put("applicationData", _applicationData.encodeAsCbor())
            .put("credentialCounter", credentialCounter)

        // adds the class name of the credential implementation - this is used by CredentialFactory.createCredential
        this::class.simpleName?.let { mapBuilder.put("credentialType", it) }

        if (replacementForIdentifier != null) {
            mapBuilder.put("replacementForAlias", replacementForIdentifier!!)
        }
        if (replacementIdentifier != null) {
            mapBuilder.put("replacementAlias", replacementIdentifier!!)
        }

        if (isCertified) {
            mapBuilder.put("data", issuerProvidedData)
                .put("validFrom", validFrom.toEpochMilli())
                .put("validUntil", validUntil.toEpochMilli())
        }

        addSerializedData(mapBuilder)
        return mapBuilder.end().build()
    }

    /**
     * Deserializes a credential for a particular document.
     *
     * @param dataItem the serialized credential.
     * @param document the document associated with the credential.
     * @return the credential object.
     */
    open fun deserialize(
        dataItem: DataItem,
        document: Document
    ): Credential {
        val applicationDataDataItem = dataItem["applicationData"]
        check(applicationDataDataItem is Bstr) { "applicationData not found or not byte[]" }
        this.document = document
        _applicationData = SimpleApplicationData
            .decodeFromCbor(applicationDataDataItem.value) {
                document.saveDocument()
            }

        identifier = dataItem["identifier"].asTstr
        domain = dataItem["domain"].asTstr
        usageCount = dataItem["usageCount"].asNumber.toInt()
        isCertified = dataItem["isCertified"].asBoolean

        if (isCertified) {
            _issuerProvidedData = dataItem["data"].asBstr
            _validFrom = Timestamp.ofEpochMilli(dataItem["validFrom"].asNumber)
            _validUntil = Timestamp.ofEpochMilli(dataItem["validUntil"].asNumber)
        } else {
            _issuerProvidedData = null
            _validFrom = null
            _validUntil = null
        }

        replacementIdentifier = dataItem.getOrNull("replacementAlias")?.asTstr
        replacementForIdentifier = dataItem.getOrNull("replacementForAlias")?.asTstr

        credentialCounter = dataItem["credentialCounter"].asNumber

        return this
    }
}