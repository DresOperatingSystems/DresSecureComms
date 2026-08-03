/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.util

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.dresos.dressecurecomms.data.SmsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Diagnostics {
    private const val FILE = "inbound_log.txt"
    private const val MAX_LINES = 200

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private fun append(ctx: Context, line: String) {
        try {
            val f = java.io.File(ctx.filesDir, FILE)
            val lines = if (f.exists()) f.readLines().toMutableList() else ArrayList()
            lines.add("${stamp()}  $line")
            while (lines.size > MAX_LINES) lines.removeAt(0)
            f.writeText(lines.joinToString("\n"))
        } catch (e: Exception) {
        }
    }

    fun recordInbound(ctx: Context, sender: String, threadId: Long, row: Uri?) {
        if (row == null) {
            append(ctx, "inbound from $sender thread=$threadId INSERT RETURNED NULL")
            return
        }
        var storedThread = -1L
        var storedAddress = ""
        try {
            ctx.contentResolver.query(
                row, arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    storedThread = c.getLong(0)
                    storedAddress = c.getString(1) ?: ""
                }
            }
        } catch (e: Exception) {
        }
        val agrees = if (storedThread == threadId) "ok" else "MISMATCH"
        append(ctx, "inbound from $sender asked=$threadId stored=$storedThread addr=$storedAddress $agrees")
    }

    fun recordFailure(ctx: Context, sender: String, e: Exception) {
        append(ctx, "inbound from $sender FAILED ${e.javaClass.simpleName} ${e.message}")
    }

    fun report(ctx: Context, address: String): String = buildString {
        append("DresSecureComms diagnostic\n")
        append("time: ${stamp()}\n")
        append("default sms app: ${SmsRepository.isDefault(ctx)}\n")
        append("package holding role: ${Telephony.Sms.getDefaultSmsPackage(ctx)}\n\n")

        append("address asked about: $address\n")
        val resolved = SmsRepository.threadIdForAddress(ctx, address)
        append("getOrCreateThreadId says: $resolved\n\n")

        append("rows in the sms table for that address\n")
        var rows = 0
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
                    Telephony.Sms.TYPE, Telephony.Sms.DATE, Telephony.Sms.SUBSCRIPTION_ID),
                null, null, "${Telephony.Sms.DATE} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val a = c.getString(2) ?: ""
                    if (!PhoneKey.same(a, address)) continue
                    rows++
                    if (rows > 40) continue
                    append("  id=${c.getLong(0)} thread=${c.getLong(1)} addr=[$a]")
                    append(" type=${c.getInt(3)} sub=${c.getInt(5)} date=${c.getLong(4)}\n")
                }
            }
        } catch (e: Exception) {
            append("  query failed: ${e.message}\n")
        }
        append("  total matching rows: $rows\n\n")

        append("canonical addresses that look like this number\n")
        try {
            ctx.contentResolver.query(
                Uri.parse("content://mms-sms/canonical-addresses"),
                arrayOf("_id", "address"), null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val a = c.getString(1) ?: ""
                    if (PhoneKey.same(a, address)) append("  _id=${c.getString(0)} address=[$a]\n")
                }
            }
        } catch (e: Exception) {
            append("  query failed: ${e.message}\n")
        }

        append("\nconversations the app can see\n")
        val convs = SmsRepository.conversations(ctx, "")
        for (c in convs.take(40)) {
            val mark = if (PhoneKey.same(c.address, address)) "  <== this one" else ""
            append("  thread=${c.threadId} addr=[${c.address}]$mark\n")
        }
        append("  total conversations: ${convs.size}\n")

        append("\nrecent inbound log\n")
        try {
            val f = java.io.File(ctx.filesDir, FILE)
            if (f.exists()) f.readLines().takeLast(30).forEach { append("  $it\n") }
            else append("  empty\n")
        } catch (e: Exception) {
            append("  unreadable\n")
        }
    }
}
