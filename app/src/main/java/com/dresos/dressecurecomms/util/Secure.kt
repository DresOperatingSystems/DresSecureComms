/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.util

import android.os.Build
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

/**
 * Applies the user's screenshot policy to this activity's window. When "Block screenshots"
 * is on (default), FLAG_SECURE prevents screenshots, screen recording, and previews in the
 * recents list. Call this at the very top of onCreate, before setContentView.
 */
fun AppCompatActivity.applyScreenshotPolicy() {
    val block = PreferenceManager.getDefaultSharedPreferences(this)
        .getBoolean("block_screenshots", true)
    if (block) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(true)
    }
}

/**
 * Makes a dialog respect the screenshot policy. A dialog is a separate window, so the
 * activity's FLAG_SECURE does not cover it; this sets the flag on the dialog's own window.
 * Call before show().
 */
fun android.app.Dialog.applyScreenshotPolicy(context: android.content.Context) {
    val block = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean("block_screenshots", true)
    if (!block) return
    setOnShowListener {
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }
}
