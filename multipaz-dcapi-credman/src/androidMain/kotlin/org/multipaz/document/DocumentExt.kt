package org.multipaz.document

import kotlinx.io.bytestring.ByteString

private const val TAG_ANDROID_CREDMAN_TITLE = "org.multipaz.document.androidCredman.Title"
private const val TAG_ANDROID_CREDMAN_SUBTITLE = "org.multipaz.document.androidCredman.Subtitle"
private const val TAG_ANDROID_CREDMAN_BITMAP = "org.multipaz.document.androidCredman.Bitmap"
private const val TAG_ANDROID_CREDMAN_EXCHANGE_PROTOCOLS = "org.multipaz.document.androidCredman.ExchangeProtocols"
private const val TAG_ANDROID_CREDMAN_OMIT_CLAIMS = "org.multipaz.document.androidCredman.OmitClaims"

/**
 * The title for a document in Android Credential Manager or `null` if not configured.
 */
val Document.androidCredmanTitle: String?
    get() = tags.getString(TAG_ANDROID_CREDMAN_TITLE)

/**
 * Sets the title for a document in Android Credential Manager.
 *
 * @param value the value or `null` to unconfigure.
 */
suspend fun Document.setAndroidCredmanTitle(value: String?) {
    edit {
        value?.let {
            tags.setString(TAG_ANDROID_CREDMAN_TITLE, value)
        } ?: tags.remove(TAG_ANDROID_CREDMAN_TITLE)
    }
}

/**
 * The subtitle for a document in Android Credential Manager or `null` if not configured.
 */
val Document.androidCredmanSubtitle: String?
    get() = tags.getString(TAG_ANDROID_CREDMAN_SUBTITLE)

/**
 * Sets the subtitle for a document in Android Credential Manager.
 *
 * @param value the value or `null` to unconfigure.
 */
suspend fun Document.setAndroidCredmanSubtitle(value: String?) {
    edit {
        value?.let {
            tags.setString(TAG_ANDROID_CREDMAN_SUBTITLE, value)
        } ?: tags.remove(TAG_ANDROID_CREDMAN_SUBTITLE)
    }
}

/**
 * The bitmap for a document in Android Credential Manager or `null` if not configured.
 */
val Document.androidCredmanBitmap: ByteString?
    get() = tags.getByteString(TAG_ANDROID_CREDMAN_BITMAP)

/**
 * Sets bitmap for a document in Android Credential Manager.
 *
 * @param value the value or `null` to unconfigure.
 */
suspend fun Document.setAndroidCredmanBitmap(value: ByteString?) {
    edit {
        value?.let {
            tags.setByteString(TAG_ANDROID_CREDMAN_BITMAP, value)
        } ?: tags.remove(TAG_ANDROID_CREDMAN_BITMAP)
    }
}

/**
 * The exchange protocols for a document in Android Credential Manager or `null` if not configured.
 */
val Document.androidCredmanExchangeProtocols: List<String>?
    get() = tags.getStringList(TAG_ANDROID_CREDMAN_EXCHANGE_PROTOCOLS)

/**
 * Sets exchange protocols for a document in Android Credential Manager.
 *
 * @param value the value or `null` to unconfigure.
 */
suspend fun Document.setAndroidCredmanExchangeProtocols(value: List<String>?) {
    edit {
        value?.let {
            tags.setStringList(TAG_ANDROID_CREDMAN_EXCHANGE_PROTOCOLS, value)
        } ?: tags.remove(TAG_ANDROID_CREDMAN_EXCHANGE_PROTOCOLS)
    }
}

/**
 * Whether claims and their values should be omitted when registering the document with
 * Android Credential Manager. Defaults to `false` if not configured.
 */
val Document.androidCredmanOmitClaims: Boolean
    get() = tags.getBoolean(TAG_ANDROID_CREDMAN_OMIT_CLAIMS) ?: false

/**
 * Sets whether claims and their values should be omitted when registering the document with
 * Android Credential Manager.
 *
 * @param value whether to omit claims and their values.
 */
suspend fun Document.setAndroidCredmanOmitClaims(value: Boolean) {
    edit {
        if (value) {
            tags.setBoolean(TAG_ANDROID_CREDMAN_OMIT_CLAIMS, true)
        } else {
            tags.remove(TAG_ANDROID_CREDMAN_OMIT_CLAIMS)
        }
    }
}


