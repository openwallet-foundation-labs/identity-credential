package org.multipaz.server.openid4vci

import org.multipaz.flow.server.Configuration
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class WellKnownOpenidCredentialIssuerServlet : BaseServlet() {
    companion object {
        const val PREFIX = "openid4vci.issuer"
    }
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val configuration = environment.getInterface(Configuration::class)!!
        val baseUrl = this.baseUrl
        resp.writer.write(buildJsonObject {
            put("credential_issuer", JsonPrimitive(baseUrl))
            put("credential_endpoint", JsonPrimitive("$baseUrl/credential"))
            put("authorization_servers", buildJsonArray {
                add(JsonPrimitive(baseUrl))
            })
            put("display", buildJsonArray {
                add(buildJsonObject {
                    put("name", JsonPrimitive("Open Wallet Sample Issuer"))
                    put("locale", JsonPrimitive("en-US"))
                    put("logo", buildJsonObject {
                        put("uri", JsonPrimitive("$baseUrl/logo.png"))
                    })
                })
            })
            put("batch_credential_issuance", buildJsonObject {
                val batchSize =
                    configuration.getValue("$PREFIX.batch_size")?.toIntOrNull() ?: 12
                put("batch_size", JsonPrimitive(batchSize))
            })
            put("credential_configurations_supported", buildJsonObject {
                for (credentialFactory in CredentialFactory.byOfferId.values) {
                    put(credentialFactory.offerId, buildJsonObject {
                        put("scope", JsonPrimitive(credentialFactory.scope))
                        val format = credentialFactory.format
                        put("format", JsonPrimitive(format.id))
                        when (format) {
                            is Openid4VciFormatMdoc -> put("doctype", JsonPrimitive(format.docType))
                            is Openid4VciFormatSdJwt -> put("vct", JsonPrimitive(format.vct))
                        }
                        if (credentialFactory.proofSigningAlgorithms.isNotEmpty()) {
                            put("proof_types_supported", buildJsonObject {
                                if (CredentialServlet.isStandaloneProofOfPossessionAccepted(
                                        environment
                                    )
                                ) {
                                    put("jwt", buildJsonObject {
                                        put(
                                            "proof_signing_alg_values_supported",
                                            JsonArray(credentialFactory.proofSigningAlgorithms.map {
                                                JsonPrimitive(it)
                                            })
                                        )
                                    })
                                }
                                put("attestation", buildJsonObject {
                                    put("proof_signing_alg_values_supported",
                                        JsonArray(credentialFactory.proofSigningAlgorithms.map {
                                            JsonPrimitive(it)
                                        })
                                    )
                                })
                            })
                        }
                        if (credentialFactory.cryptographicBindingMethods.isNotEmpty()) {
                            put("cryptographic_binding_methods_supported",
                                JsonArray(credentialFactory.cryptographicBindingMethods.map {
                                    JsonPrimitive(it)
                                })
                            )
                        }
                        put("credential_signing_alg_values_supported",
                            JsonArray(credentialFactory.credentialSigningAlgorithms.map {
                                JsonPrimitive(it)
                            }))
                        put("display", buildJsonArray {
                            add(buildJsonObject {
                                put("name", JsonPrimitive(credentialFactory.name))
                                put("locale", JsonPrimitive("en-US"))
                                if (credentialFactory.logo != null) {
                                    put("logo", buildJsonObject {
                                        put("uri", JsonPrimitive("$baseUrl/${credentialFactory.logo}"))
                                    })
                                }
                            })
                        })
                    })
                }
            })
        }.toString())
    }
}