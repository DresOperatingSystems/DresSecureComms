/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.util

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFmt {
    fun rel(time: Long): String =
        if (time <= 0L) "" else DateUtils.getRelativeTimeSpanString(time).toString()

    fun stamp(time: Long): String =
        if (time <= 0L) "" else SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(time))
}
