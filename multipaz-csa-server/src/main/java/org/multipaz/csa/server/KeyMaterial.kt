package org.multipaz.csa.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.SecretKey
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.server.common.persistentServerKey
import org.multipaz.server.enrollment.ServerIdentity
import org.multipaz.server.enrollment.getServerIdentityCertified

/**
 * Various keys used by the Cloud Secure Area.
 *
 * This is initialized on the first server start and saved in the database. On subsequent server
 * runs, it is read from the database.
 */
data class KeyMaterial(
    val serverSecureAreaBoundKey: SecretKey,
    val attestationKey: AsymmetricKey.X509Certified
) {
    companion object {
        fun create(backendEnvironment: Deferred<BackendEnvironment>): Deferred<KeyMaterial> {
            return CoroutineScope(Dispatchers.Default).async {
                withContext(backendEnvironment.await()) {
                    val attestationSigningKey = getServerIdentityCertified(ServerIdentity.KEY_ATTESTATION)
                    KeyMaterial(
                        attestationKey = attestationSigningKey,
                        serverSecureAreaBoundKey = SecretKey(persistentServerKey(name = "csa").toByteArray())
                    )
                }
            }
        }
    }
}

