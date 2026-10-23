package org.multipaz.presentment

import kotlinx.coroutines.test.runTest
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.buildCborArray
import org.multipaz.documenttype.ISO_18013_TRANSACTION_DATA_NAMESPACE
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.PaymentTransaction
import org.multipaz.mdoc.request.TransactionsInfo
import org.multipaz.mdoc.request.buildDeviceRequestFromDcql
import org.multipaz.utopia.knowntypes.PingTransaction
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsentDataTest {

    private suspend fun consentData(withPing: Boolean): ConsentData {
        val harness = DocumentStoreTestHarness()
        harness.initialize()
        harness.provisionMdoc(
            displayName = "my-mDL",
            docType = DrivingLicense.MDL_DOCTYPE,
            data = mapOf(
                DrivingLicense.MDL_NAMESPACE to listOf(
                    "given_name" to Tstr("David")
                )
            ),
            keyAuthorizedNamespaces = listOf(ISO_18013_TRANSACTION_DATA_NAMESPACE)
        )
        val transactions = if (withPing) {
            val ping = PingTransaction.serializeIso18013Request(PingTransaction.Payload("hello", null))
            mapOf("mdl" to TransactionsInfo(mapOf(PingTransaction.iso18013RequestInfoIdentifier to ping)))
        } else {
            emptyMap()
        }
        val deviceRequest = buildDeviceRequestFromDcql(
            sessionTranscript = buildCborArray { add("session"); add("transcript") },
            dcqlString = """
                {
                  "credentials": [
                    {
                      "id": "mdl",
                      "format": "mso_mdoc",
                      "meta": {
                        "doctype_value": "${DrivingLicense.MDL_DOCTYPE}"
                      },
                      "claims": [
                        {"id": "a", "path": ["${DrivingLicense.MDL_NAMESPACE}", "given_name"]}
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            transactions = transactions
        )
        return ConsentData.fromCredentialQueryResult(
            credentialQueryResult = deviceRequest.execute(presentmentSource = harness.presentmentSource),
            source = harness.presentmentSource
        )
    }

    @Test
    fun containsTransactionTypeFindsCarriedType() = runTest {
        assertTrue(consentData(withPing = true).containsTransactionType(PingTransaction.identifier))
    }

    @Test
    fun containsTransactionTypeIgnoresOtherTypes() = runTest {
        assertFalse(consentData(withPing = true).containsTransactionType(PaymentTransaction.identifier))
    }

    @Test
    fun containsTransactionTypeWithoutTransactionData() = runTest {
        assertFalse(consentData(withPing = false).containsTransactionType(PingTransaction.identifier))
    }
}
