/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.scan

import android.content.Context
import androidx.preference.PreferenceManager
import com.dresos.dressecurecomms.data.SpamStore
import com.dresos.dressecurecomms.util.Contacts

object SpamFilter {
    enum class Action { ALLOW, SILENCE, BLOCK }

    data class Result(val action: Action, val reason: String)

    private val PREMIUM_PREFIXES = listOf("090", "091", "0900", "1900", "900")

    private fun prefs(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx)

    private fun digits(s: String): String = s.filter { it.isDigit() }

    private fun looksPremium(number: String): Boolean {
        val d = digits(number)
        if (d.isEmpty()) return false
        return PREMIUM_PREFIXES.any { d.startsWith(it) }
    }

    private fun looksShortCode(number: String): Boolean {
        val d = digits(number)
        return d.isNotEmpty() && d.length <= 6
    }

    private fun looksSpoofed(ctx: Context, number: String): Boolean {
        val own = try {
            prefs(ctx).getString("own_number", "").orEmpty()
        } catch (e: Exception) {
            ""
        }
        if (own.isBlank()) return false
        val a = digits(own)
        val b = digits(number)
        if (a.length < 8 || b.length < 8) return false
        return a.take(6) == b.take(6) && a != b
    }

    fun evaluate(ctx: Context, rawNumber: String?): Result {
        val number = rawNumber?.trim().orEmpty()
        val p = prefs(ctx)

        if (number.isNotEmpty() && SpamStore.isAllowed(ctx, number)) {
            return Result(Action.ALLOW, "on your allow list")
        }
        if (number.isNotEmpty() && SpamStore.isBlocked(ctx, number)) {
            return Result(Action.BLOCK, "on your block list")
        }
        if (number.isEmpty()) {
            return if (p.getBoolean("spam_block_withheld", false)) {
                Result(Action.BLOCK, "withheld number")
            } else {
                Result(Action.ALLOW, "")
            }
        }

        val known = try {
            Contacts.isSaved(ctx, number)
        } catch (e: Exception) {
            false
        }
        if (known) return Result(Action.ALLOW, "saved contact")

        if (p.getBoolean("spam_block_premium", true) && looksPremium(number)) {
            return Result(Action.BLOCK, "premium rate prefix")
        }
        if (p.getBoolean("spam_block_premium", true) && looksShortCode(number)) {
            return Result(Action.SILENCE, "short code")
        }
        if (p.getBoolean("spam_flag_spoofed", true) && looksSpoofed(ctx, number)) {
            return Result(Action.SILENCE, "looks like a spoofed local number")
        }
        if (p.getBoolean("spam_block_unknown", false)) {
            return Result(Action.SILENCE, "not in your contacts")
        }
        return Result(Action.ALLOW, "")
    }

    fun label(ctx: Context, number: String?): String? {
        val n = number?.trim().orEmpty()
        if (n.isEmpty()) return null
        val rule = SpamStore.ruleFor(ctx, n) ?: return null
        return if (rule.blocked) "Blocked: ${rule.reason}" else null
    }
}
