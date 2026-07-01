/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.preference.PreferenceManager
import com.dresos.dressecurecomms.crypto.SmsCrypto
import com.dresos.dressecurecomms.util.Contacts
import com.dresos.dressecurecomms.util.Notify

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (Telephony.Sms.getDefaultSmsPackage(context) == context.packageName) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return
        val sender = messages[0].originatingAddress ?: "Unknown"
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val key = PreferenceManager.getDefaultSharedPreferences(context)
            .getString("sms_shared_key", "").orEmpty()
        val text = when {
            SmsCrypto.isEncrypted(body) && key.isNotBlank() ->
                try { SmsCrypto.decrypt(body, key) } catch (e: Exception) { "[encrypted, wrong or missing key]" }
            SmsCrypto.isEncrypted(body) -> "[encrypted, set the shared key in Settings]"
            else -> body
        }
        Notify.message(context, sender, Contacts.nameFor(context, sender), text)
    }
}
