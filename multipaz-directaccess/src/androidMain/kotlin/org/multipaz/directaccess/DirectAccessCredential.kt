/*
 * Copyright 2025 The Android Open Source Project
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
package org.multipaz.directaccess

import android.os.Build
import androidx.annotation.RequiresApi
import org.multipaz.cbor.CborBuilder
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.MapBuilder
import org.multipaz.claim.Claim
import org.multipaz.credential.Credential
import org.multipaz.crypto.X509CertChain
import org.multipaz.document.Document
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.securearea.KeyAttestation
import org.multipaz.util.Logger
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * An mdoc credential, according to
 * [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html), which can be
 * stored in the DirectAccess applet. This credential makes use of the
 * [DirectAccess] class to integrate with the applet.
 *
 * Creation of a [DirectAccessCredential] is very similar to any other
 * [Credential] with the following exceptions:
 *
 * First, a slot must be reserved in the Direct Access applet for the [Document]
 * this credential is associated with. This can be done using
 * [DirectAccess.allocateDocumentSlot]. Once allocated, the slot must be passed
 * into the [DirectAccessCredential] constructor when creating new credentials.
 *
 * Secondly, certifying the credential requires the following format for
 * `issuerProvidedAuthenticationData`:
 *
 * The |issuerProvidedAuthenticationData| parameter must be CBOR conforming to
 * the following CDDL:
 *  ```
 *   issuerProvidedAuthenticationData = {
 *     "issuerNameSpaces": IssuerNameSpaces,
 *     "issuerAuth" : IssuerAuth,
 *     "readerAccess" : ReaderAccess // TODO: update applet for name change to "authorizedReaderRoots"
 *   }
 *
 *   IssuerNameSpaces = {
 *     NameSpace => [ + IssuerSignedItemBytes ]
 *   }
 *
 *   ReaderAccess = [ * COSE_Key ]
 *   ```
 *
 * This data will be stored on the Secure Area and used for MDOC presentations
 * using NFC data transfer in low-power mode.
 *
 * The `readerAccess` field contains a list of keys used for implementing
 * reader authentication. If this list is empty, reader authentication
 * is not required. Otherwise the request must be be signed and the request is
 * authenticated if, and only if, a public key from the X.509 certificate
 * chain for the key signing the request exists in the `readerAccess` list.
 *
 * If reader authentication fails, the returned DeviceResponse shall return
 * error code 10 for the requested docType in the "documentErrors" field.
 *
 * Lastly, in order to use the credential, it must be set as the active credential
 * in the Direct Access applet using [setAsActiveCredential] once the credential
 * is certified.
 */
