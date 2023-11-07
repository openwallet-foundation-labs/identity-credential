package com.android.mdl.appreader.document

import com.android.identity.android.mdoc.document.DataElement
import com.android.identity.android.mdoc.document.Document
import java.io.Serializable


class RequestDocument(val requestType: RequestDocumentType) : Serializable {
    val elements: List<DataElement> = when (requestType){
        RequestDocumentType.EUPID -> Document.EuPid.elements
        RequestDocumentType.MDL -> Document.Mdl.elementsMdlNamespace
        RequestDocumentType.MDL_OLDER_THAN_18 -> Document.Mdl.elementsOlderThan18
        RequestDocumentType.MDL_OLDER_THAN_21 -> Document.Mdl.elementsOlderThan21
        RequestDocumentType.MDL_MANDATORY_FIELDS -> Document.Mdl.mandatoryElements
        RequestDocumentType.MDL_WITH_LINKAGE -> Document.Mdl.elementsMdlWithLinkage
        RequestDocumentType.MLD_US_TRANSPORTATION -> Document.Mdl.elementsUsTransportation
        RequestDocumentType.MICOV_ATT -> Document.Micov.elementsAtt
        RequestDocumentType.MICOV_VTR -> Document.Micov.elementsVtr
        RequestDocumentType.MULTI003 -> Document.Micov.elementsMulti003
        RequestDocumentType.MVR -> Document.Mvr.elements
    }
    val documentType: String = requestType.documentType.value
    private var mapSelectedDataElement: Map<DataElement, Boolean>? = null

    /**
     * Set data items selected by the user
     *
     * @param elements List of the data elements
     * @param intentToRetain Should those data elements be retained?
     */
    fun setSelectedDataElements(elements: List<DataElement>, intentToRetain: Boolean) {

        val map = mutableMapOf<DataElement, Boolean>()
        elements.forEach {
            map[it] = intentToRetain
        }
        mapSelectedDataElement = map
    }

    fun getDataElementsToRequest(): Map<String, Map<String, Boolean>> {
        val result = mapSelectedDataElement?.asSequence()?.groupBy(
            { it.key.nameSpace.value }, { Pair(it.key.elementName, it.value) })
            ?.mapValues { it.value.toMap() }
            ?: throw IllegalStateException("No data items selected for this request")
        return result
    }
}