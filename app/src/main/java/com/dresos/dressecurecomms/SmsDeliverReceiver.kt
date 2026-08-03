/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.dresos.dressecurecomms.crypto.ContactKeys
import com.dresos.dressecurecomms.crypto.SmsCrypto
import com.dresos.dressecurecomms.data.SmsRepository
import com.dresos.dressecurecomms.util.Contacts
import com.dresos.dressecurecomms.util.Diagnostics
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
        val sentAt = messages[0].timestampMillis
        val keys = ContactKeys.candidates(context, sender)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                store(context, sender, body, sentAt)
                val text = when {
                    SmsCrypto.isEncrypted(body) && keys.isNotEmpty() ->
                        SmsCrypto.tryDecrypt(body, keys) ?: "[encrypted, wrong or missing key]"
                    SmsCrypto.isEncrypted(body) -> "[encrypted, set a key in Settings or for this contact]"
                    else -> body
                }
                Notify.message(context, sender, Contacts.nameFor(context, sender), text)
            } finally {
                pending.finish()
            }
        }
    }

    private fun store(context: Context, sender: String, body: String, sentAt: Long) {
        try {
            if (!SmsRepository.isDefault(context)) return
            if (sentAt > 0 && alreadyStored(context, sender, body, sentAt)) return
            val now = System.currentTimeMillis()
            val threadId = SmsRepository.threadIdForAddress(context, sender)
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, sender)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, now)
                put(Telephony.Sms.DATE_SENT, if (sentAt > 0) sentAt else now)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                if (threadId > 0) put(Telephony.Sms.THREAD_ID, threadId)
            }
            val row = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            Diagnostics.recordInbound(context, sender, threadId, row)
        } catch (e: Exception) {
            Diagnostics.recordFailure(context, sender, e)
        }
    }

    private fun alreadyStored(context: Context, sender: String, body: String, sentAt: Long): Boolean = try {
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.ADDRESS}=? AND ${Telephony.Sms.BODY}=? AND ${Telephony.Sms.DATE_SENT}=?",
            arrayOf(sender, body, sentAt.toString()), null
        )?.use { it.moveToFirst() } ?: false
    } catch (e: Exception) {
        false
    }
}
