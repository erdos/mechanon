package dev.erdos.automechanon

import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.lifecycle.LiveData
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class NotificationService : NotificationListenerService() {

    lateinit var state: LiveData<Automations>

    override fun onCreate() {
        super.onCreate()

        state = getState(this)
        Log.i("XXX", "Notification listener created");
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.i("XXX", "Notification listener bound");
        return super.onBind(intent)
    }

    override fun onListenerConnected() {
        Log.i("XXX", "Notification listener connected");
        super.onListenerConnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        GlobalScope.launch {
            dispatch(applicationContext, sbn)
        }
    }
}
