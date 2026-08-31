/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.scan

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

object OfflineScanner {

    data class Match(val name: String, val algo: String)
    data class Import(val ok: Boolean, val count: Int, val error: String? = null)

    private var md5 = HashMap<String, String>()
    private var sha1 = HashMap<String, String>()
    private var sha256 = HashMap<String, String>()
    @Volatile private var loaded = false

    fun ready(): Boolean = loaded

    fun signatureCount(): Int = md5.size + sha1.size + sha256.size

    private fun dbFile(ctx: Context): File = File(ctx.filesDir, "signatures/hashes.hsb")

    @Synchronized
    fun load(ctx: Context) {
        if (loaded && signatureCount() > 0) return
        reload(ctx)
    }

    @Synchronized
    fun reload(ctx: Context) {
        val m5 = HashMap<String, String>()
        val s1 = HashMap<String, String>()
        val s256 = HashMap<String, String>()
        val imported = readImported(ctx)
        val lines = if (!imported.isNullOrEmpty()) imported
            else readAsset(ctx, "signatures/hashes.hsb") ?: emptyList()
        for (line in lines) {
            val parts = line.split(":")
            if (parts.size >= 3) {
                val hash = parts[0].lowercase()
                val name = parts[2]
                when (hash.length) {
                    32 -> m5[hash] = name
                    40 -> s1[hash] = name
                    64 -> s256[hash] = name
                }
            }
        }
        md5 = m5
        sha1 = s1
        sha256 = s256
        loaded = true
    }

    fun importFrom(ctx: Context, uri: Uri, merge: Boolean): Import {
        val parsed = try {
            val raw = ctx.contentResolver.openInputStream(uri) ?: return Import(false, 0, "open_failed")
            parseSignatures(wrap(uri, raw))
        } catch (e: Exception) {
            return Import(false, 0, "read_failed")
        }
        if (parsed.isEmpty()) return Import(false, 0, "no_signatures")

        val existing = if (merge) (readImported(ctx) ?: emptyList()) else emptyList()
        val keep = LinkedHashMap<String, String>()
        for (line in existing) {
            val h = line.substringBefore(":").lowercase()
            if (h.isNotEmpty()) keep[h] = line
        }
        for (line in parsed) {
            val h = line.substringBefore(":").lowercase()
            keep[h] = line
        }

        return try {
            val dir = File(ctx.filesDir, "signatures")
            dir.mkdirs()
            val tmp = File(dir, "hashes.tmp")
            tmp.writeText(keep.values.joinToString("\n"))
            val dest = dbFile(ctx)
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                dest.writeText(keep.values.joinToString("\n"))
                tmp.delete()
            }
            reload(ctx)
            Import(true, signatureCount())
        } catch (e: Exception) {
            Import(false, 0, "write_failed")
        }
    }

    fun scanFile(ctx: Context, file: File): Match? {
        load(ctx)
        return match(file.inputStream())
    }

    fun scanStream(ctx: Context, input: InputStream): Match? {
        load(ctx)
        return match(input)
    }

    private fun parseSignatures(input: InputStream): List<String> {
        val out = ArrayList<String>()
        input.bufferedReader().useLines { seq ->
            for (raw in seq) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                if (line.length > 512) continue
                val parts = line.split(":")
                if (parts.size < 3) continue
                val hash = parts[0].lowercase()
                if (hash.length != 32 && hash.length != 40 && hash.length != 64) continue
                if (!hash.all { it in "0123456789abcdef" }) continue
                out.add(line)
            }
        }
        return out
    }

    private fun match(input: InputStream): Match? {
        val m5 = MessageDigest.getInstance("MD5")
        val s1 = MessageDigest.getInstance("SHA-1")
        val s256 = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(8192)
        input.use {
            while (true) {
                val n = it.read(buf)
                if (n <= 0) break
                m5.update(buf, 0, n)
                s1.update(buf, 0, n)
                s256.update(buf, 0, n)
            }
        }
        hex(s256.digest()).let { h -> sha256[h]?.let { return Match(it, "SHA-256") } }
        hex(s1.digest()).let { h -> sha1[h]?.let { return Match(it, "SHA-1") } }
        hex(m5.digest()).let { h -> md5[h]?.let { return Match(it, "MD5") } }
        return null
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { b -> "%02x".format(b.toInt() and 0xff) }

    private fun wrap(uri: Uri, raw: InputStream): InputStream =
        if (uri.toString().endsWith(".gz")) GZIPInputStream(raw) else raw

    private fun readImported(ctx: Context): List<String>? {
        val f = dbFile(ctx)
        if (!f.exists()) return null
        return try {
            f.bufferedReader().useLines { seq ->
                seq.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readAsset(ctx: Context, path: String): List<String>? {
        return try {
            val raw = ctx.assets.open(path)
            val stream = if (path.endsWith(".gz")) GZIPInputStream(raw) else raw
            stream.bufferedReader().useLines { seq ->
                seq.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
            }
        } catch (e: Exception) {
            null
        }
    }
}
