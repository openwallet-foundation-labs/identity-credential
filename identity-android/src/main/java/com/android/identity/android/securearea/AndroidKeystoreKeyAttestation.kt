package com.android.identity.android.securearea

import com.android.identity.crypto.X509CertificateChain
import com.android.identity.securearea.KeyAttestation

class AndroidKeystoreKeyAttestation(
    val certificateChain: X509CertificateChain
): KeyAttestation(certificateChain.certificates[0].ecPublicKey)