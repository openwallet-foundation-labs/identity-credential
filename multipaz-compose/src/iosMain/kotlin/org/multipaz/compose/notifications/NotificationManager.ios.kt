package org.multipaz.compose.notifications

import org.multipaz.util.UUID
import org.multipaz.util.toKotlinError
import org.multipaz.util.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import org.multipaz.compose.encodeImageToPng
import platform.darwin.NSObject
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNNotificationAttachment
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.writeToURL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

private object NotificationManagerIos  {

    private val NOTIFICATION_PERMISSIONS =
        UNAuthorizationOptionAlert or
                UNAuthorizationOptionSound or
                UNAuthorizationOptionBadge

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    private val delegate = object : NSObject(), UNUserNotificationCenterDelegateProtocol {
        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            didReceiveNotificationResponse: UNNotificationResponse,
            withCompletionHandler: () -> Unit
        ) {
            withCompletionHandler()
        }

        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            willPresentNotification: UNNotification,
            withCompletionHandler: (UNNotificationPresentationOptions) -> Unit
        ) {
            withCompletionHandler(NOTIFICATION_PERMISSIONS)
        }
    }

    init {
        center.delegate = delegate
    }

    @OptIn(ExperimentalForeignApi::class)
    suspend fun notify(
        notification: Notification,
        notificationId: NotificationId?,
    ): NotificationId {
        val effectiveNotificationId = notificationId ?: "notification_${UUID.randomUUID()}"

        val notificationContent = UNMutableNotificationContent().apply {
            setTitle(notification.title)
            setBody(notification.body)
            setSound(UNNotificationSound.defaultSound)
            setUserInfo(userInfo)
            if (notification.image != null) {
                val tmpFileUrl = NSURL.fileURLWithPath(
                    NSTemporaryDirectory() + "/notificationImage_${Random.nextInt()}.png"
                )
                with(Dispatchers.IO) {
                    val pngImage = encodeImageToPng(notification.image)
                    pngImage.toByteArray().toNSData().writeToURL(url = tmpFileUrl, atomically = true)
                }
                setAttachments(
                    listOf(
                        UNNotificationAttachment.attachmentWithIdentifier(
                            identifier = "",
                            URL = tmpFileUrl,
                            options = null,
                            error = null
                        )
                    )
                )
            }
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(0.1, false)
        val notificationRequest = UNNotificationRequest.requestWithIdentifier(
            identifier = effectiveNotificationId,
            content = notificationContent,
            trigger = trigger
        )
        suspendCancellableCoroutine<Boolean> { continuation ->
            center.addNotificationRequest(notificationRequest) { error ->
                if (error != null) {
                    continuation.resumeWithException(
                        IllegalStateException("Error showing notification", error.toKotlinError())
                    )
                } else {
                    continuation.resume(true)
                }
            }
        }

        return effectiveNotificationId
    }

    suspend fun cancel(notificationId: NotificationId) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(notificationId))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(notificationId))
    }

    suspend fun cancelAll() {
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
    }
}

internal actual suspend fun defaultNotify(
    notification: Notification,
    notificationId: NotificationId?,
) = NotificationManagerIos.notify(notification, notificationId)

internal actual suspend fun defaultCancel(
    notificationId: NotificationId
) = NotificationManagerIos.cancel(notificationId)

internal actual suspend fun defaultCancelAll() = NotificationManagerIos.cancelAll()
