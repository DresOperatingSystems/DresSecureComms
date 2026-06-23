/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dresos.dressecurecomms.util.applyScreenshotPolicy
import com.dresos.dressecurecomms.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyScreenshotPolicy()
        val b = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tvVersion.text = "Version " + runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrDefault("1.0.0")

        b.tvManual.text = runCatching {
            assets.open("man_page.txt").bufferedReader().use { it.readText() }
        }.getOrDefault("")

        b.btnOs.setOnClickListener { openUrl(getString(R.string.os_url)) }
        b.btnWebsite.setOnClickListener { openUrl(getString(R.string.website_url)) }
        b.btnDonate.setOnClickListener { openUrl(getString(R.string.kofi_url)) }
        b.btnSource.setOnClickListener { openUrl(getString(R.string.source_url)) }
        b.btnEmail.setOnClickListener {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + getString(R.string.contact_email))),
                    "Email"
                )
            )
        }
    }

    private fun openUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
