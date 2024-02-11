package dev.erdos.automechanon

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class NotificationService : NotificationListenerService() {

    companion object {
        var notificationListenerConnected: Boolean = false
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        notificationListenerConnected = true
    }

    override fun onNotificationPosted(notification: StatusBarNotification) {
        GlobalScope.launch {
            dispatch(applicationContext, notification)
        }
    }

    override fun onListenerDisconnected() {
        notificationListenerConnected = false
    }
}
