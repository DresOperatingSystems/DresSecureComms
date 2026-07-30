/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.net

import android.util.Base64
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

object VirusTotalClient {
    private const val BASE = "https://www.virustotal.com/api/v3"
    private const val DIRECT_LIMIT = 32L * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val uploadClient = client.newBuilder()
        .writeTimeout(15, TimeUnit.MINUTES)
        .readTimeout(2, TimeUnit.MINUTES)
        .build()

    fun scanUrl(url: String, apiKey: String): String {
        if (apiKey.isBlank()) return "Add your VirusTotal API key in Settings first."
        if (url.isBlank()) return "Enter a URL to scan."
        return try {
            val id = Base64.encodeToString(
                url.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val req = Request.Builder().url("$BASE/urls/$id").header("x-apikey", apiKey).get().build()
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    401 -> return "Invalid API key. Check it in Settings."
                    429 -> throw RateLimited()
                    404 -> {
                        submit(url, apiKey)
                        return analyzing(url)
                    }
                }
                if (!resp.isSuccessful) return "VirusTotal returned an error (${resp.code}). Try again shortly."
                val attr = JSONObject(resp.body?.string().orEmpty())
                    .optJSONObject("data")?.optJSONObject("attributes")
                val stats = attr?.optJSONObject("last_analysis_stats")
                if (attr == null || !attr.has("last_analysis_date") || stats == null || stats.length() == 0) {
                    analyzing(url)
                } else {
                    format(url, stats)
                }
            }
        } catch (e: RateLimited) {
            e.message ?: "Rate limited. Wait a minute and try again."
        } catch (e: IOException) {
            "No connection to VirusTotal. Check your internet and try again."
        } catch (e: Exception) {
            "Scan failed: ${e.message ?: "unexpected error"}"
        }
    }

    enum class State { CLEAN, MALICIOUS, SUSPICIOUS, UNKNOWN, PENDING, BAD_KEY, RATE_LIMITED, ERROR }

    data class Upload(val analysisId: String = "", val error: String = "")

    data class Verdict(
        val state: State,
        val malicious: Int = 0,
        val suspicious: Int = 0,
        val total: Int = 0,
        val detail: String = ""
    )

    fun fileVerdict(sha256: String, apiKey: String): Verdict {
        if (apiKey.isBlank()) return Verdict(State.BAD_KEY, detail = "Add your VirusTotal API key in Settings first.")
        return try {
            val req = Request.Builder().url("$BASE/files/$sha256").header("x-apikey", apiKey).get().build()
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    401 -> return Verdict(State.BAD_KEY, detail = "Invalid API key. Check it in Settings.")
                    429 -> return Verdict(State.RATE_LIMITED, detail = "Rate limited. Wait a minute and try again.")
                    404 -> return Verdict(State.UNKNOWN, detail = "VirusTotal has never seen this file.")
                }
                if (!resp.isSuccessful) {
                    return Verdict(State.ERROR, detail = "VirusTotal returned an error (${resp.code}).")
                }
                val stats = JSONObject(resp.body?.string().orEmpty())
                    .optJSONObject("data")?.optJSONObject("attributes")?.optJSONObject("last_analysis_stats")
                    ?: return Verdict(State.UNKNOWN, detail = "No analysis available yet.")
                val mal = stats.optInt("malicious")
                val sus = stats.optInt("suspicious")
                val total = mal + sus + stats.optInt("harmless") + stats.optInt("undetected")
                val state = when {
                    mal > 0 -> State.MALICIOUS
                    sus > 0 -> State.SUSPICIOUS
                    else -> State.CLEAN
                }
                Verdict(state, mal, sus, total)
            }
        } catch (e: IOException) {
            Verdict(State.ERROR, detail = "No connection to VirusTotal.")
        } catch (e: Exception) {
            Verdict(State.ERROR, detail = e.message ?: "unexpected error")
        }
    }

    fun uploadFile(name: String, size: Long, apiKey: String, open: () -> InputStream): Upload {
        if (apiKey.isBlank()) return Upload(error = "Add your VirusTotal API key in Settings first.")
        return try {
            val target = if (size > DIRECT_LIMIT) largeUploadUrl(apiKey) else "$BASE/files"
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", name.ifBlank { "file" }, streamBody(size, open))
                .build()
            val req = Request.Builder().url(target).header("x-apikey", apiKey).post(body).build()
            uploadClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.code == 401 -> Upload(error = "Invalid API key. Check it in Settings.")
                    resp.code == 429 -> Upload(error = RATE_TEXT)
                    resp.code == 413 -> Upload(error = "VirusTotal will not accept a file this large.")
                    !resp.isSuccessful -> Upload(error = "Upload failed (${resp.code}). ${shortErr(text)}")
                    else -> {
                        val id = JSONObject(text).optJSONObject("data")?.optString("id").orEmpty()
                        if (id.isEmpty()) Upload(error = "VirusTotal did not return an analysis id.")
                        else Upload(analysisId = id)
                    }
                }
            }
        } catch (e: IOException) {
            Upload(error = "No connection to VirusTotal. Check your internet and try again.")
        } catch (e: Exception) {
            Upload(error = e.message ?: "unexpected error")
        }
    }

    fun analysis(analysisId: String, apiKey: String): Verdict {
        if (apiKey.isBlank()) return Verdict(State.BAD_KEY, detail = "Add your VirusTotal API key in Settings first.")
        return try {
            val req = Request.Builder().url("$BASE/analyses/$analysisId").header("x-apikey", apiKey).get().build()
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    401 -> return Verdict(State.BAD_KEY, detail = "Invalid API key. Check it in Settings.")
                    429 -> return Verdict(State.RATE_LIMITED, detail = RATE_TEXT)
                }
                if (!resp.isSuccessful) {
                    return Verdict(State.ERROR, detail = "VirusTotal returned an error (${resp.code}).")
                }
                val attr = JSONObject(resp.body?.string().orEmpty())
                    .optJSONObject("data")?.optJSONObject("attributes")
                    ?: return Verdict(State.PENDING)
                if (attr.optString("status") != "completed") return Verdict(State.PENDING)
                val stats = attr.optJSONObject("stats") ?: return Verdict(State.PENDING)
                val mal = stats.optInt("malicious")
                val sus = stats.optInt("suspicious")
                val total = mal + sus + stats.optInt("harmless") + stats.optInt("undetected")
                val state = when {
                    mal > 0 -> State.MALICIOUS
                    sus > 0 -> State.SUSPICIOUS
                    else -> State.CLEAN
                }
                Verdict(state, mal, sus, total)
            }
        } catch (e: IOException) {
            Verdict(State.ERROR, detail = "No connection to VirusTotal.")
        } catch (e: Exception) {
            Verdict(State.ERROR, detail = e.message ?: "unexpected error")
        }
    }

    private fun largeUploadUrl(apiKey: String): String {
        val req = Request.Builder().url("$BASE/files/upload_url").header("x-apikey", apiKey).get().build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 429) throw IllegalStateException(RATE_TEXT)
            if (!resp.isSuccessful) {
                throw IllegalStateException("Could not start a large upload (${resp.code}). ${shortErr(text)}")
            }
            val url = JSONObject(text).optString("data")
            if (url.isEmpty()) throw IllegalStateException("Could not start a large upload.")
            return url
        }
    }

    private fun streamBody(size: Long, open: () -> InputStream): RequestBody = object : RequestBody() {
        override fun contentType() = "application/octet-stream".toMediaType()
        override fun contentLength(): Long = if (size > 0) size else -1L
        override fun writeTo(sink: BufferedSink) {
            open().use { sink.writeAll(it.source()) }
        }
    }

    fun describe(name: String, sha256: String, v: Verdict): String = buildString {
        append("File: ").append(name).append("\n")
        append("SHA-256: ").append(sha256).append("\n\n")
        when (v.state) {
            State.MALICIOUS -> {
                append("Verdict: DANGEROUS\n")
                append(v.malicious + v.suspicious).append(" of ").append(v.total)
                append(" engines flagged this file. Delete it and do not install it.")
            }
            State.SUSPICIOUS -> {
                append("Verdict: SUSPICIOUS\n")
                append(v.suspicious).append(" of ").append(v.total)
                append(" engines flagged this file. Treat it with caution.")
            }
            State.CLEAN -> {
                append("Verdict: CLEAN\n")
                append("No engines flagged this file out of ").append(v.total).append(".")
            }
            State.UNKNOWN -> {
                append("Verdict: UNKNOWN\n")
                append("VirusTotal has no record of this file. That is normal for files you made yourself, ")
                append("and worth a second look for anything you downloaded. You can send the file itself ")
                append("to VirusTotal to have it analysed, but only if you are willing for it to leave your phone.")
            }
            State.PENDING -> {
                append("Verdict: STILL ANALYSING\n")
                append("VirusTotal has the file and its engines have not finished. Scan it again in a minute.")
            }
            else -> {
                append("Verdict: NOT CHECKED\n").append(v.detail)
            }
        }
    }

    private fun analyzing(url: String): String =
        "Site: $url\n\nVirusTotal is analyzing this link now. Tap Scan again in about a minute to see the result."

    private fun submit(url: String, apiKey: String): String? {
        val req = Request.Builder()
            .url("$BASE/urls")
            .header("x-apikey", apiKey)
            .post(FormBody.Builder().add("url", url).build())
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 429) throw RateLimited()
            if (resp.code == 401) throw IllegalStateException("Invalid API key.")
            if (!resp.isSuccessful) throw IllegalStateException("Submit failed (${resp.code}). ${shortErr(body)}")
            return JSONObject(body).optJSONObject("data")?.optString("id").takeIf { !it.isNullOrEmpty() }
        }
    }

    private fun format(url: String, stats: JSONObject?): String {
        val mal = stats?.optInt("malicious") ?: 0
        val sus = stats?.optInt("suspicious") ?: 0
        val harm = stats?.optInt("harmless") ?: 0
        val undet = stats?.optInt("undetected") ?: 0
        val flagged = mal + sus
        val total = mal + sus + harm + undet
        val verdict = when {
            mal > 0 -> "DANGEROUS"
            sus > 0 -> "SUSPICIOUS"
            else -> "SAFE"
        }
        val summary = when {
            mal > 0 -> "$flagged of $total engines flagged this as malicious. Do not open it."
            sus > 0 -> "$flagged of $total engines flagged this as suspicious. Be careful."
            else -> "No engines flagged this. Looks clean."
        }
        return buildString {
            append("Site: ").append(url).append("\n\n")
            append("Verdict: ").append(verdict).append('\n')
            append(summary).append("\n\n")
            append("Malicious: ").append(mal).append('\n')
            append("Suspicious: ").append(sus).append('\n')
            append("Harmless: ").append(harm).append('\n')
            append("Undetected: ").append(undet)
        }
    }

    private const val RATE_TEXT = "Rate limited (4 per minute, 500 per day). Wait a minute and try again."

    private class RateLimited : Exception(RATE_TEXT)

    private fun shortErr(body: String): String = try {
        JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
    } catch (e: Exception) {
        ""
    }
}
