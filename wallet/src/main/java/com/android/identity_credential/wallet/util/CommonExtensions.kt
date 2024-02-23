package com.android.identity_credential.wallet.util

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import com.android.identity.credentialtype.CredentialTypeRepository

/**
 * Attempt to get a ComponentActivity or Activity from a context
 */
fun Context.getActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

/**
 * Extension function for extracting the display name for an element name in a CredentialRequest.DataElement
 */
fun CredentialTypeRepository.stringValueFor(
    docType: String,
    namespace: String,
    elementName: String
) = getMdocCredentialType(docType)?.namespaces
    ?.get(namespace)?.dataElements?.get(elementName)
    ?.attribute?.displayName
    ?: elementName