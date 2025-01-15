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
package com.android.identity.android.direct_access

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborBuilder
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.MapBuilder
import com.android.identity.cbor.RawCbor
import com.android.identity.credential.Credential
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.X509CertChain
import com.android.identity.crypto.javaPublicKey
import com.android.identity.document.Document
import com.android.identity.document.DocumentStore
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.securearea.KeyAttestation
import kotlinx.datetime.Instant
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.interfaces.ECPublicKey
import java.util.Arrays

/**
 * An mdoc credential, according to [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html),
 * which can be stored in the DirectAccess applet. This credential makes use of the [DirectAccess]
 * class to integrate with the applet.
 *
 * In order to use this credential in a wallet application, the secondary constructor of [DocumentStore]
 * must be used in order to set the [DocumentStore.directAccessTransport].
 */
class DirectAccessCredential: Credential {
    companion object {
        private const val TAG = "DirectAccessCredential"
    }

    /**
     * Constructs a new [DirectAccessCredential].
     *
     * @param document the document to add the credential to.
     * @param asReplacementFor the credential this credential will replace, if not null
     * @param domain the domain of the credential
     * @param docType the docType of the credential
     * @param approvedReaderCerts the reader certs this credential is allowed to present data to
     */
    constructor(
        document: Document,
        asReplacementFor: Credential?,
        domain: String,
        docType: String,
        approvedReaderCerts: X509CertChain,
        documentSlot: Int
    ) : super(document, asReplacementFor, domain) {
        this.docType = docType
        this.approvedReaderCerts = approvedReaderCerts
        this.documentSlot = documentSlot

        assert(document.directAccessTransport != null) { "In order to create " +
                "DirectAccessCredentials, the DirectAccessTransport must be set in the DocumentStore" }
        directAccess = DirectAccess(document.directAccessTransport!!)

        directAccess.createCredential(this.documentSlot).let {
            signingCert = it.first
            encryptedPresentationData = it.second
        }

        // Only the leaf constructor should add the credential to the document.
        if (this::class == DirectAccessCredential::class) {
            addToDocument()
        }
    }

    /**
     * Constructs a Credential from serialized data.
     *
     * @param document the [Document] that the credential belongs to.
     * @param dataItem the serialized data.
     */
    constructor(
        document: Document,
        dataItem: DataItem,
    ) : super(document, dataItem) {
        docType = dataItem["docType"].asTstr
        documentSlot = dataItem["slot"].asNumber.toInt()
        approvedReaderCerts = X509CertChain.fromDataItem(dataItem["approvedReaderCerts"])
        encryptedPresentationData = dataItem["encryptedData"].asBstr
        signingCert = X509CertChain.fromDataItem(dataItem["signingCert"])
        assert(document.directAccessTransport != null) { "In order to use " +
                "DirectAccessCredentials, the DirectAccessTransport must be set in the DocumentStore" }
        directAccess = DirectAccess(document.directAccessTransport!!)
    }

    /**
     * The docType of the credential as defined in
     * [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html).
     */
    val docType: String

    /**
     * The attestation for the key associated with this credential.
     *
     * The application should send this attestation to the issuer which should create
     * issuer-provided data (if using ISO/IEC 18013-5:2021 this would include the MSO).
     * Once received, the application should call [Credential.certify] to certify
     * the [Credential].
     */
    val attestation: KeyAttestation
        get() {
            return KeyAttestation(signingCert.certificates.first().ecPublicKey, signingCert)
        }

    private val approvedReaderCerts: X509CertChain
    val documentSlot: Int
    private var encryptedPresentationData: ByteArray
    private val signingCert: X509CertChain
    private val directAccess: DirectAccess

    override fun addSerializedData(builder: MapBuilder<CborBuilder>) {
        super.addSerializedData(builder)
        builder.put("docType", docType)
        builder.put("slot", documentSlot)
        builder.put("approvedReaderCerts", approvedReaderCerts.toDataItem())
        builder.put("encryptedData", encryptedPresentationData)
        builder.put("signingCert", signingCert.toDataItem())
    }

