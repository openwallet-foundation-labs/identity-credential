package org.multipaz.rpc.handler

/**
 * [SimpleCipher] that does not encrypt or decrypt messages.
 */
object NoopCipher: SimpleCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
    override fun decrypt(ciphertext: ByteArray): ByteArray = ciphertext
}