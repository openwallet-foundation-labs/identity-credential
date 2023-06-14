/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.identity.credential;

import androidx.annotation.NonNull;

import com.android.identity.mdoc.mso.StaticAuthDataParser;
import com.android.identity.mdoc.request.DeviceRequestParser;

import java.util.List;

/**
 * Credential request.
 *
 * <p>This type is an abstraction of a request from a remote reader requesting one or more
 * pieces of data. Each piece of data is represented by the {@link CredentialRequest.DataElement}
 * type which includes a number of attributes. These abstractions are modeled after MDOC as
 * per ISO/IEC 18013-5:2021 but can be used for other credential shapes organized in a similar
 * fashion.
 *
 * <p>The intended use of this type is that it's generated when receiving the request from the
 * remote reader, using {@link com.android.identity.mdoc.util.MdocUtil#generateCredentialRequest(DeviceRequestParser.DocumentRequest)}
 * or similar. The {@link CredentialRequest} can then be used - along with data from the original
 * request - to render a consent dialog that can be presented to the user. Additional input can
 * be gathered from the user and recorded in the {@link CredentialRequest} instance using the
 * {@link CredentialRequest.DataElement#setDoNotSend(boolean)} method.
 *
 * <p>Once the user consents to sending data to the remote reader, the {@link CredentialRequest}
 * can then be used with {@link com.android.identity.mdoc.util.MdocUtil#mergeIssuerNamesSpaces(CredentialRequest, NameSpacedData, StaticAuthDataParser.StaticAuthData)},
 * {@link com.android.identity.mdoc.response.DocumentGenerator}, and {@link com.android.identity.mdoc.response.DeviceResponseGenerator}
 * to generate the resulting {@code DeviceResponse}. This CBOR can then be sent to the remote
 * reader.
 */
public class CredentialRequest {
    private static final String TAG = "CredentialRequest";

    private final List<DataElement> mRequestedDataElements;

    /**
     * Creates a new request.
     *
     * @param requestedDataElements A list of {@link CredentialRequest.DataElement} instances.
     */
    public CredentialRequest(@NonNull List<DataElement> requestedDataElements) {
        mRequestedDataElements = requestedDataElements;
    }

    /**
     * Gets the request data elements.
     *
     * @return the list of data elements to request.
     */
    public @NonNull List<DataElement> getRequestedDataElements() {
        return mRequestedDataElements;
    }

    /**
     * An abstraction of a piece of data to request.
     */
    public static class DataElement {
        private final String mNameSpaceName;
        private final String mDataElementName;
        private final boolean mIntentToRetain;
        private boolean mDoNotSend;

        /**
         * Constructor.
         *
         * @param nameSpaceName the namespace name for the data element.
         * @param dataElementName the data element name.
         * @param intentToRetain whether the remote reader intends to retain the data.
         */
        public DataElement(@NonNull String nameSpaceName,
                           @NonNull String dataElementName,
                           boolean intentToRetain) {
            mNameSpaceName = nameSpaceName;
            mDataElementName = dataElementName;
            mIntentToRetain = intentToRetain;
            mDoNotSend = false;
        }

        /**
         * Gets the namespace name of the data element.
         *
         * @return the namespace name.
         */
        public @NonNull String getNameSpaceName() {
            return mNameSpaceName;
        }

        /**
         * Gets the data element name.
         *
         * @return the data element name.
         */
        public @NonNull String getDataElementName() {
            return mDataElementName;
        }

        /**
         * Gets whether the remote reader intends to retain the data.
         *
         * @return whether the remote reader intends to retain the data.
         */
        public boolean getIntentToRetain() {
            return mIntentToRetain;
        }

        /**
         * Gets if this data element should not be sent to the remote reader.
         *
         * <p>By default this is set to {@code false} but can be toggled using the
         * {@link #setDoNotSend(boolean)} method.
         *
         * @return whether the data element should not be sent to the remote reader.
         */
        public boolean getDoNotSend() {
            return mDoNotSend;
        }

        /**
         * Sets whether the data element should not be sent to the remote reader.
         *
         * @param doNotSend whether the data element should be sent to the remote reader.
         */
        public void setDoNotSend(boolean doNotSend) {
            mDoNotSend = doNotSend;
        }
    }
}
