/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.security.identity;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import androidx.annotation.RestrictTo;

import java.lang.annotation.Retention;

public class Constants {

    /**
     * Normal processing. This status message shall be
     * returned if no other status is returned.
     */
    public static final int DEVICE_RESPONSE_STATUS_OK = 0;
    /**
     * The mdoc returns an error without any given
     * reason. No data is returned.
     */
    public static final int DEVICE_RESPONSE_STATUS_GENERAL_ERROR = 10;
    /**
     * The mdoc indicates an error during CBOR decoding
     * that the data received is not valid CBOR. Returning
     * this status code is optional.
     */
    public static final int DEVICE_RESPONSE_STATUS_CBOR_DECODING_ERROR = 11;
    /**
     * The mdoc indicates an error during CBOR
     * validation, e.g. wrong CBOR structures. Returning
     * this status code is optional.
     */
    public static final int DEVICE_RESPONSE_STATUS_CBOR_VALIDATION_ERROR = 12;

    /**
     * The status code of the document response.
     *
     * @hide
     */
    @Retention(SOURCE)
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @IntDef({DEVICE_RESPONSE_STATUS_OK,
            DEVICE_RESPONSE_STATUS_GENERAL_ERROR,
            DEVICE_RESPONSE_STATUS_CBOR_DECODING_ERROR,
            DEVICE_RESPONSE_STATUS_CBOR_VALIDATION_ERROR})
    public @interface DeviceResponseStatus {
    }

    /**
     * If this flag is set, {@link PresentationHelper} and {@link VerificationHelper}
     * will log informational messages.
     */
    public static final int LOGGING_FLAG_INFO = (1<<0);

    /**
     * If this flag is set, {@link PresentationHelper} and {@link VerificationHelper}
     * will log messages related to Device Engagement.
     */
    public static final int LOGGING_FLAG_DEVICE_ENGAGEMENT = (1<<1);

    /**
     * If this flag is set, {@link PresentationHelper} and {@link VerificationHelper}
     * will log messages related to session layer encryption including the
     * the hexadecimal representation of the cleartext messages in {@code SessionData}
     * and {@code SessionEstablishment} CBOR messages that are sent and received.
     *
     * <p>This might generate a lot of data.
     */
    public static final int LOGGING_FLAG_SESSION_MESSAGES = (1<<2);

    /**
     * If this flag is set, {@link PresentationHelper} and {@link VerificationHelper}
     * will log messages that are transport specific except for the transport-specific
     * packets.
     */
    public static final int LOGGING_FLAG_TRANSPORT_SPECIFIC = (1<<3);

    /**
     * If this flag is set, {@link PresentationHelper} and {@link VerificationHelper}
     * will log transport-specific data packets, for example APDUs for NFC transport.
     *
     * <p>This might generate a lot of data.
     */
    public static final int LOGGING_FLAG_TRANSPORT_SPECIFIC_VERBOSE = (1<<4);

    /**
     * Logging flags.
     *
     * @hide
     */
    @Retention(SOURCE)
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @IntDef(
        flag=true,
        value={
            LOGGING_FLAG_INFO,
            LOGGING_FLAG_DEVICE_ENGAGEMENT,
            LOGGING_FLAG_SESSION_MESSAGES,
            LOGGING_FLAG_TRANSPORT_SPECIFIC,
            LOGGING_FLAG_TRANSPORT_SPECIFIC_VERBOSE
        })
    public @interface LoggingFlag {
    }

}
