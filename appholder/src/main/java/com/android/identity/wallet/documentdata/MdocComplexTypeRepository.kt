package com.android.identity.wallet.documentdata

object MdocComplexTypeRepository {
    private val allComplexTypes: MutableMap<String, MdocComplexTypes> = mutableMapOf()

    init {
        addComplexTypes(DrivingLicense.getMdocComplexTypes())
        addComplexTypes(VehicleRegistration.getMdocComplexTypes())
        addComplexTypes(VaccinationDocument.getMdocComplexTypes())
    }

    fun addComplexTypes(complexTypes: MdocComplexTypes) {
        allComplexTypes[complexTypes.docType] = complexTypes
    }

    fun getComplexTypes(docType: String): MdocComplexTypes? {
        return allComplexTypes[docType]
    }
}