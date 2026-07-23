/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.scan

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object FileScanner {
    data class Target(val label: String, val path: String, val pkg: String? = null)

    private fun hash(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(8192)
        input.use {
            while (true) {
                val n = it.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { b -> "%02x".format(b.toInt() and 0xff) }
    }

    fun sha256(ctx: Context, uri: Uri): String {
        val stream = ctx.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open that file.")
        return hash(stream)
    }

    fun sha256(file: File): String = hash(file.inputStream())

    fun displayName(ctx: Context, uri: Uri): String {
        return try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) else null
            } ?: uri.lastPathSegment ?: "file"
        } catch (e: Exception) {
            uri.lastPathSegment ?: "file"
        }
    }

    fun sizeOf(ctx: Context, uri: Uri): Long = try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (i >= 0 && c.moveToFirst()) c.getLong(i) else -1L
        } ?: -1L
    } catch (e: Exception) {
        -1L
    }

    fun installedApps(ctx: Context, includeSystem: Boolean = false): List<Target> {
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            pm.queryIntentActivities(launcher, 0)
        } catch (e: Exception) {
            emptyList<ResolveInfo>()
        }
        val out = LinkedHashMap<String, Target>()
        for (r in resolved) {
            val info = r.activityInfo?.applicationInfo ?: continue
            if (!includeSystem && (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            val path = info.sourceDir
            if (path.isNullOrBlank() || !File(path).exists()) continue
            out[info.packageName] = Target(pm.getApplicationLabel(info).toString(), path, info.packageName)
        }
        return out.values.sortedBy { it.label.lowercase() }
    }
}
