package org.multipaz_credential.wallet.util

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.ByteArrayOutputStream
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * Convert the given context to a ComponentActivity
 */
fun Context.getActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}


/**
 * Convert a Drawable fetched from resources to bytes.
 */
fun Drawable?.toByteArray(): ByteArray? = (this as BitmapDrawable).bitmap.let { bitmap ->
    ByteArrayOutputStream().run {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
        toByteArray()
    }
}

/**
 * Convert a ByteArray to ImageBitmap.
 */
fun ByteArray.toImageBitmap(): ImageBitmap =
    BitmapFactory.decodeByteArray(this, 0, size).asImageBitmap()

/**
 * Formats an Instant as a date/time in the current timezone.
 */
val Instant.asFormattedDateTimeInCurrentTimezone: String
    get() {
        val dt = this.toLocalDateTime(TimeZone.currentSystemDefault())
        // TODO: use DateTimeFormat in kotlinx-datetime 0.6.0 when released
        return String.format(
            "%04d-%02d-%02d %02d:%02d:%02d",
            dt.year, dt.monthNumber, dt.dayOfMonth, dt.time.hour, dt.time.minute, dt.time.second
        )
    }

/**
 * Get the inverse Color of an androidx.compose.ui.graphics.Color.
 */
fun Color.inverse() = Color(1f - red, 1f - green, 1f - blue, alpha)

/**
 * Return the Url Query of a given a url having a custom scheme (non-standard http/https).
 * Called when handling Urls from a deep link or Qr code code which requires decoding the [url]
 * and the custom [urlScheme], such as openid-credential-offer://, throws an exception by [URLDecoder.decode]:
 * " java.net.MalformedURLException: unknown protocol: openid-credential-offer"
 *
 * This function [getUrlQueryFromCustomSchemeUrl] avoids the exception from being thrown by attempting
 * to convert the provided [url] String to a [URI] and return the [query] property.
 *
 * If the [url] does not contain an authority, an Exception will be thrown when trying to convert the
 * Url to a [URI], so placeholder authority is provided for the sake of creating a valid Uri and
 * obtaining the query value successfully.
 */
fun getUrlQueryFromCustomSchemeUrl(url: String): String {
    val urlParts = url.split("://")
    // ensure url scheme is defined
    if (urlParts.size == 1) {
        throw MalformedURLException("Invalid url '$url'")
    }
    // extract the scheme
    val scheme = urlParts[0]
    val decodedUri = try {
        URI(url)
    } catch (e: URISyntaxException) {
        //java.net.URISyntaxException: Expected authority at index 26: openid-credential-offer://
        // Exception is thrown when authority is missing from the Uri, ie: www.google.com?
        // add a placeholder authority for the purposes of creating a valid Uri and extracting the query.
        val restOfEncodedUrl = urlParts[1]
        val placeholderAuthority = "www.placeholder.com/"
        val placeholderAuthorityUri = "$scheme://$placeholderAuthority${restOfEncodedUrl}"
        URI(placeholderAuthorityUri)
    }
    return decodedUri.query
}