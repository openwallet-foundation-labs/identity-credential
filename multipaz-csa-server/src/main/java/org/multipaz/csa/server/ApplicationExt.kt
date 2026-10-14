package org.multipaz.csa.server

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.multipaz.asn1.ASN1
import org.multipaz.device.AndroidKeystoreSecurityLevel
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration
import org.multipaz.securearea.cloud.CloudSecureAreaServer
import org.multipaz.securearea.cloud.SimplePassphraseFailureEnforcer
import org.multipaz.server.common.ServerEnvironment
import org.multipaz.server.request.certificateAuthority
import org.multipaz.server.request.push
import org.multipaz.server.request.rpc
import org.multipaz.storage.Storage
import org.multipaz.util.Logger
import java.security.Security
import kotlin.time.Duration.Companion.seconds

/**
 * Defines server endpoints for HTTP GET and POST.
 */
fun Application.configureRouting(serverEnvironment: Deferred<ServerEnvironment>) {
    Logger.i(TAG, "Forcing BouncyCastle to the top of the list")
    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
    Security.insertProviderAt(BouncyCastleProvider(), 1)
    val keyMaterial = lazy {
        KeyMaterial.create(serverEnvironment)
    }
    val cloudSecureArea = lazy {
        createCloudSecureArea(serverEnvironment, keyMaterial.value)
    }
    val httpHandler = lazy {
        CoroutineScope(Dispatchers.Default).async {
            cloudSecureArea.value.await().httpHandler
        }
    }
    CoroutineScope(Dispatchers.IO).launch {
        withContext(serverEnvironment.await()) {
            keyMaterial.value.await()
            cloudSecureArea.value.await()
        }
    }
    routing {
        push(serverEnvironment)
        certificateAuthority()
        rpc("", httpHandler.value)
        get("/") {
            handleGet(call, keyMaterial.value.await())
        }
    }
}

private const val TAG = "ApplicationExt"

private suspend fun handleGet(
    call: ApplicationCall,
    keyMaterial: KeyMaterial
) {
    val sb = StringBuilder()
    sb.append(
        """
            <!DOCTYPE html>
            <html>
            <head>
              <title>Cloud Secure Area - Server Reference Implementation</title>
            </head>
            <body>
            <h1>Cloud Secure Area - Server Reference Implementation</h1>
            <p><b>Note: This reference implementation is not production quality. Use at your own risk.</b></p>            
            <h2>Attestation Root</h2>
    """.trimIndent()
    )

    for (certificate in keyMaterial.attestationKey.certChain.certificates) {
        sb.append("<h3>Certificate</h3>")
        sb.append("<pre>")
        sb.append(ASN1.print(ASN1.decode(certificate.tbsCertificate)!!))
        sb.append("</pre>")
    }
    sb.append(
        """
    </body>
    </html>
    """.trimIndent()
    )
    call.respondText(
        contentType = ContentType.Text.Html,
        text = sb.toString()
    )
}

private fun createCloudSecureArea(
    backendEnvironmentDeferred: Deferred<BackendEnvironment>,
    keyMaterialDeferred: Deferred<KeyMaterial>
): Deferred<CloudSecureAreaServer> = CoroutineScope(Dispatchers.Default).async {
    val backendEnvironment = backendEnvironmentDeferred.await()
    val keyMaterial = keyMaterialDeferred.await()
    val settings = CloudSecureAreaSettings(backendEnvironment.getInterface(Configuration::class)!!)
    CloudSecureAreaServer(
        serverSecureAreaBoundKey = keyMaterial.serverSecureAreaBoundKey,
        attestationKey = keyMaterial.attestationKey,
        iosReleaseBuild = settings.iosReleaseBuild,
        iosAppIdentifiers = settings.iosAppIdentifiers,
        androidGmsAttestation = settings.androidRequireGmsAttestation,
        androidVerifiedBootGreen = settings.androidRequireVerifiedBootGreen,
        androidAppSignatureCertificateDigests = settings.androidRequireAppSignatureCertificateDigests,
        androidAppPackageNames = settings.androidRequireAppPackageNames,
        androidKeystoreSecurityLevel = when (settings.androidRequireKeystoreSecurityLevel) {
            "strong_box" -> AndroidKeystoreSecurityLevel.STRONG_BOX
            "software" -> AndroidKeystoreSecurityLevel.SOFTWARE
            else -> AndroidKeystoreSecurityLevel.TRUSTED_ENVIRONMENT
        },
        openid4vciKeyAttestationIssuer = settings.openid4vciKeyAttestationIssuer,
        openid4vciKeyAttestationKeyStorage = settings.openid4vciKeyAttestationKeyStorage,
        openid4vciKeyAttestationUserAuthentication = settings.openid4vciKeyAttestationUserAuthentication,
        openid4vciKeyAttestationUserAuthenticationNoPassphrase = settings.openid4vciKeyAttestationUserAuthenticationNoPassphrase,
        openid4vciKeyAttestationCertification = settings.openid4vciKeyAttestationCertification,
        passphraseFailureEnforcer = SimplePassphraseFailureEnforcer(
            settings.cloudSecureAreaLockoutNumFailedAttempts,
            settings.cloudSecureAreaLockoutDurationSeconds.seconds
        ),
        storage = backendEnvironment.getInterface(Storage::class)!!,
    )
}


