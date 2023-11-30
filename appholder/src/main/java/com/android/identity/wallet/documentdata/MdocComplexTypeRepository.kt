package com.android.identity.wallet.documentdata

object MdocComplexTypeRepository {
    private val allComplexTypes: MutableList<MdocComplexTypes> = mutableListOf()

    init {
        addComplexTypes(DrivingLicense.getMdocComplexTypes())
        addComplexTypes(VehicleRegistration.getMdocComplexTypes())
        addComplexTypes(VaccinationDocument.getMdocComplexTypes())
    }
    fun addComplexTypes(complexTypes: MdocComplexTypes){
        allComplexTypes.add(complexTypes)
    }

    fun getComplexTypes(): List<MdocComplexTypes>{
        return allComplexTypes
    }
}