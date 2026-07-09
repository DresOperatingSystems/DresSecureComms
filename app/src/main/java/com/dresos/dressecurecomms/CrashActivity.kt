/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dresos.dressecurecomms.util.CrashLog

class CrashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra("trace") ?: "No details available."
        val pad = (16 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            text = "DresSecureComms stopped"
            textSize = 20f
            setPadding(0, 0, 0, pad)
        }
        val restart = Button(this).apply {
            text = "Restart app"
            setOnClickListener {
                startActivity(
                    Intent(this@CrashActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            }
        }
        val share = Button(this).apply {
            text = "Share report"
            setOnClickListener { CrashLog.share(this@CrashActivity) }
        }
        val detail = TextView(this).apply {
            text = trace
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(0, pad, 0, 0)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(title); addView(restart); addView(share); addView(detail)
        }
        setContentView(ScrollView(this).apply { addView(content) })
    }
}
