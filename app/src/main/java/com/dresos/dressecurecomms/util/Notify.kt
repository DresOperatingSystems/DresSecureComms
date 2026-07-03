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

    fun message(context: Context, address: String, title: String, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val id = ("sms:$address").hashCode()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val open = Intent(context, ThreadActivity::class.java)
            .putExtra("address", address)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPi = PendingIntent.getActivity(context, id, open, flags)

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
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_sms, "Copy", copyPi)
            .build()
        nm.notify(id, n)
    }
}
