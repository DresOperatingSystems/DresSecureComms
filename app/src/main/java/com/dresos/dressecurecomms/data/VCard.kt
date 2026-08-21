/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.data

import java.io.ByteArrayOutputStream

object VCard {

    fun parse(text: String): List<ContactsStore.Contact> {
        val out = ArrayList<ContactsStore.Contact>()
        var name = ""
        var number = ""
        var email = ""
        var inCard = false
        for (raw in unfold(text)) {
            val line = raw.trim()
            when {
                line.equals("BEGIN:VCARD", true) -> {
                    inCard = true; name = ""; number = ""; email = ""
                }
                line.equals("END:VCARD", true) -> {
                    if (number.isNotEmpty()) {
                        out.add(ContactsStore.Contact(if (name.isNotEmpty()) name else number, number, email))
                    }
                    inCard = false
                }
                inCard -> {
                    val colon = line.indexOf(':')
                    if (colon <= 0) continue
                    val head = line.substring(0, colon)
                    val params = head.split(";")
                    val field = params[0].uppercase()
                    val quoted = params.any { it.uppercase().contains("QUOTED-PRINTABLE") }
                    val rawValue = line.substring(colon + 1)
                    val value = decode(if (quoted) quotedPrintable(rawValue) else rawValue).trim()
                    when {
                        field == "FN" && value.isNotEmpty() -> name = value
                        field == "N" && name.isEmpty() -> {
                            val parts = value.split(";")
                            val built = ((parts.getOrNull(1) ?: "") + " " + (parts.getOrNull(0) ?: "")).trim()
                            if (built.isNotEmpty()) name = built
                        }
                        field == "TEL" && number.isEmpty() -> number = value
                        field == "EMAIL" && email.isEmpty() -> email = value
                    }
                }
            }
        }
        return out
    }

    fun write(contacts: List<ContactsStore.Contact>): String {
        val sb = StringBuilder()
        for (c in contacts) {
            sb.append("BEGIN:VCARD\r\n")
            sb.append("VERSION:3.0\r\n")
            sb.append("FN:").append(escape(c.name)).append("\r\n")
            sb.append("TEL;TYPE=CELL:").append(escape(c.number)).append("\r\n")
            if (c.email.isNotEmpty()) sb.append("EMAIL:").append(escape(c.email)).append("\r\n")
            sb.append("END:VCARD\r\n")
        }
        return sb.toString()
    }

    private fun unfold(text: String): List<String> {
        val lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val out = ArrayList<String>()
        for (l in lines) {
            if ((l.startsWith(" ") || l.startsWith("\t")) && out.isNotEmpty()) {
                out[out.size - 1] = out[out.size - 1] + l.substring(1)
            } else {
                out.add(l)
            }
        }
        return out
    }

    private fun quotedPrintable(v: String): String {
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < v.length) {
            val ch = v[i]
            if (ch == '=' && i + 2 < v.length) {
                val hex = v.substring(i + 1, i + 3)
                val code = hex.toIntOrNull(16)
                if (code != null) {
                    bytes.write(code)
                    i += 3
                    continue
                }
            }
            bytes.write(ch.code)
            i++
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    private fun decode(v: String): String =
        v.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun escape(v: String): String =
        v.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
}
