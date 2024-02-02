package com.android.identity.issuance.evidence

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import co.nstant.`in`.cbor.model.UnsignedInteger
import com.android.identity.internal.Util

/**
 * A response to an evidence request.
 */
abstract class EvidenceResponse(
    /**
     * The evidence type.
     */
    val evidenceType: EvidenceType
) {
    // TODO: maybe use reflection or other nifty trick to avoid all this boilerplate

    fun toCbor(): ByteArray {
        val builder = CborBuilder()
        val mapBuilder = builder.addMap()
        mapBuilder.put("evidenceType", evidenceType.name)
        when (evidenceType) {
            EvidenceType.MESSAGE -> {
                val er = this as EvidenceResponseMessage
                mapBuilder.put("acknowledged", er.acknowledged)
            }
            EvidenceType.QUESTION_STRING -> {
                val er = this as EvidenceResponseQuestionString
                mapBuilder.put("answer", er.answer)
            }
            EvidenceType.QUESTION_MULTIPLE_CHOICE -> {
                val er = this as EvidenceResponseQuestionMultipleChoice
                mapBuilder.put("answer", er.answer)
            }
            EvidenceType.ICAO_9303_PASSIVE_AUTHENTICATION -> {
                val er = this as EvidenceResponseIcaoPassiveAuthentication
                for (entry in er.dataGroups.entries) {
                    mapBuilder.put(UnsignedInteger(entry.key.toLong()), ByteString(entry.value))
                }
                mapBuilder.put(UnicodeString("sod"), ByteString(er.securityObject))
            }
        }
        return Util.cborEncode(builder.build()[0])
    }

    companion object {
        fun fromCbor(encodedValue: ByteArray): EvidenceResponse {
            val map = Util.cborDecode(encodedValue)
            val evidenceType = EvidenceType.valueOf(Util.cborMapExtractString(map, "evidenceType"))
            when (evidenceType) {
                EvidenceType.MESSAGE -> {
                    return EvidenceResponseMessage(
                        Util.cborMapExtractBoolean(map, "acknowledged"),
                    )
                }
                EvidenceType.QUESTION_STRING -> {
                    return EvidenceResponseQuestionString(
                        Util.cborMapExtractString(map, "answer"),
                    )
                }
                EvidenceType.QUESTION_MULTIPLE_CHOICE -> {
                    return EvidenceResponseQuestionMultipleChoice(
                        Util.cborMapExtractString(map, "answer"),
                    )
                }
                EvidenceType.ICAO_9303_PASSIVE_AUTHENTICATION -> {
                    val cborMap = Util.castTo(Map::class.java, map)
                    val decodedMap = HashMap<Int, ByteArray>()
                    var securityObject: ByteArray? = null
                    for (key in cborMap.keys) {
                        val value = cborMap[key]
                        when (key) {
                            is UnsignedInteger -> {
                                decodedMap[key.value!!.toInt()] = (value as ByteString).bytes
                            }
                            is UnicodeString -> {
                                if (key.toString() == "sod") {
                                    securityObject = (value as ByteString).bytes
                                }
                            }
                        }
                    }
                    return EvidenceResponseIcaoPassiveAuthentication(decodedMap, securityObject!!)
                }
            }
        }
    }

}
