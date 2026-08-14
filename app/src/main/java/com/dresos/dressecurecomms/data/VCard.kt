/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.data

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
                    val field = line.substring(0, colon).uppercase()
                    val value = decode(line.substring(colon + 1)).trim()
                    when {
                        field == "FN" && value.isNotEmpty() -> name = value
                        field.startsWith("N;") || field == "N" -> {
                            if (name.isEmpty()) {
                                val parts = value.split(";")
                                val built = ((parts.getOrNull(1) ?: "") + " " + (parts.getOrNull(0) ?: "")).trim()
                                if (built.isNotEmpty()) name = built
                            }
                        }
                        field.startsWith("TEL") && number.isEmpty() -> number = value
                        field.startsWith("EMAIL") && email.isEmpty() -> email = value
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

    private fun decode(v: String): String =
        v.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun escape(v: String): String =
        v.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
}
