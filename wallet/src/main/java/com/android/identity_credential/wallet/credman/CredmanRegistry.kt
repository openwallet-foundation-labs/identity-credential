package com.android.identity_credential.wallet.credman

import android.content.Context
import android.graphics.BitmapFactory
import com.android.identity.cbor.Cbor
import com.android.identity.credential.CredentialStore
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.issuance.CredentialExtensions.credentialConfiguration
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.SelfSignedMdlIssuingAuthority
import com.android.identity_credential.wallet.WalletApplication
import com.android.mdl.app.credman.IdentityCredentialEntry
import com.android.mdl.app.credman.IdentityCredentialField
import com.android.mdl.app.credman.IdentityCredentialRegistry
import com.google.android.gms.identitycredentials.IdentityCredentialManager
import java.util.Locale

private const val TAG = "CredmanRegistry"

object CredmanRegistry {

    private fun getDataElementDisplayName(
        credentialTypeRepository: CredentialTypeRepository,
        docTypeName: String,
        nameSpaceName: String,
        dataElementName: String
    ): String {
        val credType = credentialTypeRepository.getCredentialTypeForMdoc(docTypeName)
        if (credType != null) {
            val mdocDataElement = credType.mdocCredentialType!!
                .namespaces[nameSpaceName]?.dataElements?.get(dataElementName)
            if (mdocDataElement != null) {
                return mdocDataElement.attribute.displayName
            }
        }
        return dataElementName
    }

    fun registerCredentials(
        context: Context,
        credentialStore: CredentialStore,
        credentialTypeRepository: CredentialTypeRepository
    ) {
        var idCount = 0L
        val entries = credentialStore.listCredentials().map { credentialId ->

            val credential = credentialStore.lookupCredential(credentialId)!!
            val credConf = credential.credentialConfiguration

            val credentialType = credentialTypeRepository.getCredentialTypeForMdoc(credConf.mdocDocType)

            val fields = mutableListOf<IdentityCredentialField>()
            fields.add(
                IdentityCredentialField(
                    name = "doctype",
                    value = credConf.mdocDocType,
                    displayName = "Document Type",
                    displayValue = credConf.mdocDocType
                )
            )

            val nameSpacedData = credConf.staticData
            nameSpacedData.nameSpaceNames.map { nameSpaceName ->
                nameSpacedData.getDataElementNames(nameSpaceName).map { dataElementName ->
                    val fieldName = nameSpaceName + "." + dataElementName
                    val valueCbor = nameSpacedData.getDataElement(nameSpaceName, dataElementName)

                    val mdocDataElement = credentialType?.mdocCredentialType?.namespaces
                        ?.get(nameSpaceName)?.dataElements?.get(dataElementName)
                    val valueString = mdocDataElement
                        ?.renderValue(
                            value = Cbor.decode(valueCbor),
                            trueFalseStrings = Pair(
                                context.resources.getString(R.string.card_details_boolean_false_value),
                                context.resources.getString(R.string.card_details_boolean_true_value)
                            )
                        )
                        ?: Cbor.toDiagnostics(valueCbor)

                    val dataElementDisplayName = getDataElementDisplayName(
                        credentialTypeRepository,
                        credConf.mdocDocType,
                        nameSpaceName,
                        dataElementName
                    )
                    fields.add(
                        IdentityCredentialField(
                            name = fieldName,
                            value = valueString,
                            displayName = dataElementDisplayName,
                            displayValue = valueString
                        )
                    )
                    Logger.i(TAG, "Adding field $fieldName ('$dataElementDisplayName') with value '$valueString'")
                }
            }

            val options = BitmapFactory.Options()
            options.inMutable = true
            val credBitmap = BitmapFactory.decodeByteArray(
                credConf.cardArt,
                0,
                credConf.cardArt.size,
                options
            )


            Logger.i(TAG, "Adding document ${credConf.displayName}")
            IdentityCredentialEntry(
                id = idCount++,
                format = "mdoc",
                title = credConf.displayName,
                subtitle = context.getString(R.string.app_name),
                icon = credBitmap,
                fields = fields.toList(),
                disclaimer = null,
                warning = null,
            )
        }
        val registry = IdentityCredentialRegistry(entries)
        val client = IdentityCredentialManager.Companion.getClient(context)
        client.registerCredentials(registry.toRegistrationRequest(context))
            .addOnSuccessListener { Logger.i(TAG, "CredMan registry succeeded") }
            .addOnFailureListener { Logger.i(TAG, "CredMan registry failed $it") }

    }
}