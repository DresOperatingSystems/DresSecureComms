/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.dresos.dressecurecomms.crypto.CryptoManager

object SecureKeys {
    const val SMS_KEY = "sms_shared_key"
    const val VT_KEY = "virustotal_api_key"

    fun smsKey(ctx: Context): String = read(ctx, SMS_KEY)
    fun vtKey(ctx: Context): String = read(ctx, VT_KEY)

    private fun read(ctx: Context, name: String): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val stored = prefs.getString(name, "").orEmpty()
        if (stored.isBlank()) return ""
        return try {
            CryptoManager.decrypt(stored)
        } catch (e: Exception) {
            try { prefs.edit().putString(name, CryptoManager.encrypt(stored)).apply() } catch (_: Exception) {}
            stored
        }
    }
}
