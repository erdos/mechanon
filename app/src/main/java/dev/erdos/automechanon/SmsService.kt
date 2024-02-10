package dev.erdos.automechanon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class SmsService : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                GlobalScope.launch {
                    dispatch(context, smsMessage)
                }
            }
        }
    }
}