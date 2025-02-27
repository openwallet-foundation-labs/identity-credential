package com.android.identity.flow.handler

import kotlinx.io.bytestring.ByteString

/**
 * Interface that is used by the server to (1) protect its data from the client and
 * (2) serve as authentication mechanism (as the client should not be able to generate
 * valid encrypted data). Thus it is important that encryption algorithm provides both
 * data authenticity and confidentiality.
 */
interface SimpleCipher {
    fun encrypt(plaintext: ByteString): ByteString
    fun decrypt(ciphertext: ByteString): ByteString

    class DataTamperedException : IllegalArgumentException()
}