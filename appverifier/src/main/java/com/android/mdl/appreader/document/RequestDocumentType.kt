package com.android.mdl.appreader.document

import com.android.identity.android.mdoc.document.DocumentType

enum class RequestDocumentType(val value: String, val documentType: DocumentType) {
    EUPID("EuPid", DocumentType.EUPID),
    MDL("Mdl", DocumentType.MDL),
    MDL_OLDER_THAN_18("MdlOlderThan18", DocumentType.MDL),
    MDL_OLDER_THAN_21("MdlOlderThan21", DocumentType.MDL),
    MDL_MANDATORY_FIELDS("MdlMandatoryFields", DocumentType.MDL),
    MDL_WITH_LINKAGE("MdlWithLinkage", DocumentType.MDL),
    MLD_US_TRANSPORTATION("MdlUsTransportation", DocumentType.MDL),
    MICOV_ATT("MicovAtt", DocumentType.MICOV),
    MICOV_VTR("MicovVtr", DocumentType.MICOV),
    MULTI003("Multi003", DocumentType.MICOV),
    MVR("Mvr", DocumentType.MVR)
}