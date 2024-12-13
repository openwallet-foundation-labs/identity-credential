package com.android.identity.device

import com.android.identity.crypto.X509CertChain

/**
 * On Android we create a private key in secure area and use its key attestation as the
 * device attestation.
 */
data class DeviceAttestationAndroid(
    val certificateChain: X509CertChain
) : DeviceAttestation()