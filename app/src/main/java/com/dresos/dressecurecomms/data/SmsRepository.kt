/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.data

import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import com.dresos.dressecurecomms.crypto.ContactKeys
import com.dresos.dressecurecomms.crypto.SmsCrypto

object SmsRepository {
    data class Conversation(val threadId: Long, val address: String, val snippet: String, val time: Long)
    data class Msg(val body: String, val time: Long, val outgoing: Boolean, val imageUri: String? = null)
    data class IncomingMms(val address: String, val body: String)
    data class DeleteResult(val isDefault: Boolean, val removed: Int)

    private const val MAX_THREAD = 2000
    private const val DUP_WINDOW = 1000L
    private val CONVERSATIONS: Uri = Uri.parse("content://mms-sms/conversations?simple=true")
    private val CANONICAL: Uri = Uri.parse("content://mms-sms/canonical-addresses")

    @Volatile private var archivedColumnAvailable = true

    fun isDefault(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS) && rm.isRoleHeld(RoleManager.ROLE_SMS)) {
                return true
            }
        }
        return Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName
    }

    fun conversations(ctx: Context, key: String): List<Conversation> {
        val out = ArrayList<Conversation>()
        val names = canonicalAddresses(ctx)
        for (attempt in 0..1) {
            val cols = if (archivedColumnAvailable) {
                arrayOf("_id", "snippet", "date", "recipient_ids", "archived")
            } else {
                arrayOf("_id", "snippet", "date", "recipient_ids")
            }
            try {
                ctx.contentResolver.query(CONVERSATIONS, cols, "message_count > 0", null, "date DESC")?.use { c ->
                    val idi = c.getColumnIndexOrThrow("_id")
                    val si = c.getColumnIndexOrThrow("snippet")
                    val di = c.getColumnIndexOrThrow("date")
                    val ri = c.getColumnIndexOrThrow("recipient_ids")
                    while (c.moveToNext()) {
                        val threadId = c.getLong(idi)
                        val address = (c.getString(ri) ?: "")
                            .split(' ')
                            .mapNotNull { names[it.trim()] }
                            .filter { it.isNotBlank() }
                            .joinToString(", ")
                        if (address.isBlank()) continue
                        val snippet = decode(ctx, c.getString(si) ?: "", key)
                        out.add(Conversation(threadId, address, snippet, c.getLong(di)))
                    }
                }
                return out
            } catch (e: Exception) {
                if (archivedColumnAvailable) {
                    archivedColumnAvailable = false
                    out.clear()
                } else {
                    return out
                }
            }
        }
        return out
    }

    private fun canonicalAddresses(ctx: Context): Map<String, String> {
        val map = HashMap<String, String>()
        try {
            ctx.contentResolver.query(CANONICAL, arrayOf("_id", "address"), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    map[c.getString(0) ?: continue] = c.getString(1) ?: ""
                }
            }
        } catch (e: Exception) {
        }
        return map
    }

    fun threadById(ctx: Context, threadId: Long, address: String, key: String): List<Msg> {
        val id = if (threadId > 0) threadId else threadIdForAddress(ctx, address)
        val out = ArrayList<Msg>()
        if (id > 0) {
            val args = arrayOf(id.toString())
            try {
                ctx.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                    "${Telephony.Sms.THREAD_ID} = ?", args,
                    "${Telephony.Sms.DATE} ASC LIMIT $MAX_THREAD"
                )?.use { c ->
                    while (c.moveToNext()) {
                        out.add(
                            Msg(
                                decode(ctx, c.getString(0) ?: "", key),
                                c.getLong(1),
                                c.getInt(2) != Telephony.Sms.MESSAGE_TYPE_INBOX
                            )
                        )
                    }
                }
            } catch (e: Exception) {
            }
            try {
                ctx.contentResolver.query(
                    Telephony.Mms.CONTENT_URI,
                    arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX),
                    "${Telephony.Mms.THREAD_ID} = ?", args,
                    "${Telephony.Mms.DATE} ASC LIMIT $MAX_THREAD"
                )?.use { c ->
                    while (c.moveToNext()) {
                        val mid = c.getLong(0)
                        out.add(
                            Msg(
                                decode(ctx, mmsText(ctx, mid), key),
                                c.getLong(1) * 1000,
                                c.getInt(2) != Telephony.Mms.MESSAGE_BOX_INBOX,
                                mmsImageUri(ctx, mid)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
            }
        }
        if (out.isEmpty() && address.isNotEmpty()) {
            out.addAll(byAddress(ctx, address, key))
        }
        for (s in SmsStore.forAddress(ctx, address)) {
            out.add(Msg(s.body, s.time, true))
        }
        return dedupe(out)
    }

    private fun byAddress(ctx: Context, address: String, key: String): List<Msg> {
        val out = ArrayList<Msg>()
        val wanted = address.filter { it.isDigit() }.takeLast(9)
        if (wanted.length < 7) return out
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                null, null,
                "${Telephony.Sms.DATE} ASC LIMIT $MAX_THREAD"
            )?.use { c ->
                while (c.moveToNext()) {
                    val a = (c.getString(0) ?: "").filter { it.isDigit() }.takeLast(9)
                    if (a != wanted) continue
                    out.add(
                        Msg(
                            decode(ctx, c.getString(1) ?: "", key),
                            c.getLong(2),
                            c.getInt(3) != Telephony.Sms.MESSAGE_TYPE_INBOX
                        )
                    )
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun dedupe(items: List<Msg>): List<Msg> {
        val out = ArrayList<Msg>(items.size)
        for (m in items.sortedBy { it.time }) {
            val dup = out.any {
                it.outgoing == m.outgoing && it.body == m.body && it.imageUri == m.imageUri &&
                    kotlin.math.abs(it.time - m.time) < DUP_WINDOW
            }
            if (!dup) out.add(m)
        }
        return out
    }

    fun threadIdForAddress(ctx: Context, address: String): Long = try {
        Telephony.Threads.getOrCreateThreadId(ctx, address)
    } catch (e: Exception) {
        -1
    }

    fun insertOutbox(ctx: Context, address: String, body: String, threadId: Long, time: Long): Uri? {
        if (!isDefault(ctx)) return null
        return try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, time)
                put(Telephony.Sms.DATE_SENT, time)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                if (threadId > 0) put(Telephony.Sms.THREAD_ID, threadId)
            }
            ctx.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        } catch (e: Exception) {
            null
        }
    }

    fun markSendResult(ctx: Context, row: Uri?, ok: Boolean) {
        if (row == null) return
        try {
            if (ok && typeOf(ctx, row) != Telephony.Sms.MESSAGE_TYPE_OUTBOX) return
            val values = ContentValues().apply {
                put(
                    Telephony.Sms.TYPE,
                    if (ok) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED
                )
            }
            ctx.contentResolver.update(row, values, null, null)
        } catch (e: Exception) {
        }
    }

    private fun typeOf(ctx: Context, row: Uri): Int = try {
        ctx.contentResolver.query(row, arrayOf(Telephony.Sms.TYPE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getInt(0) else -1
        } ?: -1
    } catch (e: Exception) {
        -1
    }

    fun deleteThread(ctx: Context, threadId: Long, address: String): DeleteResult {
        SmsStore.deleteForAddress(ctx, address)
        val id = if (threadId > 0) threadId else threadIdForAddress(ctx, address)
        if (id <= 0) return DeleteResult(isDefault = isDefault(ctx), removed = 0)

        var removed = 0
        var denied = false
        fun attempt(uri: Uri, sel: String, args: Array<String>): Int = try {
            ctx.contentResolver.delete(uri, sel, args)
        } catch (e: SecurityException) {
            denied = true; 0
        } catch (e: Exception) {
            0
        }
        removed += attempt(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.THREAD_ID}=?", arrayOf(id.toString()))
        attempt(Telephony.Mms.CONTENT_URI, "thread_id=?", arrayOf(id.toString()))
        return DeleteResult(isDefault = !denied, removed = removed)
    }

    fun deleteThreadByAddress(ctx: Context, address: String): DeleteResult =
        deleteThread(ctx, threadIdForAddress(ctx, address), address)

    fun incomingMms(ctx: Context, mmsId: Long, key: String): IncomingMms =
        IncomingMms(mmsAddress(ctx, mmsId), decode(ctx, mmsText(ctx, mmsId), key))

    fun latestIncomingMms(ctx: Context, key: String): IncomingMms? {
        return try {
            ctx.contentResolver.query(
                Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms._ID),
                "${Telephony.Mms.MESSAGE_BOX}=${Telephony.Mms.MESSAGE_BOX_INBOX}", null,
                "${Telephony.Mms.DATE} DESC LIMIT 1"
            )?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    IncomingMms(mmsAddress(ctx, id), decode(ctx, mmsText(ctx, id), key))
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun mmsText(ctx: Context, mmsId: Long): String {
        val sb = StringBuilder()
        try {
            ctx.contentResolver.query(
                Uri.parse("content://mms/part"), arrayOf("_id", "text"),
                "mid=? AND ct='text/plain'", arrayOf(mmsId.toString()), null
            )?.use { c ->
                val idi = c.getColumnIndexOrThrow("_id")
                val ti = c.getColumnIndexOrThrow("text")
                while (c.moveToNext()) {
                    val t = c.getString(ti)
                    if (!t.isNullOrEmpty()) {
                        sb.append(t)
                    } else try {
                        ctx.contentResolver.openInputStream(Uri.parse("content://mms/part/${c.getLong(idi)}"))
                            ?.use { sb.append(String(it.readBytes(), Charsets.UTF_8)) }
                    } catch (e: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
        }
        return sb.toString()
    }

    private fun mmsImageUri(ctx: Context, mmsId: Long): String? {
        try {
            ctx.contentResolver.query(
                Uri.parse("content://mms/part"), arrayOf("_id", "ct"),
                "mid=?", arrayOf(mmsId.toString()), null
            )?.use { c ->
                val idi = c.getColumnIndexOrThrow("_id")
                val cti = c.getColumnIndexOrThrow("ct")
                while (c.moveToNext()) {
                    if ((c.getString(cti) ?: "").startsWith("image/")) return "content://mms/part/${c.getLong(idi)}"
                }
            }
        } catch (e: Exception) {
        }
        return null
    }

    private fun mmsAddress(ctx: Context, mmsId: Long, type: Int = 137): String {
        try {
            ctx.contentResolver.query(
                Uri.parse("content://mms/$mmsId/addr"), arrayOf("address"),
                "type=$type", null, null
            )?.use { c ->
                if (c.moveToFirst()) return c.getString(0) ?: ""
            }
        } catch (e: Exception) {
        }
        return ""
    }

    private fun decode(ctx: Context, body: String, key: String): String = try {
        if (!SmsCrypto.isEncrypted(body)) {
            body
        } else {
            val keys = LinkedHashSet<String>()
            if (key.isNotBlank()) keys.add(key)
            keys.addAll(ContactKeys.allKeys(ctx))
            when {
                keys.isEmpty() -> "[encrypted, set a key in Settings or for this contact]"
                else -> SmsCrypto.tryDecrypt(body, keys.toList()) ?: "[encrypted, wrong key]"
            }
        }
    } catch (e: Exception) {
        body
    }
}
