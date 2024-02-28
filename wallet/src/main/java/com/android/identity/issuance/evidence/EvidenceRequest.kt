package com.android.identity.issuance.evidence

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.Uint
import kotlin.IllegalArgumentException

/**
 * A request for evidence by the issuer.
 */
abstract class EvidenceRequest(
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
                val er = this as EvidenceRequestMessage
                mapBuilder.put("message", er.message)
                mapBuilder.put("acceptButtonText", er.acceptButtonText)
                if (er.rejectButtonText != null) {
                    mapBuilder.put("rejectButtonText", er.rejectButtonText)
                }
            }
            EvidenceType.QUESTION_STRING -> {
                val er = this as EvidenceRequestQuestionString
                mapBuilder.put("message", er.message)
                mapBuilder.put("defaultValue", er.defaultValue)
                mapBuilder.put("acceptButtonText", er.acceptButtonText)
            }
            EvidenceType.QUESTION_MULTIPLE_CHOICE -> {
                val er = this as EvidenceRequestQuestionMultipleChoice
                val pvMapBuilder = CborMap.builder()
                for (entry in er.possibleValues) {
                    pvMapBuilder.put(entry.key, entry.value)
                }
                mapBuilder.put("message", er.message)
                mapBuilder.put("possibleValues", pvMapBuilder.end().build())
                mapBuilder.put("acceptButtonText", er.acceptButtonText)
            }
            EvidenceType.ICAO_9303_PASSIVE_AUTHENTICATION -> {
                val er = this as EvidenceRequestIcaoPassiveAuthentication
                val pvArrayBuilder = CborArray.builder()
                for (dataGroup in er.requestedDataGroups) {
                    pvArrayBuilder.add(dataGroup.toLong())
                }
                mapBuilder.put("dataGroups", pvArrayBuilder.end().build())
            }
            EvidenceType.ICAO_9303_NFC_TUNNEL -> {
                val er = this as EvidenceRequestIcaoNfcTunnel
                mapBuilder.put("requestType", er.requestType.name)
                mapBuilder.put("progress", er.progressPercent.toLong())
                mapBuilder.put("message", Bstr(er.message))
            }
            EvidenceType.ICAO_9303_NFC_TUNNEL_RESULT ->
                throw IllegalArgumentException("Invalid request type")
        }
        return Cbor.encode(mapBuilder.end().build())
    }

    companion object {
        fun fromCbor(encodedValue: ByteArray): EvidenceRequest {
            val map = Cbor.decode(encodedValue)
            val evidenceType = EvidenceType.valueOf(map["evidenceType"].asTstr)
            when (evidenceType) {
                EvidenceType.MESSAGE -> {
                    return EvidenceRequestMessage(
                        map["message"].asTstr,
                        map["acceptButtonText"].asTstr,
                        map.getOrNull("rejectButtonText")?.asTstr,
                    )
                }
                EvidenceType.QUESTION_STRING -> {
                    return EvidenceRequestQuestionString(
                        map["message"].asTstr,
                        map["defaultValue"].asTstr,
                        map["acceptButtonText"].asTstr,
                    )
                }
                EvidenceType.QUESTION_MULTIPLE_CHOICE -> {
                    val possibleValues = mutableMapOf<String, String>()
                    val answers = map["possibleValues"].asMap
                    for (answerId in answers.keys) {
                        possibleValues[answerId.asTstr] = answers[answerId]!!.asTstr
                    }
                    return EvidenceRequestQuestionMultipleChoice(
                        map["message"].asTstr,
                        possibleValues,
                        map["acceptButtonText"].asTstr,
                    )
                }
                EvidenceType.ICAO_9303_PASSIVE_AUTHENTICATION -> {
                    val requests = map["dataGroups"].asArray.map {
                        when (it) {
                            is Uint -> it.asNumber.toInt()
                            else -> throw IllegalArgumentException(
                                "not a valid ICAO_9303_PASSIVE_AUTHENTICATION request")
                        }
                    }
                    return EvidenceRequestIcaoPassiveAuthentication(requests)
                }
                EvidenceType.ICAO_9303_NFC_TUNNEL -> {
                    return EvidenceRequestIcaoNfcTunnel(
                        EvidenceRequestIcaoNfcTunnelType.valueOf(map["requestType"].asTstr),
                        map["progress"].asNumber.toInt(),
                        map["message"].asBstr
                    )
                }
                EvidenceType.ICAO_9303_NFC_TUNNEL_RESULT ->
                    throw IllegalArgumentException("Invalid request type")
            }
        }
    }

}
