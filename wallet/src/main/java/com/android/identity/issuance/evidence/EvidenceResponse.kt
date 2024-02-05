package com.android.identity.issuance.evidence

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.Tstr
import com.android.identity.cbor.Uint

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
        val mapBuilder = CborMap.builder()
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
                mapBuilder.put("answer", er.answerId)
            }
            EvidenceType.ICAO_9303_PASSIVE_AUTHENTICATION -> {
                val er = this as EvidenceResponseIcaoPassiveAuthentication
                for (entry in er.dataGroups.entries) {
                    mapBuilder.put(entry.key.toLong(), Bstr(entry.value))
                }
                mapBuilder.put("sod", Bstr(er.securityObject))
            }
            EvidenceType.ICAO_9303_NFC_TUNNEL -> {
                val er = this as EvidenceResponseIcaoNfcTunnel
                mapBuilder.put("response", Bstr(er.response))
            }
            EvidenceType.ICAO_9303_NFC_TUNNEL_RESULT -> {
                val er = this as EvidenceResponseIcaoNfcTunnelResult
                for (entry in er.dataGroups.entries) {
                    mapBuilder.put(entry.key.toLong(), Bstr(entry.value))
                }
                mapBuilder.put("sod", Bstr(er.securityObject))
                mapBuilder.put("authentication", er.advancedAuthenticationType.name)
            }
        }
        return Cbor.encode(mapBuilder.end().build())
    }

    companion object {
        fun fromCbor(encodedValue: ByteArray): EvidenceResponse {
            val map = Cbor.decode(encodedValue)
            val evidenceType = EvidenceType.valueOf(map["evidenceType"].asTstr)
            when (evidenceType) {
                EvidenceType.MESSAGE -> {
                    return EvidenceResponseMessage(map["acknowledged"].asBoolean)
                }
                EvidenceType.QUESTION_STRING -> {
                    return EvidenceResponseQuestionString(map["answer"].asTstr)
                }
                EvidenceType.QUESTION_MULTIPLE_CHOICE -> {
                    return EvidenceResponseQuestionMultipleChoice(map["answer"].asTstr)
                }
                EvidenceType.ICAO_9303_PASSIVE_AUTHENTICATION -> {
                    val decodedMap = mutableMapOf<Int, ByteArray>()
                    var securityObject: ByteArray? = null
                    for (key in map.asMap.keys) {
                        when (key) {
                            is Uint -> decodedMap[key.asNumber.toInt()] = map[key].asBstr
                            is Tstr -> {
                                if (key.asTstr != "sod") {
                                    throw IllegalArgumentException("Unexpected string key $key")
                                }
                                securityObject = map[key].asBstr
                            }
                            else -> {
                                throw IllegalArgumentException("Unexpected key $key")
                            }
                        }
                    }
                    return EvidenceResponseIcaoPassiveAuthentication(decodedMap, securityObject!!)
                }
                EvidenceType.ICAO_9303_NFC_TUNNEL -> {
                    return EvidenceResponseIcaoNfcTunnel(map["response"].asBstr)
                }
                EvidenceType.ICAO_9303_NFC_TUNNEL_RESULT -> {
                    val authenticationType =
                        EvidenceResponseIcaoNfcTunnelResult.AdvancedAuthenticationType.valueOf(
                            map["authentication"].asTstr)
                    val decodedMap = mutableMapOf<Int, ByteArray>()
                    var securityObject: ByteArray? = null
                    for (key in map.asMap.keys) {
                        when (key) {
                            is Uint -> decodedMap[key.asNumber.toInt()] = map[key].asBstr
                            is Tstr -> {
                                if (key.asTstr != "sod") {
                                    throw IllegalArgumentException("Unexpected string key $key")
                                }
                                securityObject = map[key].asBstr
                            }
                            else -> {
                                throw IllegalArgumentException("Unexpected key $key")
                            }
                        }
                    }
                    return EvidenceResponseIcaoNfcTunnelResult(
                        authenticationType, decodedMap, securityObject!!)
                }
            }
        }
    }

}
