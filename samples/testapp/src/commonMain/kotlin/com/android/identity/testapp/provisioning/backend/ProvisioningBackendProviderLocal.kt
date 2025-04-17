package com.android.identity.testapp.provisioning.backend

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.bytestring.encodeToByteString
import org.multipaz.device.Assertion
import org.multipaz.device.DeviceAssertion
import org.multipaz.device.toCbor
import org.multipaz.device.DeviceCheck
import org.multipaz.provisioning.ApplicationSupport
import org.multipaz.provisioning.IssuingAuthority
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.getTable
import org.multipaz.rpc.handler.RpcAuthContext
import org.multipaz.rpc.handler.RpcAuthInspectorAssertion
import org.multipaz.storage.StorageTableSpec
import org.multipaz.testapp.platformSecureAreaProvider
import kotlin.coroutines.CoroutineContext

class ProvisioningBackendProviderLocal: ProvisioningBackendProvider {
    private val lock = Mutex()
    private var backend: ProvisioningBackendLocal? = null
    private var applicationSupport: ApplicationSupportLocal? = null
    private var deviceAttestationId: String? = null
    private val backendEnvironmentLocal = BackendEnvironmentLocal(
        applicationSupportProvider = { applicationSupport!! },
        deviceAssertionMaker = this
    )
    private val coroutineContext = backendEnvironmentLocal + RpcAuthContext(CLIENT_ID)

    override val extraCoroutineContext: CoroutineContext get() = coroutineContext

    override suspend fun getApplicationSupport(): ApplicationSupportLocal {
        init()
        return applicationSupport!!
    }

    override suspend fun getIssuingAuthority(issuingAuthorityId: String): IssuingAuthority {
        init()
        return backend!!.getIssuingAuthority(issuingAuthorityId)
    }

    override suspend fun makeDeviceAssertion(
        assertionFactory: (clientId: String) -> Assertion
    ): DeviceAssertion {
        init()
        return DeviceCheck.generateAssertion(
            secureArea = platformSecureAreaProvider().get(),
            deviceAttestationId = deviceAttestationId!!,
            assertion = assertionFactory(CLIENT_ID)
        )
    }

    private suspend fun init() {
        lock.withLock {
            if (backend != null) {
                return
            }
            val deviceAttestationIdTable = BackendEnvironment.getTable(deviceAttestationLocalStore)
            var deviceAttestation = RpcAuthInspectorAssertion.getClientDeviceAttestation(CLIENT_ID)
            if (deviceAttestation != null) {
                deviceAttestationId = deviceAttestationIdTable.get(CLIENT_ID)!!.decodeToString()
            } else {
                val clientTable =
                    BackendEnvironment.getTable(RpcAuthInspectorAssertion.rpcClientTableSpec)
                val newAttestationResult = DeviceCheck.generateAttestation(
                    secureArea = platformSecureAreaProvider().get(),
                    challenge = CLIENT_ID.encodeToByteString()
                )
                deviceAttestation = newAttestationResult.deviceAttestation
                deviceAttestationId = newAttestationResult.deviceAttestationId
                clientTable.insert(
                    key = CLIENT_ID,
                    data = ByteString(deviceAttestation.toCbor())
                )
                deviceAttestationIdTable.insert(
                    key = CLIENT_ID,
                    data = deviceAttestationId!!.encodeToByteString()
                )
            }
            backend = ProvisioningBackendLocal(CLIENT_ID)
            applicationSupport = ApplicationSupportLocal(CLIENT_ID)
        }
    }

    companion object {
        private val deviceAttestationLocalStore = StorageTableSpec(
            name = "DeviceAttestationLocal",
            supportPartitions = false,
            supportExpiration = false
        )

        const val CLIENT_ID = "__LOCAL__"
    }
}