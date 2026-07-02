/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.ui

import android.content.Context
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import com.dresos.dressecurecomms.crypto.CryptoManager

class EncryptedEditTextPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : EditTextPreference(context, attrs) {

    override fun persistString(value: String?): Boolean =
        super.persistString(if (value.isNullOrEmpty()) value else CryptoManager.encrypt(value))

    override fun getPersistedString(defaultReturnValue: String?): String {
        val stored = super.getPersistedString(null)
        if (stored.isNullOrBlank()) return defaultReturnValue ?: ""
        return try {
            CryptoManager.decrypt(stored)
        } catch (e: Exception) {
            stored
        }
    }
}
