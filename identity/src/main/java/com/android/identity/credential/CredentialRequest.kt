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
package com.android.identity.credential

/**
 * Credential request.
 *
 * This type is an abstraction of a request from a remote reader requesting one or more
 * pieces of data. Each piece of data is represented by the [CredentialRequest.DataElement]
 * type which includes a number of attributes. These abstractions are modeled after MDOC as
 * per ISO/IEC 18013-5:2021 but can be used for other credential formats organized in a similar
 * fashion.
 *
 * The intended use of this type is that it's generated when receiving the request from the
 * remote reader, using [com.android.identity.mdoc.util.MdocUtil.generateCredentialRequest]
 * or similar. The [CredentialRequest] can then be used - along with data from the original
 * request - to render a consent dialog that can be presented to the user. Additional input can
 * be gathered from the user and recorded in the [CredentialRequest] instance using the
 * [CredentialRequest.DataElement.setDoNotSend] method.
 *
 * Once the user consents to sending data to the remote reader, the [CredentialRequest]
 * can then be used with [com.android.identity.mdoc.util.MdocUtil.mergeIssuerNamesSpaces],
 * [com.android.identity.mdoc.response.DocumentGenerator], and [com.android.identity.mdoc.response.DeviceResponseGenerator]
 * to generate the resulting `DeviceResponse`. This CBOR can then be sent to the remote
 * reader.
 *
 * @param requestedDataElements A list of [CredentialRequest.DataElement] instances.
 */
class CredentialRequest(
    val requestedDataElements: List<DataElement>
) {
    /**
     * An abstraction of a piece of data to request.
     *
     * @param nameSpaceName the namespace name for the data element.
     * @param dataElementName the data element name.
     * @param intentToRetain whether the remote reader intends to retain the data.
     * @param doNotSend Whether the data element should not be sent to the remote reader.
     */
    data class DataElement(
        val nameSpaceName: String,
        val dataElementName: String,
        val intentToRetain: Boolean,
        var doNotSend: Boolean = false
    )
}