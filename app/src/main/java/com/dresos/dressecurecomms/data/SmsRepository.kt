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
import com.dresos.dressecurecomms.util.PhoneKey

object SmsRepository {
    data class Conversation(val threadId: Long, val address: String, val snippet: String, val time: Long)
    data class Msg(val body: String, val time: Long, val outgoing: Boolean, val imageUri: String? = null)
    data class IncomingMms(val address: String, val body: String)
    data class DeleteResult(val isDefault: Boolean, val removed: Int)

    private const val MAX_SCAN = 5000
    private const val MAX_THREAD = 2000
    private const val DUP_WINDOW = 1000L

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
        val byThread = LinkedHashMap<Long, Conversation>()
        val cols = arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val cursor = try {
            ctx.contentResolver.query(Telephony.Sms.CONTENT_URI, cols, null, null, "${Telephony.Sms.DATE} DESC")
        } catch (e: Exception) {
            null
        }
        cursor?.use { c ->
            val ti = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val ai = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bi = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val di = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            var scanned = 0
            while (c.moveToNext() && scanned < MAX_SCAN) {
                scanned++
                val threadId = c.getLong(ti)
                val addr = c.getString(ai) ?: continue
                val body = decode(ctx, c.getString(bi) ?: "", key)
                val time = c.getLong(di)

                if (!byThread.containsKey(threadId)) {
                    byThread[threadId] = Conversation(threadId, addr, body, time)
                }
            }
        }
        val mcols = arrayOf(Telephony.Mms._ID, Telephony.Mms.THREAD_ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX)
        val mcur = try {
            ctx.contentResolver.query(Telephony.Mms.CONTENT_URI, mcols, null, null, "${Telephony.Mms.DATE} DESC")
        } catch (e: Exception) {
            null
        }
        mcur?.use { c ->
            val idi = c.getColumnIndexOrThrow(Telephony.Mms._ID)
            val ti = c.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
            val di = c.getColumnIndexOrThrow(Telephony.Mms.DATE)
            val boxi = c.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)
            var scanned = 0
            while (c.moveToNext() && scanned < MAX_SCAN) {
                scanned++
                val threadId = c.getLong(ti)
                val time = c.getLong(di) * 1000
                val existing = byThread[threadId]
                if (existing != null && existing.time >= time) continue
                val id = c.getLong(idi)
                val incoming = c.getInt(boxi) == Telephony.Mms.MESSAGE_BOX_INBOX
                var addr = existing?.address
                if (addr.isNullOrBlank()) addr = mmsAddress(ctx, id, if (incoming) 137 else 151)
                if (addr.isBlank()) continue
                val body = decode(ctx, mmsText(ctx, id), key).ifBlank { "[photo]" }
                byThread[threadId] = Conversation(threadId, addr, body, time)
            }
        }
        return merge(byThread.values)
    }

    private fun merge(items: Collection<Conversation>): List<Conversation> {
        val out = LinkedHashMap<String, Conversation>()
        for (c in items.sortedByDescending { it.time }) {
            val k = PhoneKey.key(c.address).ifEmpty { "thread:${c.threadId}" }
            if (!out.containsKey(k)) out[k] = c
        }
        return out.values.sortedByDescending { it.time }
    }

    fun threadIdsFor(ctx: Context, threadId: Long, address: String): Set<Long> {
        val out = LinkedHashSet<Long>()
        if (threadId > 0) out.add(threadId)
        if (address.isBlank()) return out
        val resolved = threadIdForAddress(ctx, address)
        if (resolved > 0) out.add(resolved)
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS),
                null, null, "${Telephony.Sms.DATE} DESC"
            )?.use { c ->
                val ti = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val ai = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                var scanned = 0
                while (c.moveToNext() && scanned < MAX_SCAN) {
                    scanned++
                    val addr = c.getString(ai) ?: continue
                    if (PhoneKey.same(addr, address)) out.add(c.getLong(ti))
                }
            }
        } catch (e: Exception) {
        }
        return out.filter { it > 0 }.toSet()
    }

    fun threadById(ctx: Context, threadId: Long, address: String, key: String): List<Msg> {
        val ids = threadIdsFor(ctx, threadId, address)
        val out = ArrayList<Msg>()
        if (ids.isNotEmpty()) {
            val holes = ids.joinToString(",") { "?" }
            val args = ids.map { it.toString() }.toTypedArray()

            val cols = arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE)
            val cursor = try {
                ctx.contentResolver.query(
                    Telephony.Sms.CONTENT_URI, cols,
                    "${Telephony.Sms.THREAD_ID} IN ($holes)", args,
                    "${Telephony.Sms.DATE} ASC"
                )
            } catch (e: Exception) {
                null
            }
            cursor?.use { c ->
                val bi = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val di = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val tyi = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                while (c.moveToNext() && out.size < MAX_THREAD) {
                    val body = decode(ctx, c.getString(bi) ?: "", key)
                    val time = c.getLong(di)
                    val outgoing = c.getInt(tyi) != Telephony.Sms.MESSAGE_TYPE_INBOX
                    out.add(Msg(body, time, outgoing))
                }
            }

            val mcols = arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX)
            val mcur = try {
                ctx.contentResolver.query(
                    Telephony.Mms.CONTENT_URI, mcols,
                    "${Telephony.Mms.THREAD_ID} IN ($holes)", args,
                    "${Telephony.Mms.DATE} ASC"
                )
            } catch (e: Exception) {
                null
            }
            mcur?.use { c ->
                val idi = c.getColumnIndexOrThrow(Telephony.Mms._ID)
                val di = c.getColumnIndexOrThrow(Telephony.Mms.DATE)
                val boxi = c.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)
                while (c.moveToNext() && out.size < MAX_THREAD) {
                    val id = c.getLong(idi)
                    val time = c.getLong(di) * 1000
                    val outgoing = c.getInt(boxi) != Telephony.Mms.MESSAGE_BOX_INBOX
                    out.add(Msg(decode(ctx, mmsText(ctx, id), key), time, outgoing, mmsImageUri(ctx, id)))
                }
            }
        }
        for (s in SmsStore.forAddress(ctx, address)) {
            out.add(Msg(s.body, s.time, true))
        }
        return dedupe(out)
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

    fun threadIdForAddress(ctx: Context, address: String): Long =
        try { Telephony.Threads.getOrCreateThreadId(ctx, address) } catch (e: Exception) { -1 }

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

        var removed = 0
        var denied = false

        fun attempt(uri: Uri, sel: String, args: Array<String>): Int = try {
            ctx.contentResolver.delete(uri, sel, args)
        } catch (e: SecurityException) {
            denied = true; 0
        } catch (e: Exception) {
            0
        }

        for (tid in threadIdsFor(ctx, threadId, address)) {
            removed += attempt(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.THREAD_ID}=?", arrayOf(tid.toString()))
            attempt(Telephony.Mms.CONTENT_URI, "thread_id=?", arrayOf(tid.toString()))
        }

        if (removed == 0 && !denied) {
            try {
                val ids = ArrayList<Long>()
                ctx.contentResolver.query(
                    Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS),
                    null, null, null
                )?.use { c ->
                    val i = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                    val ai = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    var scanned = 0
                    while (c.moveToNext() && scanned < MAX_SCAN) {
                        scanned++
                        val addr = c.getString(ai) ?: continue
                        if (PhoneKey.same(addr, address)) ids.add(c.getLong(i))
                    }
                }
                for (id in ids) {
                    removed += attempt(Telephony.Sms.CONTENT_URI, "${Telephony.Sms._ID}=?", arrayOf(id.toString()))
                }
            } catch (e: Exception) {
            }
        }
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
                "${Telephony.Mms.DATE} DESC"
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
