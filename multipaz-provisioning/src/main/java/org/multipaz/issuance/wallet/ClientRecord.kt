package org.multipaz.issuance.wallet

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.device.DeviceAttestation

/**
 * Data that we keep for each device that ever connected to this wallet server.
 *
 * It is created and stored (identified by the `clientId`) the first time the new device is seen.
 * When the device wishes to connect to the server again, it must prove that it still possesses the
 * private key that was used initially. If the key is lost (e.g. wallet app is moved to another
 * device or its storage erased), the app is treated as a new client.
 */
@CborSerializable
data class ClientRecord(
    val deviceAttestation: DeviceAttestation
) {
    companion object
}