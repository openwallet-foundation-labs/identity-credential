package com.android.identity.securearea.cloud

import com.android.identity.cbor.CborArray
import com.android.identity.crypto.CertificateChain
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPublicKey
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.cloud.Protocol.RegisterRequest1

/**
 * This describes the protocol (messages and constants) between the client and the server
 * for the Cloud Secure Area.
 *
 * Throughout, the term _client_ is used to refer to an Android application acting as a
 * wallet and using a [SecureArea] implementation which is implementing this protocol (e.g.
 * the [CloudSecureArea] implementation in the `identity-android` library. Similarly, the
 * term _server_ is used to refer to server-side of the Cloud Secure Area and _Secure Area_
 * is used to refer to the part of this that may be implemented in an isolated execution
 * environment with additional protections.
 *
 * All messages in the protocol are encoded using [CBOR](https://datatracker.ietf.org/doc/html/rfc8949)
 * and is described here according to [CDDL](https://datatracker.ietf.org/doc/rfc8610/).
 * Messages are exchanged using the HTTP protocol (HTTPS), using HTTP POST. It is not
 * required that the client supports HTTP cookies.
 *
 * One trait shared by all messages is that they are all arrays and the first element is a
 * `tstr` used to identify which message it is, for example `"RegisterRequest0"` or `"E2EEResponse"`.
 *
 * The protocol consists of several flows
 * - Device Registration
 *   - This includes the device and the server exchanging keys (`DeviceBindingKeu` and
 *     `CloudBindingKey`) to be used to bootstrap end-to-end encryption between the two parties.
 *     Each key has an attestation to allow both parties to authenticate each other.
 *   - The server authenticates the device using Android Keystore Attestation which enables
 *   restricting the service to e.g. a subset of Android applications only running
 *   on devices with verified boot state GREEN.
 *   - The device authenticates the server by inspecting the root certificate of the
 *   attestation and making sure it is well known.
 *   - Detailed description in [RegisterRequest0], [RegisterResponse0], [RegisterRequest1],
 *   and [RegisterResponse1].
 * - E2EE Setup
 *   - This involves the device and the server exchanging ephemeral keys (`EDeviceKey`
 *   and `ECloudKey`) which are signed by `DeviceBindingKey` and `CloudBindingKey`.
 *   These keys are used to derive symmetric encryption keys (`SKDevice` and `SKCloud`)
 *   used for AES-GCM-128 encryption.
 *   - The derived symmetric encryption keys are intended to only valid for a finite amount
 *   of time, 10 minutes for example. This is enforced by the server asking the device
 *   to go through the E2EE Setup flow again.
 *   - Detailed description for the E2EE setup flow is in [E2EESetupRequest0],
 *   [E2EESetupResponse0], [E2EESetupRequest1], and [E2EESetupResponse1]. See [E2EERequest]
 *   and [E2EEResponse] for how the symmetric encryption keys are used.
 *
 * The following flows are all implemented on top of E2EE encruption.
 * - Create Key
 *   - For every key created on the server, a companion key on the local device will
 *     be created. The local key is used to enforce User Authentication requirements
 *     e.g. biometric and Lock Screen Knowledge Factor.
 *   - See [CreateKeyRequest0], [CreateKeyResponse0], [CreateKeyRequest1], [CreateKeyResponse1]
 *   for a detailed description of this flow.
 * - Sign with Key
 *   - See [SignRequest0], [SignResponse0], [SignRequest1], [SignResponse1] for a detailed
 *   description of this flow.
 * - Key Agreement with Key
 *   - See [KeyAgreementRequest0], [KeyAgreementResponse0], [KeyAgreementRequest1],
 *   [KeyAgreementResponse1] for a detailed description of this flow.
 *
 * The protocol is designed so it's possible to implement a 100% stateless server except
 * that the server needs to possess a symmetric encryption key which never leaves the
 * Secure Area of the server. This key is used to encrypt/decrypt state shipped back
 * and forth to the client.
 *
 * Messages shall be sent from the device to the server as the body of a HTTP POST
 * request and messages from the server to the device shall be in the body of the
 * response. The server shall use HTTP status 200 for responses unless otherwise
 * specified.
 */
