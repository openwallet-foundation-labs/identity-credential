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

package com.ul.ims.gmdl.cbordata.security.mso

import android.util.Log
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Map
import com.ul.ims.gmdl.cbordata.cryptoUtils.HashAlgorithms
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import com.ul.ims.gmdl.cbordata.namespace.MdlNamespace
import com.ul.ims.gmdl.cbordata.security.CoseKey
import com.ul.ims.gmdl.cbordata.security.namespace.IMsoNameSpace
import com.ul.ims.gmdl.cbordata.security.namespace.MsoMdlNamespace
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable

class MobileSecurityObject private constructor(
    //TODO : Check if bouncycastle related functions could be extracted to security module
    val digestAlgorithm: ASN1ObjectIdentifier,
    val coseKey: CoseKey?,
    val documentType: String,
    val listOfNameSpaces: List<IMsoNameSpace>,
    val validityInfo: ValidityInfo?
) : AbstractCborStructure() , Serializable {

    companion object {

        val algorithmMap = hashMapOf<String, ASN1ObjectIdentifier>(
            HashAlgorithms.SHA_256.algorithm to NISTObjectIdentifiers.id_sha256,
            HashAlgorithms.SHA_384.algorithm to NISTObjectIdentifiers.id_sha384,
            HashAlgorithms.SHA_512.algorithm to NISTObjectIdentifiers.id_sha512
        )

        const val DIGEST_ALGORITHM = "digestAlgorithm"
        const val VALUE_DIGESTS = "valueDigests"
        const val DEVICE_KEY = "deviceKey"
        const val DOCUMENT_TYPE = "docType"
        const val NAME_SPACES = "nameSpaces"
        const val VALIDITY_INFO = "validityInfo"
    }

    private fun getMdlNamespace(): IMsoNameSpace? {
        for (i in listOfNameSpaces) {
            if (i.namespace == MdlNamespace.namespace) {
                return i
            }
        }
        return null
    }

    fun getMdlDigestIds(): DigestIds? {
        val nspace = getMdlNamespace() ?: return null
        return DigestIds.Builder().decode(nspace.items).build()
    }

    override fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        var builder = CborBuilder()

        // MobileSecurityObject Map
        var mapBuilder = builder.addMap()

        // DigestAlgorithm Message digest algorithm used
        mapBuilder = mapBuilder.put(DIGEST_ALGORITHM, getAlg(digestAlgorithm))

        // ValueDigests Array of digests of all data elements
        var valueDigestsMapBuilder = mapBuilder.putMap(VALUE_DIGESTS)

        // NameSpaces
        var nameSpacesMapBuilder = valueDigestsMapBuilder.putMap(NAME_SPACES)
        for (i in listOfNameSpaces) {
            nameSpacesMapBuilder = nameSpacesMapBuilder.put(toDataItem(i.namespace), createDigestIdsMap())
        }
        valueDigestsMapBuilder = nameSpacesMapBuilder.end()
        mapBuilder = valueDigestsMapBuilder.end()

        if (coseKey == null) {
            throw CborException("deviceKey cannot be null")
        }
        mapBuilder = mapBuilder.put(UnicodeString(DEVICE_KEY), toDataItem(coseKey))
        mapBuilder.put(UnicodeString(DOCUMENT_TYPE), toDataItem(documentType))

        if (validityInfo == null) {
            throw CborException("validityInfo cannot be null")
        }
        mapBuilder = mapBuilder.put(UnicodeString(VALIDITY_INFO), validityInfo.toDataItem())

        builder = mapBuilder.end()
        CborEncoder(outputStream).encode(builder.build())
        return outputStream.toByteArray()
    }

    private fun createDigestIdsMap(): Map {
        var digestIdMap = Map()
        val digestIds = getMdlDigestIds()?.values ?: throw CborException("DigestIds cannot be null")
        for (i in digestIds.keys) {
            val digest = digestIds[i] ?: throw CborException("Digest for digest id $i is null")
            digestIdMap = digestIdMap.put(toDataItem(i), toDataItem(digest))
        }
        return digestIdMap
    }

    private fun getAlg(digestAlgorithm: ASN1ObjectIdentifier?) : String {
        for (i in algorithmMap.keys) {
            if (digestAlgorithm == algorithmMap[i]) {
                return i
            }
        }
        throw CborException("Unknown algorithm")
    }

    class Builder {
        private var digestAlgorithm: ASN1ObjectIdentifier? = null
        private var valueDigests: Map = Map()
        private var deviceKey: Map = Map()
        private var documentType: String = ""
        private var listOfNameSpaces: MutableList<IMsoNameSpace> = mutableListOf()
        private var mdlDigestIds: DigestIds? = null
        private var coseKey: CoseKey? = null
        private var validityInfo: ValidityInfo? = null

        fun decode(msoData: ByteArray?) = apply {
            try {
                val stream = ByteArrayInputStream(msoData)
                val dataItems = CborDecoder(stream).decode()
                if (dataItems.size > 0 ) {
                    val structureItems: Map? = dataItems[0] as? Map
                    structureItems?.let {
                        if (structureItems.keys.size == 5) {
                            val dA = structureItems[UnicodeString(DIGEST_ALGORITHM)] as UnicodeString
                            decodeDigestAlgorithm(dA)
                            valueDigests = structureItems[UnicodeString(VALUE_DIGESTS)] as Map
                            decodeValueDigests(valueDigests)
                            deviceKey = structureItems[UnicodeString(DEVICE_KEY)] as Map
                            decodeDeviceKey(deviceKey)
                            val dT = structureItems[UnicodeString(DOCUMENT_TYPE)] as UnicodeString
                            documentType = dT.string
                            val validityInfoDataItem =
                                structureItems[UnicodeString(VALIDITY_INFO)] as DataItem
                            validityInfo =
                                ValidityInfo.Builder().fromDataItem(validityInfoDataItem).build()
                        }
                    }
                }
            } catch (ex: CborException) {
                Log.e(javaClass.simpleName,": $ex")
            }
        }

        private fun decodeDigestAlgorithm(dA: UnicodeString) {
            digestAlgorithm = algorithmMap[dA.string]
        }

        private fun decodeDeviceKey(deviceKey: Map) {
            coseKey = CoseKey.Builder().decode(deviceKey).build()
        }

        private fun decodeValueDigests(valueDigests: Map) {
            val nameSpaces = valueDigests.get(UnicodeString(NAME_SPACES)) as Map
            decodeNameSpaces(nameSpaces)
        }

        private fun decodeNameSpaces(nameSpaces: Map) {
            nameSpaces.keys?.forEach {nspace ->
                val valueKey = (nspace as? UnicodeString)?.string
                val valueMap = nameSpaces.get(nspace)

                if (valueMap.majorType == MajorType.MAP) {
                    val hashMap = HashMap<Int, ByteArray>()

                    val cborMap = valueMap as Map
                    cborMap.keys?.forEach {
                        val key = (it as? UnsignedInteger)?.value?.toInt()
                        val value = (cborMap.get(it) as? ByteString)?.bytes
                        key?.let {k ->
                            value?.let {v ->
                                hashMap[k] = v
                            }
                        }
                    }
                    valueKey?.let {
                        listOfNameSpaces.add(MsoMdlNamespace(valueKey, hashMap))

                        if (valueKey == MdlNamespace.namespace) {
                            mdlDigestIds = DigestIds.Builder().decode(hashMap).build()
                        }
                    }
                }
            }

        }

        fun setDigestAlgorithm(digestAlgorithm: HashAlgorithms) = apply {
            this.digestAlgorithm = when (digestAlgorithm) {
                HashAlgorithms.SHA_256 -> {
                    ASN1ObjectIdentifier.getInstance(NISTObjectIdentifiers.id_sha256)
                }
                HashAlgorithms.SHA_384 -> {
                    ASN1ObjectIdentifier.getInstance(NISTObjectIdentifiers.id_sha384)
                }
                HashAlgorithms.SHA_512 -> {
                    ASN1ObjectIdentifier.getInstance(NISTObjectIdentifiers.id_sha512)
                }
            }
        }

        fun setDeviceKey(deviceKey: CoseKey) = apply {
            this.coseKey = deviceKey
        }

        fun setDocumentType(documentType: String) = apply {
            this.documentType = documentType
        }

        fun setListOfNameSpaces(list : List<IMsoNameSpace>) = apply {
            this.listOfNameSpaces = list.toMutableList()
        }

        fun setValidityInfo(validityInfo: ValidityInfo?) = apply {
            this.validityInfo = validityInfo
        }

        fun build() :  MobileSecurityObject? {
            digestAlgorithm?.let {algo ->
                return MobileSecurityObject(
                    algo,
                    coseKey,
                    documentType,
                    listOfNameSpaces,
                    validityInfo
                )
            }

            return null
        }
    }
}