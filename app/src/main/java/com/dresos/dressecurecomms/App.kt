/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.WindowManager
import androidx.preference.PreferenceManager
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import com.dresos.dressecurecomms.util.CrashLog
import kotlin.system.exitProcess

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        forceSystemMms()

        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                val block = PreferenceManager.getDefaultSharedPreferences(activity)
                    .getBoolean("block_screenshots", true)
                if (block) activity.window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun forceSystemMms() {
        Transaction.settings = Settings().apply { useSystemSending = true }
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean("system_mms_sending", true)
            .apply()
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val trace = Log.getStackTraceString(error)
            CrashLog.append(this, trace)
            if (fromMmsLibrary(error)) {
                Log.e("App", "swallowed MMS library failure", error)
                if (thread != Looper.getMainLooper().thread) {
                    return@setDefaultUncaughtExceptionHandler
                }
                Process.killProcess(Process.myPid())
                exitProcess(1)
            }
            try {
                startActivity(
                    Intent(this, CrashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra("trace", trace)
                )
            } catch (_: Throwable) {
                previous?.uncaughtException(thread, error)
            }
            Process.killProcess(Process.myPid())
            exitProcess(1)
        }
    }

    private fun fromMmsLibrary(error: Throwable): Boolean {
        var t: Throwable? = error
        while (t != null) {
            if (t.stackTrace.any {
                    it.className.startsWith("com.klinker.android") ||
                        it.className.startsWith("com.android.mms") ||
                        it.className.startsWith("com.google.android.mms")
                }) {
                return true
            }
            t = t.cause
        }
        return false
    }
}
