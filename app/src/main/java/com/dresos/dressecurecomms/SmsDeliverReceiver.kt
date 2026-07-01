/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.preference.PreferenceManager
import com.dresos.dressecurecomms.crypto.SmsCrypto
import com.dresos.dressecurecomms.util.Notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return
        val sender = messages[0].originatingAddress ?: "Unknown"
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val key = PreferenceManager.getDefaultSharedPreferences(context)
            .getString("sms_shared_key", "").orEmpty()

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {

                try {
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, sender)
                        put(Telephony.Sms.BODY, body)
                        put(Telephony.Sms.DATE, System.currentTimeMillis())
                        put(Telephony.Sms.READ, 0)
                        put(Telephony.Sms.SEEN, 0)
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    }
                    context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
                } catch (_: Exception) {
                }
                val text = when {
                    SmsCrypto.isEncrypted(body) && key.isNotBlank() ->
                        try { SmsCrypto.decrypt(body, key) } catch (e: Exception) { "[encrypted, wrong or missing key]" }
                    SmsCrypto.isEncrypted(body) -> "[encrypted, set the shared key in Settings]"
                    else -> body
                }
                Notify.message(context, sender, text)
            } finally {
                pending.finish()
            }
        }
    }
}
