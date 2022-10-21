package com.android.mdl.app.documentdata

import com.android.mdl.app.R

object RequestMicovVtr : RequestDocument() {

    override val docType = "org.micov.1"
    override val nameSpace = "org.micov.vtr.1"
    override val dataItems = DataItems.values().asList()

    enum class DataItems(override val identifier: String, override val stringResourceId: Int) :
        RequestDataItem {
        FAMILY_NAME("fn", R.string.micov_vtr_fn),
        GIVEN_NAME("gn", R.string.micov_vtr_gn),
        DATE_OF_BIRTH("dob", R.string.micov_vtr_dob),
        SEX("sex", R.string.micov_vtr_sex),
        FIRST_VACCINATION_AGAINST_RA01("v_RA01_1", R.string.micov_vtr_v_RA01_1),
        SECOND_VACCINATION_AGAINST_RA01("v_RA01_2", R.string.micov_vtr_v_RA01_2),
        ID_WITH_PASPORT_NUMBER("pid_PPN", R.string.micov_vtr_pid_PPN),
        ID_WITH_DRIVERS_LICENSE_NUMBER("pid_DL", R.string.micov_vtr_pid_DL)
    }
}