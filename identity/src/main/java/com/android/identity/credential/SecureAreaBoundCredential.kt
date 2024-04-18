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

import com.android.identity.cbor.CborBuilder
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.MapBuilder
import com.android.identity.crypto.CertificateChain
import com.android.identity.document.Document
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.SecureArea

/**
 * Base class for credentials bound to a [SecureArea].
 */
open class SecureAreaBoundCredential protected constructor() : Credential() {

    /**
     * Creates a new [SecureAreaBoundCredential].
     *
     * @param asReplacementFor the credential this credential will replace, if not null
     * @param domain the domain of the credential
     * @param secureArea the secure area for the authentication key associated with this credential.
     * @param createKeySettings the settings used to create new credentials.
     */
    constructor(
        asReplacementFor: Credential?,
        domain: String,
        secureArea: SecureArea,
        createKeySettings: CreateKeySettings,
    ) : this() {
        SecureAreaBoundCredential.apply { create(
            asReplacementFor,
            domain,
            secureArea,
            createKeySettings
        ) }
    }

    companion object {
        const val AUTHENTICATION_KEY_ALIAS_PREFIX = "IC_SecureAreaBoundCredential_"
        const val TAG = "SecureAreaBoundCredential"

        internal fun fromCbor(
            dataItem: DataItem,
            document: Document
        ) = SecureAreaBoundCredential().apply { deserialize(dataItem, document) }
    }

    /**
     * The secure area for the authentication key associated with this credential.
     *
     * This can be used together with the alias returned by [alias].
     */
    lateinit var secureArea: SecureArea
        protected set

    /**
     * The alias for the authentication key associated with this credential.
     *
     * This can be used together with the [SecureArea] returned by [secureArea]
     */
    lateinit var alias: String
        protected set

    override val isInvalidated: Boolean
        get() = secureArea.getKeyInvalidated(alias)

    /**
     * The X.509 certificate chain for the authentication key associated with this credential.
     *
     * The application should send this credential to the issuer which should create issuer-provided
     * data (e.g. an MSO if using ISO/IEC 18013-5:2021) using the credential as the `DeviceKey`.
     */
    val attestation: CertificateChain
        get() = secureArea.getKeyInfo(alias).attestation

    override fun delete() {
        secureArea.deleteKey(alias)
        super.delete()
    }

    protected fun create(
        asReplacementFor: Credential?,
        domain: String,
        secureArea: SecureArea,
        createKeySettings: CreateKeySettings,
    ): SecureAreaBoundCredential {
        super.create(asReplacementFor, domain)
        this.alias = AUTHENTICATION_KEY_ALIAS_PREFIX + this.identifier
        this.secureArea = secureArea
        this.secureArea.createKey(alias, createKeySettings)
        return this
    }

    override fun addSerializedData(mapBuilder: MapBuilder<CborBuilder>) {
        mapBuilder.put("secureAreaIdentifier", secureArea.identifier)
            .put("alias", alias)
    }

    override fun deserialize(
        dataItem: DataItem,
        document: Document
    ): SecureAreaBoundCredential {
        super.deserialize(dataItem, document)
        alias = dataItem["alias"].asTstr
        val secureAreaIdentifier = dataItem["secureAreaIdentifier"].asTstr
        secureArea = document.secureAreaRepository.getImplementation(secureAreaIdentifier)
            ?: throw IllegalStateException("Unknown Secure Area $secureAreaIdentifier")

        return this
    }
}