    // Provisions credential data for a specific signing key request.
    //
    // The |credentialData| parameter must be CBOR conforming to the following CDDL:
    //
    //   CredentialData = {
    //     "docType": tstr, // todo remove once applet is updated
    //     "issuerNameSpaces": IssuerNameSpaces,
    //     "issuerAuth" : IssuerAuth,
    //     "readerAccess" : ReaderAccess
    //   }
    //
    //   IssuerNameSpaces = {
    //     NameSpace => [ + IssuerSignedItemBytes ]
    //   }
    //
    //   ReaderAccess = [ * COSE_Key ]
    //
    // This data will be stored on the Secure Area and used for MDOC presentations
    // using NFC data transfer in low-power mode.
    //
    // The `readerAccess` field contains a list of keys used for implementing
    // reader authentication. If this list is non-empty, reader authentication
    // is not required. Otherwise the request must be be signed and the request is
    // authenticated if, and only if, a public key from the X.509 certificate
    // chain for the key signing the request exists in the `readerAccess` list.
    //
    // If reader authentication fails, the returned DeviceResponse shall return
    // error code 10 for the requested docType in the "documentErrors" field.
    override fun certify(
        issuerProvidedAuthenticationData: ByteArray,
        validFrom: Instant,
        validUntil: Instant
    ) {
        val daCredentialData = appendReaderCerts(issuerProvidedAuthenticationData)

        // update presentation package
        encryptedPresentationData = directAccess.certifyCredential(documentSlot,
            daCredentialData, encryptedPresentationData)
        // TODO add applet functionality such that validFrom and validUntil are passed to the applet
        // and considered when presenting
        super.certify(issuerProvidedAuthenticationData, validFrom, validUntil)
    }

    private fun appendReaderCerts(
        issuerProvidedAuthenticationData: ByteArray
    ): ByteArray {
        // pull out components from issuerProvidedAuthenticationData
        val staticAuthData = StaticAuthDataParser(issuerProvidedAuthenticationData).parse()

        val readerBuilder = CborArray.builder()
        for (cert in approvedReaderCerts.certificates) {
            val pubKey = getAndFormatRawPublicKey(cert)
            readerBuilder.add(pubKey)
        }
        val readerAuth = readerBuilder.end().build()

        // rebuild with readerCerts
        return CborMap.builder().apply {
            for ((namespace, bytesList) in staticAuthData.digestIdMapping) {
                putArray(namespace).let { innerBuilder ->
                    bytesList.forEach { encodedIssuerSignedItemMetadata ->
                        innerBuilder.add(RawCbor(encodedIssuerSignedItemMetadata))
                    }
                }
            }
        }.end().build().let { digestIdMappingItem ->
            Cbor.encode(
                CborMap.builder()
                    .put("docType", docType) // todo update applet so that this field is not required
                    .put("issuerNameSpaces", digestIdMappingItem)
                    .put("issuerAuth", RawCbor(staticAuthData.issuerAuth))
                    .put("readerAccess", readerAuth) // TODO update applet so that this can be replaced with approvedReaderCerts.toDataItem()
                    .end()
                    .build()
            )
        }
    }

    private fun getAndFormatRawPublicKey(cert: X509Cert): ByteArray {
        val pubKey: EcPublicKey = cert.ecPublicKey
        val key: ECPublicKey = pubKey.javaPublicKey as ECPublicKey
        val xCoord: BigInteger = key.w.affineX
        val yCoord: BigInteger = key.w.affineY
        var keySize = 0
        if ("EC" == key.algorithm) {
            val curve: Int =
                key.params.curve.field.fieldSize
            when (curve) {
                256 -> keySize = 32
                384 -> keySize = 48
                521 -> keySize = 66
                512 -> keySize = 65
            }
        } else {
            // TODO Handle other Algorithms
        }
        val bb: ByteBuffer = ByteBuffer.allocate((keySize * 2) + 1)
        Arrays.fill(bb.array(), 0.toByte())
        bb.put(0x04.toByte())
        val xBytes: ByteArray = xCoord.toByteArray()
        // BigInteger returns the value as two's complement big endian byte encoding. This means
        // that a positive, 32-byte value with a leading 1 bit will be converted to a byte array of
        // length 33 in order to include a leading 0 bit.
        if (xBytes.size == (keySize + 1)) {
            bb.put(xBytes, 1, keySize)
        } else {
            bb.position(bb.position() + keySize - xBytes.size)
            bb.put(xBytes, 0, xBytes.size)
        }
        val yBytes: ByteArray = yCoord.toByteArray()
        if (yBytes.size == (keySize + 1)) {
            bb.put(yBytes, 1, keySize)
        } else {
            bb.position(bb.position() + keySize - yBytes.size)
            bb.put(yBytes, 0, yBytes.size)
        }
        return bb.array()
    }

    /**
     * Sets the credential as the active credential in the direct access applet (ie. this credential
     * would be the one used during presentation).
     */
    fun setAsActiveCredential() {
        directAccess.setActiveCredential(documentSlot, encryptedPresentationData)
    }
}
