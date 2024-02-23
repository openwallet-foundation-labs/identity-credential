package com.android.identity_credential.wallet.util

import android.os.Parcelable
import com.android.identity.credential.CredentialRequest
import kotlinx.parcelize.Parcelize

/**
 * Parcelable version of CredentialRequest and its DataElement.
 * This class is produced when sending CredentialRequest as an argument to [WalletDestination.ConsentPrompt]
 * and parsed back into a CredentialRequest object to render ConsentPrompt.
 */
@Parcelize
data class ParcelableCredentialRequest(
    val requestedDataElements: List<ParcelableDataElement>
) : Parcelable {

    @Parcelize
    data class ParcelableDataElement(
        val nameSpaceName: String,
        val dataElementName: String,
        val intentToRetain: Boolean,
        var doNotSend: Boolean = false,
    ) : Parcelable
}


/**
 * Convert a CredentialRequest object into ParcelableCredentialRequest.
 * Used when navigating to the ConsentPrompt dialog via `onNavigate` hoisted function, ex:
 *
 * onNavigate(
 *          WalletDestination.ConsentPrompt
 *          .getRouteWithArguments(
 *              listOf(
 *                  Pair(
 *                      WalletDestination.ConsentPrompt.Argument.CREDENTIAL_REQUEST,
 *                      credentialRequest.parcelize()
 *                  ),
 *                  ...
 *              )
 *           )
 */
fun CredentialRequest.parcelize(): ParcelableCredentialRequest = ParcelableCredentialRequest(
    requestedDataElements.map { dataElement ->
        ParcelableCredentialRequest.ParcelableDataElement(
            nameSpaceName = dataElement.nameSpaceName,
            dataElementName = dataElement.dataElementName,
            intentToRetain = dataElement.intentToRetain,
            doNotSend = dataElement.doNotSend
        )
    }
)

/**
 * "Unparcelize" a Parcelable Credential Request object - convert back to CredentialRequest
 */
fun ParcelableCredentialRequest.unparcelize() = CredentialRequest(
    requestedDataElements.map { parcelableDataElement ->
        CredentialRequest.DataElement(
            nameSpaceName = parcelableDataElement.nameSpaceName,
            dataElementName = parcelableDataElement.dataElementName,
            intentToRetain = parcelableDataElement.intentToRetain,
            doNotSend = parcelableDataElement.doNotSend
        )
    }
)