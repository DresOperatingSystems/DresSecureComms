/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.dresos.dressecurecomms.data.SmsRepository
import com.dresos.dressecurecomms.util.Contacts
import com.dresos.dressecurecomms.util.Notify

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val ok = resultCode == Activity.RESULT_OK
        val row = intent.getStringExtra(EXTRA_ROW)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        SmsRepository.markSendResult(context, row, ok)
        if (!ok) {
            val address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
            if (address.isNotBlank()) {
                Notify.sendFailed(context, address, Contacts.nameFor(context, address))
            }
        }
    }

    companion object {
        const val ACTION = "com.dresos.dressecurecomms.SMS_SENT"
        private const val EXTRA_ROW = "row"
        private const val EXTRA_ADDRESS = "address"

        fun intents(context: Context, parts: Int, row: Uri?, address: String): ArrayList<PendingIntent> {
            val out = ArrayList<PendingIntent>(parts)
            val base = (row?.toString() ?: address).hashCode()
            for (i in 0 until parts) {
                val intent = Intent(context, SmsSentReceiver::class.java)
                    .setAction(ACTION)
                    .putExtra(EXTRA_ROW, row?.toString())
                    .putExtra(EXTRA_ADDRESS, address)
                out.add(PendingIntent.getBroadcast(context, base + i, intent, flags()))
            }
            return out
        }

        private fun flags(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
    }
}
