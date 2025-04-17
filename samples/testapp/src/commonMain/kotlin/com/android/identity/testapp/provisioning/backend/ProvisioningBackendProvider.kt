package com.android.identity.testapp.provisioning.backend

import kotlinx.coroutines.withContext
import org.multipaz.device.DeviceAssertionMaker
import org.multipaz.provisioning.ApplicationSupport
import org.multipaz.provisioning.IssuingAuthority
import org.multipaz.provisioning.ProvisioningBackend
import kotlin.coroutines.CoroutineContext

/**
 * Interface that acquires and manages an instance of [ProvisioningBackend].
 *
 * Note: currently a direct access to [ProvisioningBackend] is not required, so this interface
 * proxies access to it, caching information as needed.
 */
interface ProvisioningBackendProvider: DeviceAssertionMaker {
    /**
     * Provides required [CoroutineContext] data to communicate with the objects returned by
     * this interface or acquired through them.
     *
     * Use [withContext] function to add this to the existing coroutine context.
     */
    val extraCoroutineContext: CoroutineContext

    suspend fun getApplicationSupport(): ApplicationSupport
    suspend fun getIssuingAuthority(issuingAuthorityId: String): IssuingAuthority
}

/**
 * Creates an Issuing Authority by the [credentialIssuerUri] and [credentialConfigurationId],
 * caching instances. If unable to connect, suspend and wait until connecting is possible.
 */
suspend fun ProvisioningBackendProvider.createOpenid4VciIssuingAuthorityByUri(
    credentialIssuerUri:String,
    credentialConfigurationId: String
): IssuingAuthority {
    // Not allowed per spec, but double-check, so there are no surprises.
    check(credentialIssuerUri.indexOf('#') < 0)
    check(credentialConfigurationId.indexOf('#') < 0)
    val id = "openid4vci#$credentialIssuerUri#$credentialConfigurationId"
    return getIssuingAuthority(id)
}

