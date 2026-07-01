/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.data

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.provider.Telephony
import com.dresos.dressecurecomms.crypto.SmsCrypto

object SmsRepository {
    data class Conversation(val threadId: Long, val address: String, val snippet: String, val time: Long)
    data class Msg(val body: String, val time: Long, val outgoing: Boolean)
    data class DeleteResult(val isDefault: Boolean, val removed: Int)

    private const val MAX_SCAN = 5000
    private const val MAX_THREAD = 2000

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
        } ?: return emptyList()
        cursor.use { c ->
            val ti = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val ai = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bi = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val di = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            var scanned = 0
            while (c.moveToNext() && scanned < MAX_SCAN) {
                scanned++
                val threadId = c.getLong(ti)
                val addr = c.getString(ai) ?: continue
                val body = decode(c.getString(bi) ?: "", key)
                val time = c.getLong(di)

                if (!byThread.containsKey(threadId)) {
                    byThread[threadId] = Conversation(threadId, addr, body, time)
                }
            }
        }
        return byThread.values.sortedByDescending { it.time }
    }

    fun threadById(ctx: Context, threadId: Long, key: String): List<Msg> {
        if (threadId <= 0) return emptyList()
        val out = ArrayList<Msg>()
        val cols = arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE)
        val cursor = try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI, cols,
                "${Telephony.Sms.THREAD_ID}=?", arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC"
            )
        } catch (e: Exception) {
            null
        } ?: return out
        cursor.use { c ->
            val bi = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val di = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val tyi = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            while (c.moveToNext() && out.size < MAX_THREAD) {
                val body = decode(c.getString(bi) ?: "", key)
                val time = c.getLong(di)
                val outgoing = c.getInt(tyi) != Telephony.Sms.MESSAGE_TYPE_INBOX
                out.add(Msg(body, time, outgoing))
            }
        }
        return out
    }

    fun threadIdForAddress(ctx: Context, address: String): Long =
        try { Telephony.Threads.getOrCreateThreadId(ctx, address) } catch (e: Exception) { -1 }

    fun deleteThread(ctx: Context, threadId: Long, address: String): DeleteResult {
        SmsStore.deleteForAddress(ctx, address)

        var removed = 0
        var denied = false
        val tid = if (threadId > 0) threadId else threadIdForAddress(ctx, address)

        fun attempt(uri: android.net.Uri, sel: String, args: Array<String>): Int = try {
            ctx.contentResolver.delete(uri, sel, args)
        } catch (e: SecurityException) {
            denied = true; 0
        } catch (_: Exception) {
            0
        }

        if (tid > 0) {
            removed += attempt(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.THREAD_ID}=?", arrayOf(tid.toString()))
            attempt(Telephony.Mms.CONTENT_URI, "thread_id=?", arrayOf(tid.toString()))
        }

        if (removed == 0 && !denied) {
            try {
                val ids = ArrayList<Long>()
                ctx.contentResolver.query(
                    Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID),
                    "${Telephony.Sms.ADDRESS}=?", arrayOf(address), null
                )?.use { c ->
                    val i = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                    while (c.moveToNext()) ids.add(c.getLong(i))
                }
                for (id in ids) {
                    removed += attempt(Telephony.Sms.CONTENT_URI, "${Telephony.Sms._ID}=?", arrayOf(id.toString()))
                }
            } catch (_: Exception) {
            }
            removed += attempt(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.ADDRESS}=?", arrayOf(address))
        }
        return DeleteResult(isDefault = !denied, removed = removed)
    }

    fun deleteThreadByAddress(ctx: Context, address: String): DeleteResult =
        deleteThread(ctx, threadIdForAddress(ctx, address), address)

    private fun decode(body: String, key: String): String = when {
        !SmsCrypto.isEncrypted(body) -> body
        key.isBlank() -> "[encrypted, set the shared key in Settings]"
        else -> try { SmsCrypto.decrypt(body, key) } catch (e: Exception) { "[encrypted, wrong key]" }
    }
}
