package com.android.identity.mdoc.serverretrieval.oidc

import com.android.identity.credentialtype.knowntypes.DrivingLicense
import com.android.identity.mdoc.serverretrieval.TestKeysAndCertificates
import com.android.identity.mdoc.serverretrieval.transport.MockTransportLayer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class TestOidcServerRetrievalProcess {

    private val drivingLicenseInfo = DrivingLicense.getCredentialType().mdocCredentialType!!
    private val prettyPrintJson = Json() {
        prettyPrint = true
    }

    @Test
    fun testServerRetrievalProcess() {
        // note: here the client communicates directly with the server implementation, without the http layer
        val baseUrl = "https://utopiadot.gov"
        val response = OidcServerRetrievalProcess(
            OidcClient(baseUrl, MockTransportLayer()), TestKeysAndCertificates.clientPrivateKey
        ).process("Test server retrieval token",
            drivingLicenseInfo.docType,
            // request all data elements...
            drivingLicenseInfo.namespaces.map { ns ->
                ns.key to ns.value.dataElements.map { el -> el.value.attribute.identifier to false }
                    .toMap()
            }.toMap()
        )
        assert(response.isNotEmpty())
        println(prettyPrintJson.encodeToString(response))
    }
}