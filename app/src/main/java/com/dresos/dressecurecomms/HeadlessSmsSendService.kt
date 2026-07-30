/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import com.dresos.dressecurecomms.crypto.ContactKeys
import com.dresos.dressecurecomms.crypto.SmsCrypto
import com.dresos.dressecurecomms.data.SmsRepository
import com.dresos.dressecurecomms.data.SmsStore

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
                    val contactKey = ContactKeys.forAddress(this, address)
                    val payload = if (contactKey.isNullOrBlank()) text else SmsCrypto.encrypt(text, contactKey)
                    val now = System.currentTimeMillis()
                    val threadId = SmsRepository.threadIdForAddress(this, address)
                    val row = SmsRepository.insertOutbox(this, address, payload, threadId, now)
                    val parts = sm.divideMessage(payload)
                    sm.sendMultipartTextMessage(
                        address, null, parts,
                        SmsSentReceiver.intents(this, parts.size, row, address), null
                    )
                    SmsStore.add(this, address, text, now)
                } catch (e: Exception) {
                }
            }
        }
        stopSelf()
        return START_NOT_STICKY
    }
}
