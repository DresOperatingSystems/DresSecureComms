/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class DialerInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.inCallService = this
        CallManager.call = call
        showIncoming()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (CallManager.call === call) CallManager.call = null
        NotificationManagerCompat.from(this).cancel(CALL_NOTIF)
    }

    override fun onDestroy() {
        super.onDestroy()
        CallManager.inCallService = null
        NotificationManagerCompat.from(this).cancel(CALL_NOTIF)
    }

    private fun showIncoming() {
        val full = Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            this, 0, full,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CALL_CHANNEL, "Calls", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val ringing = CallManager.call?.state == Call.STATE_RINGING
        val n = NotificationCompat.Builder(this, CALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(getString(if (ringing) R.string.call_incoming else R.string.call_dialing))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()
        nm.notify(CALL_NOTIF, n)
        try { startActivity(full) } catch (_: Exception) {}
    }

    companion object {
        private const val CALL_CHANNEL = "calls"
        private const val CALL_NOTIF = 1001
    }
}
