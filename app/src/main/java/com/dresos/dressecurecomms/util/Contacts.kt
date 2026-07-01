/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
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
