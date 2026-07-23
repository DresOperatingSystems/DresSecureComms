/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.data

import android.content.Context
import com.dresos.dressecurecomms.crypto.CryptoManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SpamStore {
    private const val FILE = "spam_rules.dat"

    data class Rule(val number: String, val blocked: Boolean, val reason: String, val at: Long)

    fun load(ctx: Context): List<Rule> {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(CryptoManager.decrypt(f.readText()))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Rule(o.getString("n"), o.optBoolean("b", true), o.optString("r", ""), o.optLong("t", 0L))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(ctx: Context, list: List<Rule>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("n", it.number).put("b", it.blocked)
                    .put("r", it.reason).put("t", it.at)
            )
        }
        File(ctx.filesDir, FILE).writeText(CryptoManager.encrypt(arr.toString()))
    }

    private fun digits(s: String): String = s.filter { it.isDigit() }

    private fun same(a: String, b: String): Boolean {
        val da = digits(a)
        val db = digits(b)
        if (da.isEmpty() || db.isEmpty()) return a.trim() == b.trim()
        if (da == db) return true
        val n = minOf(da.length, db.length, 9)
        return n >= 7 && da.takeLast(n) == db.takeLast(n)
    }

    fun ruleFor(ctx: Context, number: String): Rule? =
        load(ctx).firstOrNull { same(it.number, number) }

    fun isBlocked(ctx: Context, number: String): Boolean = ruleFor(ctx, number)?.blocked == true

    fun isAllowed(ctx: Context, number: String): Boolean = ruleFor(ctx, number)?.blocked == false

    fun block(ctx: Context, number: String, reason: String) = put(ctx, number, true, reason)

    fun allow(ctx: Context, number: String) = put(ctx, number, false, "allowed by you")

    private fun put(ctx: Context, number: String, blocked: Boolean, reason: String) {
        if (number.isBlank()) return
        val list = load(ctx).filterNot { same(it.number, number) }.toMutableList()
        list.add(Rule(number.trim(), blocked, reason, System.currentTimeMillis()))
        save(ctx, list)
    }

    fun remove(ctx: Context, number: String) {
        save(ctx, load(ctx).filterNot { same(it.number, number) })
    }

    fun clear(ctx: Context) {
        File(ctx.filesDir, FILE).delete()
    }
}
