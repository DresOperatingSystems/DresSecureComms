/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import com.dresos.dressecurecomms.data.SmsRepository
import com.dresos.dressecurecomms.util.Contacts
import com.dresos.dressecurecomms.util.Notify
import com.dresos.dressecurecomms.util.SecureKeys
import com.klinker.android.send_message.MmsReceivedReceiver

class MmsReceiver : MmsReceivedReceiver() {
    override fun onMessageReceived(context: Context, messageUri: Uri) {
        val key = SecureKeys.smsKey(context)
        val byUri = try {
            SmsRepository.incomingMms(context, ContentUris.parseId(messageUri), key)
        } catch (e: Exception) {
            null
        }
        val mms = byUri?.takeIf { it.address.isNotBlank() }
            ?: SmsRepository.latestIncomingMms(context, key)
            ?: return
        if (mms.address.isBlank()) return
        val text = mms.body.ifBlank { "[photo]" }
        Notify.message(context, mms.address, Contacts.nameFor(context, mms.address), text)
    }

    override fun onError(context: Context, error: String) { }
}
