package com.android.identity.android.securearea

import com.android.identity.crypto.X509CertChain
import com.android.identity.securearea.KeyAttestation

class AndroidKeystoreKeyAttestation(
    val certificateChain: X509CertChain
): KeyAttestation(certificateChain.certificates[0].ecPublicKey)