actual class DirectAccessCredential private constructor(
    document: Document,
    asReplacementForIdentifier: String?,
    domain: String,
    /**
     * The docType of the credential as defined in
     * [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html).
     */
    var documentType: String
) : Credential(document, asReplacementForIdentifier, domain) {
    /**
     * The docType of the credential as defined in
     * [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html).
     */
    var docType: String = documentType // Made public and initialized
        private set // Keep the setter private if you only want it to be set internally

    lateinit var signingCert: X509CertChain
        private set

    lateinit var encryptedPresentationData: ByteArray
        private set

    actual companion object {
        private const val TAG = "DirectAccessCredential"
        actual const val CREDENTIAL_TYPE = "DirectAccessCredential"

        /** APDU slot Id as allocated for the document. -1 on failure. -2 is a placeholder for uninitialized slotId. */
        var documentSlotId: Int = -2

        @RequiresApi(Build.VERSION_CODES.P)
        actual suspend fun create(
            document: Document,
            asReplacementForIdentifier: String?,
            domain: String,
            documentType: String
        ): DirectAccessCredential {
            val credential = DirectAccessCredential(
                document,
                asReplacementForIdentifier,
                domain,
                documentType
            )

            allocateSlotIfNeeded(documentType)

            val (cert, presentationData) = DirectAccess.createCredential(documentSlotId)

            credential.signingCert = cert
            credential.encryptedPresentationData = presentationData
            credential.addToDocument()

            return credential
        }

        @RequiresApi(Build.VERSION_CODES.P)
        actual suspend fun delete(documentType: String) {
            allocateSlotIfNeeded(documentType)
            DirectAccess.clearDocumentSlot(documentSlotId)
        }

        @RequiresApi(Build.VERSION_CODES.P)
        private suspend fun allocateSlotIfNeeded(docType: String) {
            documentSlotId = if (documentSlotId < 0) DirectAccess.allocateDocumentSlot(docType) else documentSlotId
            // -1 means the slot was allocated and populated in the past sesson (can't be provisioned again).
            // As we can provision only slot 0, it should be set as if already allocated.
            if (documentSlotId == -1) documentSlotId = 0
        }
    }

    /**
     * Secondary constructor for deserialization.
     * This constructor also needs to call the super constructor of Credential.
     */
    actual constructor(document: Document) : this(
        document = document,
        asReplacementForIdentifier = null, // Default or placeholder for Credential constructor
        domain = "",                       // Default or placeholder for Credential constructor
        documentType = ""
    ) {
        // Properties like signingCert, encryptedPresentationData, and docType
        // will be set by the deserialize method.
        // The call to the primary constructor 'this(...)' handles the super() call.
    }

    actual override val credentialType: String
        get() = CREDENTIAL_TYPE

    actual val attestation: KeyAttestation
        get() {
            if (!::signingCert.isInitialized) {
                throw IllegalStateException(
                    "Signing certificate not initialized. Call create() or deserialize() first.")
            }
            return KeyAttestation(signingCert.certificates.first().ecPublicKey, signingCert)
        }

    actual override fun getClaims(
        documentTypeRepository: DocumentTypeRepository?
    ): List<Claim> {
        Logger.e(TAG, "getClaims() Not yet implemented")
        return emptyList()
    }

    /**
     * Constructs a Credential from serialized data, ie. the inverse of
     * [addSerializedData].
     *
     * This is required for all [Credential] implementations since it's used
     * when loading a [Document] instance from disk. Since the credential is serialized by the [Document] and stored
     * in [org.multipaz.document.DocumentStore] in its serialized CBOR form, creation of a [DirectAccessCredential]
     * via deserialization does not require integration with [DirectAccess].
     *
     * @param dataItem the serialized data.
     */
    override suspend fun deserialize(dataItem: DataItem) {
        super.deserialize(dataItem)
        this.docType = dataItem["docType"].asTstr
        this.encryptedPresentationData = dataItem["encryptedPresentationData"].asBstr
        this.signingCert = X509CertChain.fromDataItem(dataItem["signingCert"])
    }

    override fun addSerializedData(builder: MapBuilder<CborBuilder>) {
        super.addSerializedData(builder)
        builder.put("docType", docType)
        if (!::encryptedPresentationData.isInitialized || !::signingCert.isInitialized) {
            throw IllegalStateException("Credential not fully initialized for serialization.")
        }
        builder.put("encryptedPresentationData", encryptedPresentationData)
        builder.put("signingCert", signingCert.toDataItem())
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @OptIn(ExperimentalTime::class)
    override suspend fun certify(
        issuerProvidedAuthenticationData: ByteArray,
        validFrom: Instant,
        validUntil: Instant
    ) {
        if (!::encryptedPresentationData.isInitialized) {
            throw IllegalStateException("Credential not fully initialized for certification.")
        }

        allocateSlotIfNeeded(docType)

        // Assuming DirectAccess.certifyCredential is a suspend function
        encryptedPresentationData = DirectAccess.certifyCredential(
            documentSlotId,
            issuerProvidedAuthenticationData,
            encryptedPresentationData
        )

        setAsActiveCredential();

        super.certify(issuerProvidedAuthenticationData, validFrom, validUntil) // Call super.certify
    }

    @RequiresApi(Build.VERSION_CODES.P)
    internal suspend fun setAsActiveCredential() {
        if (!::encryptedPresentationData.isInitialized) {
            throw IllegalStateException("Credential not fully initialized to set as active.")
        }

        allocateSlotIfNeeded(docType = docType)

        DirectAccess.setActiveCredential(documentSlotId, encryptedPresentationData)
    }
}
