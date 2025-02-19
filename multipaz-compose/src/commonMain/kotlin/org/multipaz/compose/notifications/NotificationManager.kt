package org.multipaz.compose.notifications

import org.multipaz.compose.permissions.rememberNotificationPermissionState

/**
 * An interface for posting notifications to the user.
 *
 * Posting notifications may require explicit permission granted by the user, use
 * [rememberNotificationPermissionState] to check for or request this permission.
 */
interface NotificationManager {

    /**
     * Posts or updates a notification to be shown in the status bar.
     *
     * @param notification the notification to show.
     * @param notificationId an identifier for the notification to show or update or `null` to
     *   automatically generate one.
     * @return an identifier for the notification which can be used to update or cancel it later.
     */
    suspend fun notify(
        notification: Notification,
        notificationId: NotificationId? = null,
    ): NotificationId

    /**
     * Cancels a pending or delivered notification.
     *
     * @param notificationId the identifier for the notification to cancel.
     */
    suspend fun cancel(
        notificationId: NotificationId
    )

    /**
     * Cancels all pending and delivered notifications.
     */
    suspend fun cancelAll()

    /**
     * The default implementation of [NotificationManager] on the platform.
     */
    object Default: NotificationManager {
        override suspend fun notify(notification: Notification, notificationId: NotificationId?): NotificationId =
            defaultNotify(notification, notificationId)
        override suspend fun cancel(notificationId: NotificationId) = defaultCancel(notificationId)
        override suspend fun cancelAll() = defaultCancelAll()
    }
}

/**
 * An identifier for a pending or delivered notification.
 */
typealias NotificationId = String

internal expect suspend fun defaultNotify(
    notification: Notification,
    notificationId: NotificationId?,
): NotificationId

internal expect suspend fun defaultCancel(
    notificationId: NotificationId
)

internal expect suspend fun defaultCancelAll()
