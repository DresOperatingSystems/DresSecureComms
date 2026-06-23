/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Placeholder so the app qualifies as a default SMS app. MMS is not handled yet. */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { /* no-op for now */ }
}