object Protocol {

    const val RESULT_OK = 0

    const val RESULT_WRONG_PASSPHRASE = 1

    const val RESULT_TOO_MANY_PASSPHRASE_ATTEMPTS = 2

    /**
     * This is the first message in the Registration flow and it's sent by
     * the client to the server the first time the client wishes to communicate
     * with the server. The message consists of the bytes of CBOR conforming to
     * the following CDDL:
     * ```
     * RegisterRequest0 = [
     *   "RegisterRequest0"
     * ]
     * ```
     * The device will send this to the server.
     */
    class RegisterRequest0 {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .generate()
        }

        companion object {
            const val COMMAND = "RegisterRequest0"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): RegisterRequest0 {
                MessageParser(encodedData, COMMAND)
                return RegisterRequest0()
            }
        }
    }

    /**
     * This is sent in response to [RegisterRequest0] and consists of the bytes
     * of CBOR conforming to the following CDDL:
     * ```
     * RegisterResponse0 = [
     *   "RegisterResponse0",
     *   bstr,                   ; cloudChallenge
     *   bstr                    ; serverState
     * ]
     * ```
     * The server generates `cloudChallenge` and stores it in session state. The
     * `serverState` parameter can be used by the server to store encrypted serialized
     * state or an identifier of the session.
     *
     * Upon receiving the message, the device shall create `DeviceBindingKey` - an ECC
     * key using curve P-256 - in hardware-backed Android Keystore using `cloudChallenge`
     * as the challenge to be included in the attestation. The device proceeds to prepare
     * a [RegisterResponse0] message.
     */
    class RegisterResponse0(@JvmField val cloudChallenge: ByteArray, @JvmField val serverState: ByteArray) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(cloudChallenge)
                .add(serverState)
                .generate()
        }

        companion object {
            const val COMMAND = "RegisterResponse0"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): RegisterResponse0 {
                val mp = MessageParser(encodedData, COMMAND)
                return RegisterResponse0(
                    mp.byteString!!,
                    mp.byteString!!
                )
            }
        }
    }

    /**
     * This is sent in response to [RegisterResponse0] and consists of the bytes
     * of CBOR conforming to the following CDDL:
     * ```
     * RegisterRequest1 = [
     *   "RegisterRequest1",
     *   bstr,                   ; deviceChallenge
     *   [ bstr ],               ; deviceBindingKeyAttestation
     *   bstr,                   ; serverState
     * ]
     * ```
     * The device includes `deviceBindingKeyAttestation` (which is a list of encoded
     * X509 certificates) and `serverState` described in [RegisterRequest0]. The device
     * also generates `deviceChallenge` which is to be included in this message and also
     * stored in the device's session state.
     *
     * Upon receiving this message the server shall check `deviceBindingKeyAttestation`
     * which is an [Android Keystore attestation](https://developer.android.com/training/articles/security-key-attestation)
     * and this include checking that the previously sent `cloudChallenge` is present
     * in the Android Attestation Extension, that verified boot is in state GREEN, that
     * the expected Android application requested the creation of the key, the root
     * public key is well-known (e.g. the Google root), and so on. If a check fails
     * the server shall return HTTP status code 403 (Forbidden).
     *
     * After this, the server shall create `DeviceBindingKey` - an EC key using curve
     * P-256 - using `deviceChallenge` as the challenge to be included in the attestation.
     * The attestation format to be used is defined in this protocol, see [Attestation]
     * for the encoding of the attestation extension and the OID to include it at.
     *
     * The server proceeds to prepare a [RegisterResponse1] message.
     */
    class RegisterRequest1(
        @JvmField val deviceChallenge: ByteArray,
        @JvmField val deviceBindingKeyAttestation: CertificateChain,
        @JvmField val serverState: ByteArray
    ) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(deviceChallenge)
                .add(deviceBindingKeyAttestation)
                .add(serverState)
                .generate()
        }

        companion object {
            const val COMMAND = "RegisterRequest1"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): RegisterRequest1 {
                val mp = MessageParser(encodedData, COMMAND)
                return RegisterRequest1(
                    mp.byteString!!,
                    mp.certificateChain!!,
                    mp.byteString!!
                )
            }
        }
    }

    /**
     * This is sent in response to [RegisterRequest1] and consists of the bytes
     * of CBOR conforming to the following CDDL:
     * ```
     * RegisterResponse1 = [
     *   "RegisterResponse1",
     *   [ bstr ],               ; cloudBindingKeyAttestation
     *   bstr,                   ; serverState
     * ]
     * ```
     * The server includes `cloudBindingKeyAttestation` (which is a list of encoded
     * X509 certificates) and `serverState` described in [RegisterRequest1].
     *
     * When the device receives this message it checks that `cloudBindingKeyAttestation`
     * includes the previously sent `deviceChallenge` in its attestation, that each certificate
     * is signed by the next one, the root public key is well-known (e.g. a the root of the
     * Cloud Secure Area provider), and so on. If a check fails the device shall discard
     * any received state and report an error to the application.
     *
     * If successful, the device stores `serverState` locally as "registration context". See
     * [E2EESetupRequest0] for where it's used.
     */
    class RegisterResponse1(
        @JvmField val cloudBindingKeyAttestation: CertificateChain,
        @JvmField val serverState: ByteArray
    ) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(cloudBindingKeyAttestation)
                .add(serverState)
                .generate()
        }

        companion object {
            const val COMMAND = "RegisterResponse1"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): RegisterResponse1 {
                val mp = MessageParser(encodedData, COMMAND)
                return RegisterResponse1(
                    mp.certificateChain!!,
                    mp.byteString!!
                )
            }
        }
    }

    /**
     * This message is sent when the client wishes to set up end-to-end
     * encryption to the Secure Area of the server. It consists of the
     * bytes of CBOR conforming to the following CDDL:
     * ```
     * E2EESetupRequest0 = [
     *   "E2EESetupRequest0",
     *   bstr,                   ; registrationContext
     * ]
     * ```
     * where `registrationContext` is the value obtained from the registration
     * flow, see [RegisterResponse1].
     *
     * After receiving this message, the server responds with a [E2EESetupResponse0] message.
     */
    class E2EESetupRequest0(@JvmField val registrationContext: ByteArray) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(registrationContext)
                .generate()
        }

        companion object {
            const val COMMAND = "E2EESetupRequest0"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): E2EESetupRequest0 {
                val mp = MessageParser(encodedData, COMMAND)
                return E2EESetupRequest0(
                    mp.byteString!!
                )
            }
        }
    }

    /**
     * This is sent in response to [E2EESetupRequest0] and consists of the bytes
     * of CBOR conforming to the following CDDL:
     * ```
     * E2EESetupResponse0 = [
     *   "E2EESetupResponse0",
     *   bstr,                   ; cloudNonce
     *   bstr,                   ; serverState
     * ]
     * ```
     * When the devices receives this message it shall generate `deviceNonce`
     * and create `EDeviceKey` which shall be a EC key using curve P-256. It
     * is not a requirement to use hardware-backed keystore for `EDeviceKey`.
     *
     * The device then uses `DeviceBindingKey` (created at registration time) to
     * create an ECDSA signature over the bytes of the CBOR
     * ```
     * DataSignedByDevice = [
     *   bstr,                   ; EDeviceKey.pub in uncompressed form
     *   batr,                   ; cloudNonce
     *   bstr                    ; deviceNonce
     * ]
     * ```
     * The device proceeds to prepare a [E2EESetupRequest1] message.
     */
    class E2EESetupResponse0(@JvmField val cloudNonce: ByteArray, @JvmField val serverState: ByteArray) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(cloudNonce)
                .add(serverState)
                .generate()
        }

        companion object {
            const val COMMAND = "E2EESetupResponse0"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): E2EESetupResponse0 {
                val mp = MessageParser(encodedData, COMMAND)
                return E2EESetupResponse0(
                    mp.byteString!!,
                    mp.byteString!!
                )
            }
        }
    }

    /**
     * This is sent by the device in response to the [E2EESetupResponse0] and is CBOR
     * encoding formatted according to the following CDDL:
     * ```
     * E2EESetupRequest1 = [
     *   "E2EESetupRequest1",
     *   batr,                   ; EDeviceKey.pub in uncompressed form
     *   bstr                    ; deviceNonce
     *   bstr                    ; signature
     *   bstr                    ; serverState
     * ]
     * ```
     * where `EDeviceKey.pub`, `deviceNonce`, `signature`, and `serverState` are all
     * described in the [E2EESetupResponse0] message.
     *
     * Upon receiving this message the server builds up `DataSignedByDevice` and checks
     * the signature was made by `DeviceBindingKey` (as received during registration phase).
     * If this fails, the server shall return HTTP status code 403 (Forbidden).
     *
     * If the check is successful, the server creates `ECloudKey` which shall be a EC key
     * using curve P-256. The server then uses `CloudBindingKey` (created at registration time)
     * to create an ECDSA signature over the bytes of the CBOR
     * ```
     * DataSignedByServer = [
     *   bstr,                   ; ECloudKey.pub in uncompressed form
     *   bstr                    ; cloudNonce,
     *   bstr                    ; deviceNonce
     * ]
     * ```
     * The server proceeds to prepare a [E2EESetupResponse1] message.
     */
    class E2EESetupRequest1(
        @JvmField val eDeviceKey: EcPublicKey,
        @JvmField val deviceNonce: ByteArray,
        @JvmField val signature: ByteArray,
        @JvmField val serverState: ByteArray
    ) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(eDeviceKey)
                .add(deviceNonce)
                .add(signature)
                .add(serverState)
                .generate()
        }

        companion object {
            const val COMMAND = "E2EESetupRequest1"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): E2EESetupRequest1 {
                val mp = MessageParser(encodedData, COMMAND)
                return E2EESetupRequest1(
                    mp.publicKey!!,
                    mp.byteString!!,
                    mp.byteString!!,
                    mp.byteString!!
                )
            }
        }
    }

    /**
     * This is sent in response to [E2EESetupRequest1] and consists of the bytes
     * of CBOR conforming to the following CDDL:
     * ```
     * E2EESetupResponse1 = [
     *   "E2EESetupResponse1",
     *   bstr,                   ; ECloudKey.pub in uncompressed form
     *   bstr,                   ; signature
     *   bstr                    ; serverState
     * ]
     * ```
     * where `ECloudKey`, `signature`, and `serverState` are all described in
     * the [E2EESetupRequest1] message.
     *
     * Upon receiving this message the device builds up `DataSignedByServer` and checks
     * the signature was made by `CloudBindingKey` (as received during registration phase).
     * If the check fails the device shall discard any received state and report an error
     * to the application.
     *
     * On success, both the server and the device are now able to calculate session
     * encryption keys. Let ZAB be the output of ECKA-DH (Elliptic Curve Key Agreement
     * Algorithm – Diffie-Hellman) as defined in BSI TR-03111 where the inputs shall be
     * `EDeviceKey` and `ECloudKey.pub` on the device side and `ECloudKey` and `EDeviceKey.pub`
     * on the server side.
     *
     * `SKDevice` shall be derived using HKDF as defined in RFC 5869 with the following parameters:
     * - Hash: SHA-256
     * - IKM: ZAB
     * - salt: SHA-256(E2EESetupTranscript)
     * - info: "SKDevice" (encoded as a UTF-8 string without the quotes)
     * - Length: 32 octets
     *
     * `SKCloud` shall be derived using HKDF as defined in RFC 5869 with the following parameters:
     * - Hash: SHA-256
     * - IKM: ZAB
     * - salt: SHA-256(E2EESetupTranscript)
     * - info: "SKCloud" (encoded as a UTF-8 string without the quotes)
     * - Length: 32 octets
     *
     * where `E2EESetupTranscript` is defined as bytes of CBOR conforming to the following CDDL
     * ```
     * E2EESetupTranscript = [
     *   cloudNonce,
     *   deviceNonce
     * ]
     * ```
     */
    class E2EESetupResponse1(
        @JvmField val eCloudKey: EcPublicKey,
        @JvmField val signature: ByteArray,
        @JvmField val serverState: ByteArray
    ) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(eCloudKey)
                .add(signature)
                .add(serverState)
                .generate()
        }

        companion object {
            const val COMMAND = "E2EESetupResponse1"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): E2EESetupResponse1 {
                val mp = MessageParser(encodedData, COMMAND)
                return E2EESetupResponse1(
                    mp.publicKey!!,
                    mp.byteString!!,
                    mp.byteString!!
                )
            }
        }
    }

    class E2EERequest(@JvmField val encryptedRequest: ByteArray, @JvmField val e2eeContext: ByteArray) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(encryptedRequest)
                .add(e2eeContext)
                .generate()
        }

        companion object {
            const val COMMAND = "E2EERequest"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): E2EERequest {
                val mp = MessageParser(encodedData, COMMAND)
                return E2EERequest(
                    mp.byteString!!,
                    mp.byteString!!
                )
            }
        }
    }

    class E2EEResponse(@JvmField val encryptedResponse: ByteArray, @JvmField val e2eeContext: ByteArray) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(encryptedResponse)
                .add(e2eeContext)
                .generate()
        }

        companion object {
            const val COMMAND = "E2EEResponse"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): E2EEResponse {
                val mp = MessageParser(encodedData, COMMAND)
                return E2EEResponse(
                    mp.byteString!!,
                    mp.byteString!!
                )
            }
        }
    }

    class CreateKeyRequest0(
        @JvmField val purposes: Set<KeyPurpose>,
        @JvmField val curve: EcCurve,
        @JvmField val validFromMillis: Long,
        @JvmField val validUntilMillis: Long,
        @JvmField val passphrase: String?,
        @JvmField val challenge: ByteArray
    ) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(KeyPurpose.encodeSet(purposes))
                .add(curve.coseCurveIdentifier)
                .add(validFromMillis)
                .add(validUntilMillis)
                .add(passphrase)
                .add(challenge)
                .generate()
        }

        companion object {
            const val COMMAND = "CreateKeyRequest0"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): CreateKeyRequest0 {
                val mp = MessageParser(encodedData, COMMAND)
                return CreateKeyRequest0(
                    KeyPurpose.decodeSet(mp.long),
                    EcCurve.fromInt(mp.int),
                    mp.long,
                    mp.long,
                    mp.string,
                    mp.byteString!!
                )
            }
        }
    }

    class CreateKeyResponse0(@JvmField val cloudChallenge: ByteArray, @JvmField val serverState: ByteArray) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(cloudChallenge)
                .add(serverState)
                .generate()
        }

        companion object {
            const val COMMAND = "CreateKeyResponse0"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): CreateKeyResponse0 {
                val mp = MessageParser(encodedData, COMMAND)
                return CreateKeyResponse0(
                    mp.byteString!!,
                    mp.byteString!!
                )
            }
        }
    }

    class CreateKeyRequest1(
        @JvmField val localKeyAttestation: CertificateChain,
        @JvmField val serverState: ByteArray
    ) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(localKeyAttestation)
                .add(serverState)
                .generate()
        }

        companion object {
            const val COMMAND = "CreateKeyRequest1"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): CreateKeyRequest1 {
                val mp = MessageParser(encodedData, COMMAND)
                return CreateKeyRequest1(
                    mp.certificateChain!!,
                    mp.byteString!!
                )
            }
        }
    }

    class CreateKeyResponse1(
        @JvmField val remoteKeyAttestation: CertificateChain,
        @JvmField val serverState: ByteArray
    ) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(remoteKeyAttestation)
                .add(serverState)
                .generate()
        }

        companion object {
            const val COMMAND = "CreateKeyResponse1"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): CreateKeyResponse1 {
                val mp = MessageParser(encodedData, COMMAND)
                return CreateKeyResponse1(
                    mp.certificateChain!!,
                    mp.byteString!!
                )
            }
        }
    }

    class SignRequest0(
        val signatureAlgorithm: Int,
        @JvmField val dataToSign: ByteArray,
        @JvmField val keyContext: ByteArray
    ) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(signatureAlgorithm)
                .add(dataToSign)
                .add(keyContext)
                .generate()
        }

        companion object {
            const val COMMAND = "SignRequest0"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): SignRequest0 {
                val mp = MessageParser(encodedData, COMMAND)
                return SignRequest0(
                    mp.int,
                    mp.byteString!!,
                    mp.byteString!!
                )
            }
        }
    }

    class SignResponse0(@JvmField val cloudNonce: ByteArray, @JvmField val serverState: ByteArray) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(cloudNonce)
                .add(serverState)
                .generate()
        }

        companion object {
            const val COMMAND = "SignResponse0"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): SignResponse0 {
                val mp = MessageParser(encodedData, COMMAND)
                return SignResponse0(
                    mp.byteString!!,
                    mp.byteString!!
                )
            }
        }
    }

    class SignRequest1(
        @JvmField val signature: ByteArray,
        @JvmField val passphrase: String?,
        @JvmField val serverState: ByteArray
    ) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(signature)
                .add(passphrase)
                .add(serverState)
                .generate()
        }

        companion object {
            const val COMMAND = "SignRequest1"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): SignRequest1 {
                val mp = MessageParser(encodedData, COMMAND)
                return SignRequest1(
                    mp.byteString!!,
                    mp.string,
                    mp.byteString!!
                )
            }
        }
    }

    class SignResponse1(
        @JvmField val result: Int,
        @JvmField val signature: ByteArray?,
        @JvmField val waitDurationMillis: Long
    ) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(result)
                .add(signature)
                .add(waitDurationMillis)
                .generate()
        }

        companion object {
            const val COMMAND = "SignResponse1"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): SignResponse1 {
                val mp = MessageParser(encodedData, COMMAND)
                return SignResponse1(
                    mp.int,
                    mp.byteString,
                    mp.long
                )
            }
        }
    }

    class KeyAgreementRequest0(@JvmField val otherPublicKey: EcPublicKey, @JvmField val keyContext: ByteArray) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(otherPublicKey)
                .add(keyContext)
                .generate()
        }

        companion object {
            const val COMMAND = "KeyAgreementRequest0"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): KeyAgreementRequest0 {
                val mp = MessageParser(encodedData, COMMAND)
                return KeyAgreementRequest0(
                    mp.publicKey!!,
                    mp.byteString!!
                )
            }
        }
    }

    class KeyAgreementResponse0(@JvmField val cloudNonce: ByteArray, @JvmField val serverState: ByteArray) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(cloudNonce)
                .add(serverState)
                .generate()
        }

        companion object {
            const val COMMAND = "KeyAgreementResponse0"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): KeyAgreementResponse0 {
                val mp = MessageParser(encodedData, COMMAND)
                return KeyAgreementResponse0(
                    mp.byteString!!,
                    mp.byteString!!
                )
            }
        }
    }

    class KeyAgreementRequest1(
        @JvmField val signature: ByteArray,
        @JvmField val passphrase: String?,
        @JvmField val serverState: ByteArray
    ) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(signature)
                .add(passphrase)
                .add(serverState)
                .generate()
        }

        companion object {
            const val COMMAND = "KeyAgreementRequest1"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): KeyAgreementRequest1 {
                val mp = MessageParser(encodedData, COMMAND)
                return KeyAgreementRequest1(
                    mp.byteString!!,
                    mp.string,
                    mp.byteString!!
                )
            }
        }
    }

    class KeyAgreementResponse1(
        @JvmField val result: Int,
        @JvmField val zab: ByteArray?,
        @JvmField val waitDurationMillis: Long
    ) {
        fun toCbor(): ByteArray {
            return MessageGenerator(COMMAND)
                .add(result)
                .add(zab)
                .add(waitDurationMillis)
                .generate()
        }

        companion object {
            const val COMMAND = "KeyAgreementResponse1"
            @JvmStatic
            fun fromCbor(encodedData: ByteArray): KeyAgreementResponse1 {
                val mp = MessageParser(encodedData, COMMAND)
                return KeyAgreementResponse1(
                    mp.int,
                    mp.byteString,
                    mp.long
                )
            }
        }
    }

}