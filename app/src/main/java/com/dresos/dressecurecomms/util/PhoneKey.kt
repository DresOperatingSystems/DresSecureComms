/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.util

object PhoneKey {
    private const val TAIL = 9
    private const val MIN = 7

    fun digits(s: String): String = s.filter { it.isDigit() }

    fun isGroup(address: String): Boolean = address.contains(',') || address.contains(';')

    fun key(address: String): String {
        val a = address.trim()
        if (a.isEmpty()) return ""
        if (isGroup(a)) {
            return a.split(Regex("[,;]"))
                .map { key(it) }
                .filter { it.isNotEmpty() }
                .sorted()
                .joinToString(",")
        }
        val d = digits(a)
        return if (d.length >= MIN) d.takeLast(TAIL) else a.lowercase()
    }

    fun same(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val ka = key(a)
        val kb = key(b)
        if (ka.isEmpty() || kb.isEmpty()) return false
        if (ka == kb) return true
        if (isGroup(a) || isGroup(b)) return false
        val da = digits(a)
        val db = digits(b)
        if (da.isEmpty() || db.isEmpty()) return a.trim().equals(b.trim(), ignoreCase = true)
        val n = minOf(da.length, db.length, TAIL)
        return n >= MIN && da.takeLast(n) == db.takeLast(n)
    }
}
