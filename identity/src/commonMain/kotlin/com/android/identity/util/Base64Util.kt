package com.android.identity.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Extension to encode a [ByteArray] to a base64 encoded string.
 */
@OptIn(ExperimentalEncodingApi::class)
fun ByteArray.toBase64(): String = Base64.UrlSafe.encode(this).trimEnd('=')

/**
 * Extension to decode a [ByteArray] from a base64 encoded string.
 */
@OptIn(ExperimentalEncodingApi::class)
fun String.fromBase64(): ByteArray = Base64.UrlSafe.decode(this)
