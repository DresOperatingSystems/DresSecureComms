/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dresos.dressecurecomms.R
import com.dresos.dressecurecomms.ThreadActivity

object Notify {
    private const val CHANNEL = "messages"
    const val ACTION_COPY = "com.dresos.dressecurecomms.COPY"
    const val EXTRA_TEXT = "text"
    const val EXTRA_ID = "id"

    private fun manager(context: Context): NotificationManager {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        return nm
    }

    private fun openThread(context: Context, address: String, id: Int): PendingIntent {
        val open = Intent(context, ThreadActivity::class.java)
            .putExtra("address", address)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, id, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun message(context: Context, address: String, title: String, text: String) {
        val nm = manager(context)
        val id = ("sms:$address").hashCode()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val copy = Intent(context, NotificationActionReceiver::class.java)
            .setAction(ACTION_COPY)
            .putExtra(EXTRA_TEXT, text)
            .putExtra(EXTRA_ID, id)
        val copyPi = PendingIntent.getBroadcast(context, id, copy, flags)

        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_sms)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openThread(context, address, id))
            .setAutoCancel(true)
            .addAction(R.drawable.ic_sms, "Copy", copyPi)
            .build()
        nm.notify(id, n)
    }

    fun sendFailed(context: Context, address: String, title: String) {
        val nm = manager(context)
        val id = ("fail:$address").hashCode()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_sms)
            .setContentTitle(context.getString(R.string.send_failed_title))
            .setContentText(context.getString(R.string.send_failed_text, title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openThread(context, address, id))
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }
}
