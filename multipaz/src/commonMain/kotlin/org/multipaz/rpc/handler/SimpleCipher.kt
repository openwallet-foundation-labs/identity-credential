package org.multipaz.rpc.handler

/**
 * Interface that is used by the server to (1) protect its data from the client and
 * (2) serve as authentication mechanism (as the client should not be able to generate
 * valid encrypted data). Thus it is important that encryption algorithm provides both
 * data authenticity and confidentiality.
 */
interface SimpleCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray): ByteArray

    class DataTamperedException : IllegalArgumentException()
}