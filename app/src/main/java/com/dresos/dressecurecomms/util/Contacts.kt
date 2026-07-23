/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.util

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.dresos.dressecurecomms.data.ContactsStore

object Contacts {
    fun nameFor(ctx: Context, number: String): String {
        if (number.isBlank()) return number
        return fromStore(ContactsStore.load(ctx), number) ?: fromSystem(ctx, number) ?: number
    }

    fun isSaved(ctx: Context, number: String): Boolean {
        if (number.isBlank()) return false
        if (number.contains(',') || number.contains(';')) return false
        if (fromStore(ContactsStore.load(ctx), number) != null) return true
        return fromSystem(ctx, number) != null
    }

    fun nameMap(ctx: Context, numbers: Collection<String>): Map<String, String> {
        val stored = ContactsStore.load(ctx)
        val out = HashMap<String, String>()
        for (n in numbers.toSet()) {
            if (n.isBlank()) continue
            val name = fromStore(stored, n) ?: fromSystem(ctx, n)
            if (name != null) out[n] = name
        }
        return out
    }

    data class Suggestion(val name: String, val number: String) {
        override fun toString(): String =
            if (name.isBlank() || name == number) number else "$name ($number)"
    }

    fun suggestions(ctx: Context): List<Suggestion> {
        val out = LinkedHashMap<String, Suggestion>()
        for (c in ContactsStore.load(ctx)) {
            if (c.number.isBlank()) continue
            val k = digits(c.number).ifEmpty { c.number }
            out[k] = Suggestion(c.name, c.number)
        }
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0).orEmpty()
                    val num = c.getString(1).orEmpty()
                    if (num.isBlank()) continue
                    val k = digits(num).ifEmpty { num }
                    if (!out.containsKey(k)) out[k] = Suggestion(name, num)
                }
            }
        } catch (e: Exception) {
        }
        return out.values.toList()
    }

    private fun digits(s: String): String = s.filter { it.isDigit() }

    private fun sameNumber(a: String, b: String): Boolean {
        val da = digits(a)
        val db = digits(b)
        if (da.isEmpty() || db.isEmpty()) return a == b
        if (da == db) return true
        val n = minOf(da.length, db.length, 9)
        return n >= 7 && da.takeLast(n) == db.takeLast(n)
    }

    private fun fromStore(stored: List<ContactsStore.Contact>, number: String): String? =
        stored.firstOrNull { sameNumber(it.number, number) }?.name

    private fun fromSystem(ctx: Context, number: String): String? = try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null }
    } catch (e: Exception) {
        null
    }
}
