/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import com.dresos.dressecurecomms.util.SecureKeys

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.dresos.dressecurecomms.crypto.ContactKeys
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
        val keys = ContactKeys.candidates(context, sender)
        val text = when {
            SmsCrypto.isEncrypted(body) && keys.isNotEmpty() ->
                SmsCrypto.tryDecrypt(body, keys) ?: "[encrypted, wrong or missing key]"
            SmsCrypto.isEncrypted(body) -> "[encrypted, set a key in Settings or for this contact]"
            else -> body
        }
        Notify.message(context, sender, Contacts.nameFor(context, sender), text)
    }
}
