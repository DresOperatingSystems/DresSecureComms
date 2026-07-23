/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.crypto

import android.content.Context
import com.dresos.dressecurecomms.util.SecureKeys
import org.json.JSONObject
import java.io.File

object ContactKeys {
    private const val FILE = "contact_keys.dat"

    private fun load(ctx: Context): MutableMap<String, String> {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return HashMap()
        return try {
            val o = JSONObject(CryptoManager.decrypt(f.readText()))
            val out = HashMap<String, String>()
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                out[k] = o.getString(k)
            }
            out
        } catch (e: Exception) {
            HashMap()
        }
    }

    private fun save(ctx: Context, map: Map<String, String>) {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, v)
        File(ctx.filesDir, FILE).writeText(CryptoManager.encrypt(o.toString()))
    }

    private fun id(address: String): String {
        val a = address.trim()
        if (a.contains(',') || a.contains(';')) return a.lowercase()
        val d = a.filter { it.isDigit() }
        return if (d.length >= 7) d.takeLast(9) else a.lowercase()
    }

    fun set(ctx: Context, address: String, key: String) {
        val map = load(ctx)
        val k = id(address)
        if (key.isBlank()) map.remove(k) else map[k] = key
        save(ctx, map)
    }

    fun forAddress(ctx: Context, address: String): String? =
        load(ctx)[id(address)]?.takeIf { it.isNotBlank() }

    fun has(ctx: Context, address: String): Boolean = forAddress(ctx, address) != null

    fun keyFor(ctx: Context, address: String): String =
        forAddress(ctx, address) ?: SecureKeys.smsKey(ctx)

    fun candidates(ctx: Context, address: String): List<String> {
        val out = LinkedHashSet<String>()
        forAddress(ctx, address)?.let { out.add(it) }
        SecureKeys.smsKey(ctx).takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (v in load(ctx).values) if (v.isNotBlank()) out.add(v)
        return out.toList()
    }

    fun allKeys(ctx: Context): List<String> = load(ctx).values.filter { it.isNotBlank() }

    fun anyConfigured(ctx: Context): Boolean =
        load(ctx).isNotEmpty() || SecureKeys.smsKey(ctx).isNotBlank()

    fun count(ctx: Context): Int = load(ctx).size
}
