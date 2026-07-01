/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.dresos.dressecurecomms.crypto.SmsCrypto
import com.klinker.android.send_message.Message
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction

object MmsSender {

    fun send(
        context: Context,
        recipients: List<String>,
        text: String?,
        imageUri: Uri?,
        encrypt: Boolean,
        key: String,
        threadId: Long
    ) {
        val settings = Settings().apply { useSystemSending = true }
        val transaction = Transaction(context, settings)

        val body = when {
            text.isNullOrBlank() -> ""
            encrypt -> SmsCrypto.encrypt(text, key)
            else -> text
        }
        val message = Message(body, recipients.toTypedArray())

        if (imageUri != null) {
            val raw = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Could not read the selected image")
            if (encrypt) {

                message.addMedia(SmsCrypto.encryptBytes(raw, key), "application/octet-stream")
            } else {
                message.setImage(downscale(raw))
            }
        }

        transaction.sendNewMessage(message, if (threadId > 0) threadId else Transaction.NO_THREAD_ID)
    }

    private fun downscale(raw: ByteArray, maxDim: Int = 1024): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
            ?: throw IllegalStateException("Could not decode the selected image")
    }
}
