/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ComposeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ssp = intent?.data?.schemeSpecificPart
        val address = ssp?.substringBefore("?")?.replace(" ", "")?.trim().orEmpty()
        val next = if (address.isNotEmpty())
            Intent(this, ThreadActivity::class.java).putExtra("address", address)
        else
            Intent(this, MessagesActivity::class.java)
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(next)
        finish()
    }
}
