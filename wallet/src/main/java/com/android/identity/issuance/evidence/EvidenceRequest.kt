package com.android.identity.issuance.evidence

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.MajorType
import co.nstant.`in`.cbor.model.UnicodeString
import co.nstant.`in`.cbor.model.UnsignedInteger
import com.android.identity.internal.Util
import java.lang.IllegalArgumentException

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
        val builder = CborBuilder()
        val mapBuilder = builder.addMap()
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
                var pvBuilder = CborBuilder()
                var pvArrayBuilder = pvBuilder.addArray()
                for (value in er.possibleValues) {
                    pvArrayBuilder.add(value)
                }
                mapBuilder.put("message", er.message)
                mapBuilder.put(UnicodeString("possibleValues"), pvBuilder.build()[0])
                mapBuilder.put("acceptButtonText", er.acceptButtonText)
            }
            EvidenceType.ICAO_9303_PASSIVE_AUTHENTICATION -> {
                val er = this as EvidenceRequestIcaoPassiveAuthentication
                val pvBuilder = CborBuilder()
                val pvArrayBuilder = pvBuilder.addArray()
                for (dataGroup in er.requestedDataGroups) {
                    pvArrayBuilder.add(dataGroup.toLong())
                }
                mapBuilder.put(UnicodeString("dataGroups"), pvBuilder.build()[0])
            }
        }
        return Util.cborEncode(builder.build()[0])
    }

    companion object {
        fun fromCbor(encodedValue: ByteArray): EvidenceRequest {
            val map = Util.cborDecode(encodedValue)
            val evidenceType = EvidenceType.valueOf(Util.cborMapExtractString(map, "evidenceType"))
            when (evidenceType) {
                EvidenceType.MESSAGE -> {
                    var rejectButtonText: String? = null
                    if (Util.cborMapHasKey(map, "rejectButtonText")) {
                        rejectButtonText = Util.cborMapExtractString(map, "rejectButtonText")
                    }
                    return EvidenceRequestMessage(
                        Util.cborMapExtractString(map, "message"),
                        Util.cborMapExtractString(map, "acceptButtonText"),
                        rejectButtonText,
                    )
                }
                EvidenceType.QUESTION_STRING -> {
                    return EvidenceRequestQuestionString(
                        Util.cborMapExtractString(map, "message"),
                        Util.cborMapExtractString(map, "defaultValue"),
                        Util.cborMapExtractString(map, "acceptButtonText"),
                    )
                }
                EvidenceType.QUESTION_MULTIPLE_CHOICE -> {
                    val possibleValues = mutableListOf<String>()
                    for (stringDataItem in Util.cborMapExtractArray(map, "possibleValues")) {
                        possibleValues.add(stringDataItem.toString())
                    }
                    return EvidenceRequestQuestionMultipleChoice(
                        Util.cborMapExtractString(map, "message"),
                        possibleValues,
                        Util.cborMapExtractString(map, "acceptButtonText"),
                    )
                }
                EvidenceType.ICAO_9303_PASSIVE_AUTHENTICATION -> {
                    val requests = Util.cborMapExtractArray(map, "dataGroups").map {
                        when (it.majorType) {
                            MajorType.UNSIGNED_INTEGER -> (it as UnsignedInteger).value.toInt()
                            else -> throw IllegalArgumentException(
                                "not a valid ICAO_9303_PASSIVE_AUTHENTICATION request")
                        }
                    }
                    return EvidenceRequestIcaoPassiveAuthentication(requests)
                }
            }
        }
    }

}
