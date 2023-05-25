package com.android.mdl.app.transfer

import com.android.identity.android.legacy.IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256
import com.android.identity.android.legacy.PresentationSession

class SessionSetup(
    private val credentialStore: CredentialStore
) {

    fun createSession(): PresentationSession {
        val store = credentialStore.createIdentityCredentialStore()
        return store.createPresentationSession(
            CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256
        )
    }
}