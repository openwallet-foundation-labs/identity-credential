package com.android.identity.mdoc.serverretrieval.webapi

import com.android.identity.credentialtype.knowntypes.DrivingLicense
import com.android.identity.mdoc.serverretrieval.Jwt
import com.android.identity.mdoc.serverretrieval.transport.MockTransportLayer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class TestWebApiServerRetrievalProcess {

    private val prettyPrintJson = Json() {
        prettyPrint = true
    }
    private val drivingLicenseInfo = DrivingLicense.getCredentialType().mdocCredentialType!!

    @Test
    fun testClient() {
        // note: here the client communicates directly with the server implementation, without the http layer
        val response = WebApiServerRetrievalProcess(
            WebApiClient("https://utopiadot.gov", MockTransportLayer())
        ).process(
            "Test server retrieval token",
            drivingLicenseInfo.docType,
            // request all data elements...
            drivingLicenseInfo.namespaces.map { ns ->
                ns.key to ns.value.dataElements.map { el -> el.value.attribute.identifier to false }
                    .toMap()
            }.toMap()
        )
        println("Raw result:\n$response")
        for (document in response["documents"] as JsonArray) {
            assert(Jwt.verify(document.jsonPrimitive.content))
            println("Document:\n${prettyPrintJson.encodeToString(Jwt.decode(document.jsonPrimitive.content).payload)}")
        }
    }
}