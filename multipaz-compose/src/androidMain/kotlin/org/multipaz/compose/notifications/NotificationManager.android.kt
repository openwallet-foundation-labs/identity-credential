package org.multipaz.compose.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context.NOTIFICATION_SERVICE
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.multipaz.context.applicationContext
import org.multipaz.util.UUID

object NotificationManagerAndroid {

    fun setSmallIcon(iconResourceId: Int) {
        smallIconResourceId = iconResourceId
    }

    fun setChannelTitle(channelTitle: String) {
        notificationChannelTitle = channelTitle
    }

    private var smallIconResourceId: Int? = null
    private var notificationChannelTitle: String? = null

    val notificationChannel: NotificationChannel by lazy {
        require(notificationChannelTitle != null) {
            "Notification channel title not set - did you remember to call " +
                    "NotificationManagerAndroid.setChannelTitle()?"
        }
        val channel = NotificationChannel(
            "org.multipaz.defaultNotificationChannel",
            notificationChannelTitle,
            NotificationManager.IMPORTANCE_HIGH
        )
        val notificationManager = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        channel
    }

    @SuppressLint("MissingPermission")
    internal suspend fun notify(
        notification: Notification,
        notificationId: NotificationId?,
    ): NotificationId {
        require(smallIconResourceId != null) {
            "Small icon not set - did you remember to call NotificationManagerAndroid.setSmallIcon()?"
        }

        val effectiveNotificationId = notificationId ?: "notification_${UUID.randomUUID()}"

        val builder = NotificationCompat.Builder(applicationContext, notificationChannel.id).apply {
            setSmallIcon(smallIconResourceId!!)
            if (notification.image != null) {
                setLargeIcon(notification.image.asAndroidBitmap())
            }
            setContentTitle(notification.title)
            setContentText(notification.body)
            setAutoCancel(true)
            setPriority(NotificationCompat.PRIORITY_HIGH)
        }

        NotificationManagerCompat.from(applicationContext).notify(
            effectiveNotificationId,
            1,
            builder.build()
        )

        return effectiveNotificationId
    }

    suspend fun cancel(notificationId: NotificationId) {
        NotificationManagerCompat.from(applicationContext).cancel(notificationId, 1)
    }

    suspend fun cancelAll() {
        NotificationManagerCompat.from(applicationContext).cancelAll()
    }
}

internal actual suspend fun defaultNotify(
    notification: Notification,
    notificationId: NotificationId?,
) = NotificationManagerAndroid.notify(notification, notificationId)

internal actual suspend fun defaultCancel(
    notificationId: NotificationId
) = NotificationManagerAndroid.cancel(notificationId)

internal actual suspend fun defaultCancelAll() = NotificationManagerAndroid.cancelAll()
