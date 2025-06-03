package org.multipaz.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.Base64.PaddingOption
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Extension to encode a [ByteArray] to a URL-safe base64 encoding without padding
 * as defined in Section 5 of RFC 4648.
 */
@OptIn(ExperimentalEncodingApi::class)
fun ByteArray.toBase64Url(): String = Base64.UrlSafe.encode(this).trimEnd('=')

/**
 * Extension to decode a [ByteArray] from a URL-safe base64 encoded string
 * as defined in Section 5 of RFC 4648.
 *
 * This works for both strings with or without padding.
 */
@OptIn(ExperimentalEncodingApi::class)
fun String.fromBase64Url(): ByteArray {
    return Base64.UrlSafe.withPadding(PaddingOption.ABSENT_OPTIONAL).decode(this)
}

/**
 * Extension to encode a [ByteArray] to a base64 encoding (not URL-safe base64) without padding
 * as defined in Section 4 of RFC 4648.
 */
@OptIn(ExperimentalEncodingApi::class)
fun ByteArray.toBase64(): String = Base64.encode(this).trimEnd('=')

/**
 * Extension to decode a [ByteArray] from a base64 encoded (not URL-safe base64) string
 * as defined in Section 4 of RFC 4648.
 *
 * This works for both strings with or without padding.
 */
@OptIn(ExperimentalEncodingApi::class)
fun String.fromBase64(): ByteArray {
    return Base64.withPadding(PaddingOption.ABSENT_OPTIONAL).decode(this)
}
