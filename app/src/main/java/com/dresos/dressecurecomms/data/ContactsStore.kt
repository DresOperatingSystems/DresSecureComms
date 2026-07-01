/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.data

import android.content.Context
import com.dresos.dressecurecomms.crypto.CryptoManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ContactsStore {
    private const val FILE = "contacts.dat"
    data class Contact(val name: String, val number: String, val email: String = "")

    fun load(ctx: Context): List<Contact> {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(CryptoManager.decrypt(f.readText()))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Contact(o.getString("n"), o.getString("p"), o.optString("e", ""))
            }.sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(ctx: Context, contact: Contact) {
        val list = load(ctx).toMutableList()
        if (list.none { it.name == contact.name && it.number == contact.number }) {
            list.add(contact)
            save(ctx, list)
        }
    }

    fun addAll(ctx: Context, contacts: List<Contact>) {
        val list = load(ctx).toMutableList()
        for (c in contacts) if (list.none { it.name == c.name && it.number == c.number }) list.add(c)
        save(ctx, list)
    }

    fun update(ctx: Context, original: Contact, updated: Contact) {
        var matched = false
        val list = load(ctx).map {
            if (!matched && it.name == original.name && it.number == original.number) {
                matched = true; updated
            } else it
        }.toMutableList()
        if (!matched) list.add(updated)
        save(ctx, list)
    }

    fun delete(ctx: Context, contact: Contact) {
        save(ctx, load(ctx).filterNot { it.name == contact.name && it.number == contact.number })
    }

    private fun save(ctx: Context, list: List<Contact>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("n", it.name).put("p", it.number).put("e", it.email))
        }
        File(ctx.filesDir, FILE).writeText(CryptoManager.encrypt(arr.toString()))
    }
}
