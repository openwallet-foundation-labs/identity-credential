package com.android.identity.android.mdoc.document

import com.android.identity.R

/**
 * The [DocumentType] is the official name of the documentType as specified by the mDoc standard
 */
enum class DocumentType (val value: String, val stringResourceId: Int){
    EUPID("eu.europa.ec.eudiw.pid.1", R.string.document_name_eu_pid),
    MDL("org.iso.18013.5.1.mDL", R.string.document_name_mdl),
    MICOV("org.micov.1", R.string.document_name_micov),
    MVR("nl.rdw.mekb.1", R.string.document_name_mvr)
}