/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.data

import android.content.Context
import android.provider.CallLog

/** Reads and deletes the system call log. Reading needs READ_CALL_LOG; deleting WRITE_CALL_LOG. */
object CallHistory {
    data class Entry(val id: Long, val number: String, val type: Int, val date: Long, val duration: Long)

    fun load(ctx: Context): List<Entry> {
        val out = ArrayList<Entry>()
        val cols = arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.TYPE,
            CallLog.Calls.DATE, CallLog.Calls.DURATION
        )
        val cursor = try {
            ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, cols, null, null, "${CallLog.Calls.DATE} DESC")
        } catch (e: SecurityException) {
            null
        } ?: return out
        cursor.use { c ->
            val idi = c.getColumnIndexOrThrow(CallLog.Calls._ID)
            val ni = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val ti = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val di = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val dui = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (c.moveToNext()) {
                out.add(Entry(c.getLong(idi), c.getString(ni) ?: "Unknown",
                    c.getInt(ti), c.getLong(di), c.getLong(dui)))
            }
        }
        return out
    }

    fun delete(ctx: Context, id: Long) {
        try {
            ctx.contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID}=?", arrayOf(id.toString()))
        } catch (_: Exception) {
        }
    }

    fun clear(ctx: Context) {
        try {
            ctx.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
        } catch (_: Exception) {
        }
    }

    fun typeLabel(type: Int): String = when (type) {
        CallLog.Calls.INCOMING_TYPE -> "Incoming"
        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
        CallLog.Calls.MISSED_TYPE -> "Missed"
        CallLog.Calls.REJECTED_TYPE -> "Rejected"
        CallLog.Calls.BLOCKED_TYPE -> "Blocked"
        CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
        else -> "Call"
    }
}
