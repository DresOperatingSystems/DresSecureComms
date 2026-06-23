/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.TelephonyManager

/** Sends quick-reply messages (e.g. "reject call with text") for the default SMS app. */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val address = intent.data?.schemeSpecificPart
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!address.isNullOrBlank() && !text.isNullOrBlank()) {
                try {
                    @Suppress("DEPRECATION")
                    val sm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        getSystemService(SmsManager::class.java) else SmsManager.getDefault()
                    sm.sendTextMessage(address, null, text, null, null)
                } catch (_: Exception) {
                }
            }
        }
        stopSelf()
        return START_NOT_STICKY
    }
}
