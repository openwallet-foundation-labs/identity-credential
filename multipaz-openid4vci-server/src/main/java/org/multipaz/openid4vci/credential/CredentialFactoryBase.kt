package org.multipaz.openid4vci.credential

import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Resources

/**
 * Common parts of [CredentialFactory] implementation.
 */
internal abstract class CredentialFactoryBase: CredentialFactory {
    final override lateinit var signingCertificateChain: X509CertChain
        private set

    protected lateinit var signingKey: EcPrivateKey

    final override suspend fun initialize() {
        val resources = BackendEnvironment.getInterface(Resources::class)!!
        val cert = X509Cert.fromPem(resources.getStringResource("ds_certificate.pem")!!)
        signingCertificateChain = X509CertChain(listOf(cert))
        signingKey = EcPrivateKey.fromPem(
            resources.getStringResource("ds_private_key.pem")!!,
            cert.ecPublicKey
        )
    }
}