/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLog {
    private const val FILE = "crash_log.txt"

    fun append(context: Context, trace: String) {
        try {
            val f = File(context.filesDir, FILE)
            if (f.length() > 200_000) f.delete()
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            f.appendText("=== $stamp ===\n$trace\n\n")
        } catch (e: Throwable) {
        }
    }

    fun read(context: Context): String = try {
        File(context.filesDir, FILE).readText()
    } catch (e: Throwable) {
        ""
    }

    fun share(activity: Activity) {
        val log = read(activity)
        val body = if (log.isBlank()) "No crash log recorded yet." else log
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("security@dresos.org"))
            putExtra(Intent.EXTRA_SUBJECT, "DresSecureComms crash log")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            activity.startActivity(Intent.createChooser(intent, "Share crash log"))
        } catch (e: Throwable) {
        }
    }
}
