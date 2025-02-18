package org.multipaz.compose.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context.NOTIFICATION_SERVICE
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android.identity.util.AndroidContexts
import com.android.identity.util.UUID

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
        val context = AndroidContexts.applicationContext
        val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
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

        val context = AndroidContexts.applicationContext
        val builder = NotificationCompat.Builder(context, notificationChannel.id).apply {
            setSmallIcon(smallIconResourceId!!)
            if (notification.image != null) {
                setLargeIcon(notification.image.asAndroidBitmap())
            }
            setContentTitle(notification.title)
            setContentText(notification.body)
            setAutoCancel(true)
            setPriority(NotificationCompat.PRIORITY_HIGH)
        }

        NotificationManagerCompat.from(context).notify(
            effectiveNotificationId,
            1,
            builder.build()
        )

        return effectiveNotificationId
    }

    suspend fun cancel(notificationId: NotificationId) {
        val context = AndroidContexts.applicationContext
        NotificationManagerCompat.from(context).cancel(notificationId, 1)
    }

    suspend fun cancelAll() {
        val context = AndroidContexts.applicationContext
        NotificationManagerCompat.from(context).cancelAll()
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
