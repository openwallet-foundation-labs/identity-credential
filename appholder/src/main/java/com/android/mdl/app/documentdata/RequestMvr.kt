package com.android.mdl.app.documentdata

import com.android.mdl.app.R

object RequestMvr : RequestDocument() {

    override val docType = "nl.rdw.mekb.1"
    override val nameSpace = "nl.rdw.mekb.1"
    override val dataItems = DataItems.values().asList()

    enum class DataItems(override val identifier: String, override val stringResourceId: Int) :
        RequestDataItem {
        REGISTRATION_INFO("registration_info", R.string.registration_info),
        ISSUE_DATE("issue_date", R.string.issue_date),
        REGISTRATION_HOLDER("registration_holder", R.string.registration_holder),
        BASIC_VEHICLE_INFO("basic_vehicle_info", R.string.basic_vehicle_info),
        VIN("vin", R.string.vin)
    }
}