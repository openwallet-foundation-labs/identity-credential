package org.multipaz.compose.notifications

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Notification content.
 *
 * @property title The title of the notification.
 * @property body The body text of the notification.
 * @property image an [ImageBitmap] to show in the notification or `null`.
 */
data class Notification(
    val title: String,
    val body: String,
    val image: ImageBitmap? = null
)
