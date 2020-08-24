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

package com.ul.ims.gmdl.security.mdlauthentication

import com.ul.ims.gmdl.cbordata.cryptoUtils.CryptoUtils
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.doctype.IDoctype
import com.ul.ims.gmdl.cbordata.response.DeviceAuth
import com.ul.ims.gmdl.cbordata.security.CoseKey
import com.ul.ims.gmdl.cbordata.security.CoseSign1
import com.ul.ims.gmdl.cbordata.security.SigStructure
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.*
import com.ul.ims.gmdl.cbordata.utils.Log
import com.ul.ims.gmdl.security.mdlauthentication.MacVerificationUtils.calculateDerivedKey
import com.ul.ims.gmdl.security.mdlauthentication.MacVerificationUtils.calculateHMac
import com.ul.ims.gmdl.security.mdlauthentication.MacVerificationUtils.calculateSharedKey
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils
import java.security.*

class MdlAuthenticator (
    val deviceEngagement: DeviceEngagement,
    private val deviceNameSpaces: DeviceNameSpaces?,
    val docType: IDoctype,
    private val readerKey: CoseKey?,
    private val deviceAuth: DeviceAuth?,
    private val verifierPrivateKey: PrivateKey?,
    private val deviceKey: CoseKey?
) {

    companion object {
        const val LOG_TAG = "MdlAuthenticator"
    }
    private var deviceAuthentication : DeviceAuthentication? = null
    private var sessionTranscript : SessionTranscript? = null
    private lateinit var holderPublicKey : PublicKey

    init {
        setHolderPublicKey()
        createSessionTranscript()
        createDeviceAuthentication()
    }

    private fun setHolderPublicKey() {
        // use the mDL public key stored in the MSO
        val hPublicKey = deviceEngagement.security?.coseKey?.getPublicKey()
            ?: throw MdlAuthenticationException("holderPublicKey is null")
        holderPublicKey = hPublicKey
    }

    fun isMdlAuthentic() : Boolean {
        val coseMac0 = deviceAuth?.deviceMac
        val coseSign1 = deviceAuth?.deviceSignature
        if (coseMac0 != null) {
            return validateUsingMac(coseMac0)
        }
        if (coseSign1 != null) {
            return validateUsingEcdsa(coseSign1)
        }
        throw MdlAuthenticationException("COSE_Mac0 and COSE_Sign1 are both null.")
    }

    private fun validateUsingEcdsa(coseSign1: CoseSign1): Boolean {
        validateCoseSign1(coseSign1)

        val sigStructureBuilder = SigStructure.Builder()
        sigStructureBuilder.setAlg(coseSign1.alg)
        sigStructureBuilder.setPayloadData(deviceAuthentication?.encode())
        val toBeSignedSigStructure = sigStructureBuilder.build().encode()
        Log.d(LOG_TAG, "toBeSignedSigStructure: ${ByteUtils.toHexString(toBeSignedSigStructure)}")

        return validateSignature(coseSign1.alg, toBeSignedSigStructure, coseSign1.signature)
    }

    private fun validateUsingMac(coseMac0: CoseMac0): Boolean {
        validateCoseMac0(coseMac0)
        val macStructureBuilder = MacStructure.Builder()
        macStructureBuilder.setAlg(coseMac0.encodeAlgMap(coseMac0.algorithm))
        macStructureBuilder.setPayload(deviceAuthentication?.encode())
        val toBeMacedData = macStructureBuilder.build().encode()
        return validateMac(coseMac0.algorithm, toBeMacedData, coseMac0.macValue)
    }

    private fun validateCoseSign1(coseSign1: CoseSign1) {
        if (coseSign1.payloadData != null) {
            throw MdlAuthenticationException("Payload in COSE_Sign1 is invalid.")
        }
        if (deviceAuthentication == null) {
            throw MdlAuthenticationException("DeviceAuthentication is null")
        }
    }

    private fun validateCoseMac0(coseMac0: CoseMac0) {
        if (coseMac0.payload != null) {
            throw MdlAuthenticationException("Payload in COSE_Mac0 is invalid.")
        }
        if (deviceAuthentication == null) {
            throw MdlAuthenticationException("DeviceAuthentication is null.")
        }
    }

    private fun encodeToString(encoded: ByteArray): String {
        val sb = StringBuilder(encoded.size * 2)

        for (b in encoded) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun validateSignature(alg : ASN1ObjectIdentifier?,
                                  toBeSignedSigStructure: ByteArray,
                                  signature: ByteArray?) : Boolean {
        if (signature == null) {
            throw MdlAuthenticationException("Signature in COSE_Sign1 cannot be null")
        }

        if (deviceKey == null) {
            throw MdlAuthenticationException("deviceKey cannot be null")
        }

        val mDeviceKey = deviceKey.getPublicKey()

        val signer : Signature
        try {
            signer = Signature.getInstance(alg?.id)
            signer.initVerify(mDeviceKey)
            signer.update(toBeSignedSigStructure)

            Log.d(LOG_TAG, "Envelop Signature to be validated")
            Log.d(LOG_TAG, encodeToString(signature))
            val sig = CryptoUtils.signatureCoseToDer(signature)

            sig?.let {
                Log.d(LOG_TAG, "Signature to be validated")
                Log.d(LOG_TAG, encodeToString(it))

                if (signer.verify(it)) {
                    return true
                } else {
                    throw MdlAuthenticationException("Signature does not verify")
                }
            }
        } catch (ex: NoSuchAlgorithmException) {
            throw MdlAuthenticationException("Invalid algorithm: $ex with message: ${ex.message}")
        } catch (ex: InvalidKeyException) {
            throw MdlAuthenticationException("Invalid key: $ex with message: ${ex.message}")
        } catch (ex: SignatureException) {
            throw MdlAuthenticationException("Signature does not verify: $ex with message: ${ex.message}")
        }
        return false
    }

    private fun validateMac(alg : String?, toBeMacedData: ByteArray, macValue: ByteArray?) : Boolean {
        if (macValue == null) {
            throw MdlAuthenticationException("tag in COSE_Mac0 cannot be null")
        }
        if (alg == null) {
            throw MdlAuthenticationException("alg is null")
        }
        verifierPrivateKey?.let {
            val verifierSharedKey = calculateSharedKey(holderPublicKey.encoded, verifierPrivateKey.encoded)
                ?: throw MdlAuthenticationException("VerifierSharedKey is null")
            val verifierDerivedKey = calculateDerivedKey(byteArrayOf(), verifierSharedKey)
                ?: throw MdlAuthenticationException("VerifierDerivedKey is null")
            val calculatedMacValue = calculateHMac(alg, toBeMacedData, verifierDerivedKey)
                ?: throw MdlAuthenticationException("calculatedMac is null")
            if (calculatedMacValue.contentEquals(macValue)) {
                return true
            }
        }
        return false
    }

    private fun createDeviceAuthentication() {
        createSessionTranscript()
        val deviceAuthenticationBuilder = DeviceAuthentication.Builder()
        deviceAuthenticationBuilder.setDeviceNameSpaces(deviceNameSpaces)
        deviceAuthenticationBuilder.setDocType(docType.docType)
        deviceAuthenticationBuilder.setSessionTranscript(sessionTranscript)
        deviceAuthentication = deviceAuthenticationBuilder.build()
        Log.d(LOG_TAG, "deviceAuthentication: ${ByteUtils.toHexString(deviceAuthentication?.encode())}")
    }

    private fun createSessionTranscript() {
        val sessionTranscriptBuilder = SessionTranscript.Builder()
        sessionTranscriptBuilder.setDeviceEngagement(deviceEngagement.encode())
        readerKey?.let {
            sessionTranscriptBuilder.setReaderKey(readerKey.encode() )
        }
        sessionTranscript = sessionTranscriptBuilder.build()
    }

}
