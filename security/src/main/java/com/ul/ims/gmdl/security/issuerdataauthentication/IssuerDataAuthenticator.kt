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

package com.ul.ims.gmdl.security.issuerdataauthentication

import android.util.Log
import com.ul.ims.gmdl.cbordata.cryptoUtils.CryptoUtils
import com.ul.ims.gmdl.cbordata.namespace.MdlNamespace
import com.ul.ims.gmdl.cbordata.response.IssuerSignedItem
import com.ul.ims.gmdl.cbordata.security.CoseSign1
import com.ul.ims.gmdl.cbordata.security.EC2Curve
import com.ul.ims.gmdl.cbordata.security.IssuerNameSpaces
import com.ul.ims.gmdl.cbordata.security.mso.DigestIds
import com.ul.ims.gmdl.cbordata.security.mso.MobileSecurityObject
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils
import java.security.*
import java.util.*

class IssuerDataAuthenticator constructor(
    private val rootCertificatesAndPublicKeys: Map<IACACertificate, PublicKey>,
    private val coseSign1: CoseSign1,
    private val issuerNameSpaces: IssuerNameSpaces,
    private val docType: String,
    private var issuingCountry : String?
) {
    companion object {
        const val LOG_TAG = "IssuerDataAuthenticator"
        const val KEY_MEMBER_EC2_KEYS = 2
    }

    private var mso = MobileSecurityObject.Builder()
        .decode(coseSign1.payloadData).build()
    private var computedDigestIds: Map<Int, ByteArray>? = hashMapOf()
    private var dsCertificate : DSCertificate? = null

    fun isDataAuthentic(date: Date): Boolean {
        mso?.let {
            if (docType != it.documentType) {
                print(it.documentType)
                throw IssuerDataAuthenticationException("Invalid documentType")
            }
            Log.i(LOG_TAG, "docType matches with mso docType")
            if (!keyTypeIsConsistentWithCurve(it)) {
                throw IssuerDataAuthenticationException("KeyType inconsistent with Curve")
            }
            try {
                if (!certificatesAreValid(coseSign1.dsCertificateBytes, date, issuingCountry)) {
                    throw IssuerDataAuthenticationException("Certificates are invalid")
                }
            } catch (ex: IssuerDataAuthenticationException) {
                throw IssuerDataAuthenticationException("Parsing certificates failed. $ex")
            }
            if (!signatureVerifies(coseSign1)) {
                throw IssuerDataAuthenticationException("Signature does not verify")
            } else {
                Log.d(LOG_TAG, "Issuer Signature Verification Succeed")
            }
            if (!digestsMatch(it.getMdlDigestIds()?.values, issuerNameSpaces)) {
                throw IssuerDataAuthenticationException("Computed digests do not match received digests")
            }
            return true
        }
        return false
    }

    private fun certificatesAreValid(dsCertificateData: ByteArray?, date: Date, issuingCountry: String?): Boolean {
        dsCertificateData?.let {
            this.dsCertificate = DSCertificate(dsCertificateData, rootCertificatesAndPublicKeys, issuingCountry)
            val dsCert = this.dsCertificate
            dsCert?.let {
                try {
                    if (!dsCert.isValid(date)) {
                        return false
                    }
                } catch (ex: IssuerDataAuthenticationException) {
                    throw IssuerDataAuthenticationException("DS certificate is invalid: $ex")
                }
                Log.i(LOG_TAG, "DS and IACA certificates validated.")
                return true
            }
        }
        throw IssuerDataAuthenticationException("DSCertificate is null")
    }

    private fun signatureVerifies(coseSign1: CoseSign1): Boolean {

        val signer: Signature
        try {
            signer = Signature.getInstance(coseSign1.alg?.id)
            signer.initVerify(this.dsCertificate?.certificate?.publicKey)
            signer.update(coseSign1.toBeSignedSigStructure)

            coseSign1.signature?.let {sig ->
                val signature = CryptoUtils.signatureCoseToDer(sig)
                signature?.let {s ->
                    if (signer.verify(s)) {
                        return true
                    } else {
                        throw IssuerDataAuthenticationException("Signature does not verify")
                    }
                }

            }

        } catch (ex: NoSuchAlgorithmException) {
            throw IssuerDataAuthenticationException("Invalid algorithm: $ex")
        } catch (ex: InvalidKeyException) {
            throw IssuerDataAuthenticationException("Invalid key: $ex")
        } catch (ex: SignatureException) {
            throw IssuerDataAuthenticationException("Signature does not verify: $ex")
        }

        return false
    }

    private fun digestsMatch(receivedDigests: Map<Int, ByteArray>?, issuerNameSpaces: IssuerNameSpaces): Boolean {
        val listOfMdlIssuerSignedItems = issuerNameSpaces.nameSpaces[MdlNamespace.namespace]
        listOfMdlIssuerSignedItems?.let {
            computedDigestIds = computeDigests(it)?.values
        }
        if (receivedDigests == null) {
            throw IssuerDataAuthenticationException("Received digests are invalid")
        }
        if (computedDigestIds == null) {
            throw IssuerDataAuthenticationException("Computed digests are invalid")
        }
        for (i in receivedDigests.keys) {
            val computedDigest = computedDigestIds?.get(i)
            val receivedDigest = receivedDigests[i]
            if (computedDigest != null && receivedDigest != null) {
                if (!receivedDigest.contentEquals(computedDigest)) {
                    print("\n DigestId $i does not match. \n " +
                            "ComputedDigest = ${ByteUtils.toHexString(computedDigest)} \n" +
                            "ReceivedDigest = ${ByteUtils.toHexString(receivedDigest)} \n")
                return false
                }
                if (receivedDigest.contentEquals(computedDigest)) {
                    print("\n DigestId $i matches. \n " +
                            "ComputedDigest = ${ByteUtils.toHexString(computedDigest)} \n" +
                            "ReceivedDigest = ${ByteUtils.toHexString(receivedDigest)} \n")
                }
            }
        }
        Log.i(LOG_TAG, "All digests match.")
        return true
    }

    private fun computeDigests(listOfMdlIssuerSignedItems: MutableList<IssuerSignedItem>): DigestIds? {
        val digestIdsHashMap: HashMap<Int, ByteArray> = hashMapOf()
        var digest: ByteArray
        val id = mso?.digestAlgorithm?.id
        Log.d(LOG_TAG, "MSO digestAlgorithm $id")

        // TODO: Fix encoding of driving privileges
        for (i in listOfMdlIssuerSignedItems) {
            id?.let {
                val encodedIsi = i.encode()
                val messageDigest: MessageDigest = MessageDigest.getInstance(id)
                digest = messageDigest.digest(encodedIsi)
                digestIdsHashMap[i.digestId] = digest

                Log.d(
                    LOG_TAG, "Calculated hash for ${ByteUtils.toHexString(encodedIsi)} is " +
                            ByteUtils.toHexString(digest)
                )
            }
        }
        return DigestIds.Builder().decode(digestIdsHashMap).build()
    }

    private fun keyTypeIsConsistentWithCurve(mso: MobileSecurityObject): Boolean {
        return if (mso.coseKey?.keyType == KEY_MEMBER_EC2_KEYS && mso.coseKey?.curve is EC2Curve) {
            Log.i(LOG_TAG, "keyType is consistent with curve.")
            true
        } else
            false
    }
}
