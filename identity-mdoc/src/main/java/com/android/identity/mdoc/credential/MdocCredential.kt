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
package com.android.identity.mdoc.credential

import com.android.identity.cbor.CborBuilder
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.MapBuilder
import com.android.identity.credential.Credential
import com.android.identity.credential.SecureAreaBoundCredential
import com.android.identity.document.Document
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.SecureArea

/**
 * An mdoc credential, according to [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html).
 * The credential plays the role of *DeviceKey* and the issuer-signed
 * data includes the *Mobile Security Object* which includes the authentication
 * key and is signed by the issuer. This is used for anti-cloning and to return data signed
 * by the device.
 */
class MdocCredential() : SecureAreaBoundCredential() {

    /**
     * Creates a new [MdocCredential].
     *
     * @param asReplacementFor the credential this credential will replace, if not null
     * @param domain the domain of the credential
     * @param secureArea the secure area for the authentication key associated with this credential.
     * @param createKeySettings the settings used to create new credentials.
     * @param docType the docType of the credential
     */
    constructor(
        asReplacementFor: Credential?,
        domain: String,
        secureArea: SecureArea,
        createKeySettings: CreateKeySettings,
        docType: String
    ) : this() {
        MdocCredential.apply { create(
            asReplacementFor,
            domain,
            secureArea,
            createKeySettings,
            docType
        ) }
    }

    companion object {
        const val TAG = "MdocCredential"

        fun fromCbor(
            dataItem: DataItem,
            document: Document
        ) = MdocCredential().apply { deserialize(dataItem, document) }
    }

    /**
     * The docType of the credential as defined in
     * [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html).
     */
    lateinit var docType: String
        protected set

    protected fun create(
        asReplacementFor: Credential?,
        domain: String,
        secureArea: SecureArea,
        createKeySettings: CreateKeySettings,
        docType: String
    ): MdocCredential {
        super.create(asReplacementFor, domain, secureArea, createKeySettings)
        this.docType = docType
        return this
    }

    override fun addSerializedData(mapBuilder: MapBuilder<CborBuilder>) {
        super.addSerializedData(mapBuilder)
        mapBuilder.put("docType", docType)
    }

    override fun deserialize(dataItem: DataItem, document: Document): MdocCredential {
        super.deserialize(dataItem, document)
        val docType = dataItem["docType"].asTstr
        return this
    }
}
