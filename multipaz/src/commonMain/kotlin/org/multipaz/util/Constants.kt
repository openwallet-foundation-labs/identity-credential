/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.multipaz.util

/**
 * Various constants used by other classes.
 *
 * TODO: Get rid of this. Move each item to relevant classes/interfaces.
 */
object Constants {
    /**
     * Normal processing. This status message shall be
     * returned if no other status is returned.
     *
     *
     * This value is defined in ISO/IEC 18013-5 Table 8.
     */
    const val DEVICE_RESPONSE_STATUS_OK: Long = 0

    /**
     * The mdoc returns an error without any given
     * reason. No data is returned.
     *
     *
     * This value is defined in ISO/IEC 18013-5 Table 8.
     */
    const val DEVICE_RESPONSE_STATUS_GENERAL_ERROR: Long = 10

    /**
     * The mdoc indicates an error during CBOR decoding
     * that the data received is not valid CBOR. Returning
     * this status code is optional.
     *
     *
     * This value is defined in ISO/IEC 18013-5 Table 8.
     */
    const val DEVICE_RESPONSE_STATUS_CBOR_DECODING_ERROR: Long = 11

    /**
     * The mdoc indicates an error during CBOR
     * validation, e.g. wrong CBOR structures. Returning
     * this status code is optional.
     *
     *
     * This value is defined in ISO/IEC 18013-5 Table 8.
     */
    const val DEVICE_RESPONSE_STATUS_CBOR_VALIDATION_ERROR: Long = 12

    /**
     * Flag indicating that the *mdoc central client mode* should be supported
     * for BLE data retrieval.
     */
    const val BLE_DATA_RETRIEVAL_OPTION_MDOC_CENTRAL_CLIENT_MODE = 1 shl 0

    /**
     * Flag indicating that the *mdoc peripheral server mode* should be supported
     * for BLE data retrieval.
     */
    const val BLE_DATA_RETRIEVAL_OPTION_MDOC_PERIPHERAL_SERVER_MODE = 1 shl 1

    /**
     * Flag indicating that L2CAP should be used for data retrieval if available and supported.
     */
    const val BLE_DATA_RETRIEVAL_OPTION_L2CAP = 1 shl 2

    /**
     * Flag indicating that BLE Services Cache should be cleared before service discovery
     * when acting as a GATT Client.
     */
    const val BLE_DATA_RETRIEVAL_CLEAR_CACHE = 1 shl 3

    /**
     * Error: session encryption. The session shall be terminated.
     *
     *
     * This value is defined in ISO/IEC 18013-5 Table 20.
     */
    const val SESSION_DATA_STATUS_ERROR_SESSION_ENCRYPTION: Long = 10

    /**
     * Error: CBOR decoding. The session shall be terminated.
     *
     *
     * This value is defined in ISO/IEC 18013-5 Table 20.
     */
    const val SESSION_DATA_STATUS_ERROR_CBOR_DECODING: Long = 11

    /**
     * Session termination. The session shall be terminated.
     *
     *
     * This value is defined in ISO/IEC 18013-5 Table 20.
     */
    const val SESSION_DATA_STATUS_SESSION_TERMINATION: Long = 20
}