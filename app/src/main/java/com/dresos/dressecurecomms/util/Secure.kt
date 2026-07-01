/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.util

import android.os.Build
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

fun AppCompatActivity.applyScreenshotPolicy() {

    window.decorView.filterTouchesWhenObscured = true

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
