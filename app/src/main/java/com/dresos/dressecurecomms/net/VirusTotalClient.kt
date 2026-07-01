/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.net

import android.util.Base64
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object VirusTotalClient {
    private const val BASE = "https://www.virustotal.com/api/v3"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun scanUrl(url: String, apiKey: String): String {
        if (apiKey.isBlank()) return "Add your VirusTotal API key in Settings first."
        if (url.isBlank()) return "Enter a URL to scan."
        return try {
            lookupExisting(url, apiKey) ?: run {
                val analysisId = submit(url, apiKey) ?: return "Could not start the scan."
                poll(analysisId, apiKey, url)
                    ?: "Site: $url\n\nVirusTotal is analyzing this URL for the first time. Try again in a minute."
            }
        } catch (e: RateLimited) {
            e.message ?: "Rate limited. Wait a minute and try again."
        } catch (e: IOException) {
            "No connection to VirusTotal. Check your internet and try again."
        } catch (e: Exception) {
            "Scan failed: ${e.message ?: "unexpected error"}"
        }
    }

    private fun lookupExisting(url: String, apiKey: String): String? {
        val id = Base64.encodeToString(
            url.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        val req = Request.Builder().url("$BASE/urls/$id").header("x-apikey", apiKey).get().build()
        client.newCall(req).execute().use { resp ->
            when (resp.code) {
                404 -> return null
                429 -> throw RateLimited()
                401 -> throw IllegalStateException("Invalid API key.")
            }
            if (!resp.isSuccessful) return null
            val attr = JSONObject(resp.body?.string().orEmpty())
                .optJSONObject("data")?.optJSONObject("attributes")
            val stats = attr?.optJSONObject("last_analysis_stats") ?: return null
            if (stats.length() == 0) return null
            return format(url, stats)
        }
    }

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

    private fun poll(analysisId: String, apiKey: String, url: String): String? {
        repeat(15) {
            val req = Request.Builder()
                .url("$BASE/analyses/$analysisId").header("x-apikey", apiKey).get().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.code == 429) throw RateLimited()
                if (!resp.isSuccessful) throw IllegalStateException("Report failed (${resp.code}). ${shortErr(body)}")
                val attr = JSONObject(body).optJSONObject("data")?.optJSONObject("attributes")
                if (attr?.optString("status") == "completed") {
                    return format(url, attr.optJSONObject("stats"))
                }
            }
            Thread.sleep(5000)
        }
        return null
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

    private class RateLimited : Exception("Rate limited (4 per minute, 500 per day). Wait a minute and try again.")

    private fun shortErr(body: String): String = try {
        JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
    } catch (e: Exception) {
        ""
    }
}
