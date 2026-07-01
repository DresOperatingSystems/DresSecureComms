/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.data

import android.content.Context
import com.dresos.dressecurecomms.crypto.CryptoManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SmsStore {
    private const val FILE = "sent_sms.dat"
    data class Sent(val address: String, val body: String, val time: Long)

    fun add(ctx: Context, address: String, body: String, time: Long) {
        val list = load(ctx).toMutableList()
        list.add(Sent(address, body, time))
        save(ctx, list)
    }

    fun forAddress(ctx: Context, address: String): List<Sent> =
        load(ctx).filter { it.address == address }

    fun load(ctx: Context): List<Sent> {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(CryptoManager.decrypt(f.readText()))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Sent(o.getString("a"), o.getString("b"), o.getLong("t"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deleteForAddress(ctx: Context, address: String) {
        save(ctx, load(ctx).filterNot { it.address == address })
    }

    private fun save(ctx: Context, list: List<Sent>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("a", it.address).put("b", it.body).put("t", it.time)) }
        File(ctx.filesDir, FILE).writeText(CryptoManager.encrypt(arr.toString()))
    }
}
