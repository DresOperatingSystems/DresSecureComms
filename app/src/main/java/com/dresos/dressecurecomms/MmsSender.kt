/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.klinker.android.send_message.Message
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import java.io.ByteArrayOutputStream

object MmsSender {

    fun send(
        context: Context,
        recipients: List<String>,
        text: String?,
        imageUri: Uri?,
        threadId: Long
    ) {
        val settings = Settings().apply { useSystemSending = true }
        val transaction = Transaction(context, settings)

        val message = Message(text ?: "", recipients.toTypedArray())

        if (imageUri != null) {
            val raw = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Could not read the selected image")
            message.addMedia(compress(raw), "image/jpeg")
        }

        transaction.sendNewMessage(message, if (threadId > 0) threadId else Transaction.NO_THREAD_ID)
    }

    private fun compress(raw: ByteArray, maxDim: Int = 1024, maxBytes: Int = 600_000): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
            ?: throw IllegalStateException("Could not decode the selected image")
        var quality = 90
        var out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        while (out.size() > maxBytes && quality > 40) {
            quality -= 10
            out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        return out.toByteArray()
    }
}
