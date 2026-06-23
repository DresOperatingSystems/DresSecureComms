/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.os.Bundle
import android.telephony.SmsManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.dresos.dressecurecomms.util.applyScreenshotPolicy
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.preference.PreferenceManager
import com.dresos.dressecurecomms.crypto.SmsCrypto
import com.dresos.dressecurecomms.data.ContactsStore
import com.dresos.dressecurecomms.data.SmsRepository
import com.dresos.dressecurecomms.data.SmsStore
import com.dresos.dressecurecomms.databinding.ActivityThreadBinding
import com.dresos.dressecurecomms.ui.MessageAdapter
import com.google.android.material.snackbar.Snackbar

class ThreadActivity : AppCompatActivity() {
    private lateinit var b: ActivityThreadBinding
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private lateinit var address: String
    private var threadId: Long = -1L
    private lateinit var adapter: MessageAdapter
    private var pendingText: String? = null

    private val requestSmsRead =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { reload() }
    private val requestSmsSend =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val t = pendingText
            if (granted && t != null) reallySend(t) else toast("SMS permission denied")
            pendingText = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyScreenshotPolicy()
        b = ActivityThreadBinding.inflate(layoutInflater)
        setContentView(b.root)
        address = intent.getStringExtra("address").orEmpty()
        threadId = intent.getLongExtra("threadId", -1L)

        b.toolbar.title = ContactsStore.load(this).firstOrNull { it.number == address }?.name ?: address
        b.toolbar.setNavigationIcon(R.drawable.ic_back)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.toolbar.menu.add(0, 1, 0, "Delete conversation").setOnMenuItemClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Delete conversation")
                .setMessage("Delete all messages with $address?")
                .setPositiveButton("Delete") { _, _ ->
                    val result = SmsRepository.deleteThread(this, threadId, address)
                    val msg = when {
                        !result.isDefault ->
                            "Set DresSecureComms as the default SMS app to delete texts. Settings, Apps, Default apps, SMS app."
                        result.removed > 0 -> "Conversation deleted."
                        else -> "Conversation cleared."
                    }
                    android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        adapter = MessageAdapter(this, emptyList())
        b.list.adapter = adapter
        b.encrypt.isChecked = prefs.getString("sms_shared_key", "").orEmpty().isNotBlank()

        b.sendBtn.setOnClickListener {
            val text = b.input.text.toString()
            if (text.isBlank()) return@setOnClickListener
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                reallySend(text)
            } else {
                pendingText = text
                requestSmsSend.launch(Manifest.permission.SEND_SMS)
            }
        }

        requestSmsRead.launch(Manifest.permission.READ_SMS)
    }

    override fun onResume() { super.onResume(); reload() }

    private fun reload() {
        val key = prefs.getString("sms_shared_key", "").orEmpty()
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                if (threadId <= 0) threadId = SmsRepository.threadIdForAddress(this@ThreadActivity, address)
                SmsRepository.threadById(this@ThreadActivity, threadId, key)
            }
            adapter.setItems(items)
            b.list.setSelection(adapter.count - 1)
        }
    }

    private fun reallySend(text: String) {
        try {
            val key = prefs.getString("sms_shared_key", "").orEmpty()
            val payload = if (b.encrypt.isChecked) {
                if (key.isBlank()) { toast("Set a shared SMS key in Settings first."); return }
                SmsCrypto.encrypt(text, key)
            } else text
            val sm = smsManager()
            sm.sendMultipartTextMessage(address, null, sm.divideMessage(payload), null, null)
            SmsStore.add(this, address, text, System.currentTimeMillis())
            // As the default SMS app, also record the sent message in the system provider.
            if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                try {
                    val values = android.content.ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, address)
                        put(Telephony.Sms.BODY, payload)
                        put(Telephony.Sms.DATE, System.currentTimeMillis())
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    }
                    contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
                } catch (_: Exception) {
                }
            }
            b.input.setText("")
            reload()
        } catch (e: Exception) {
            toast("Send failed: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(SmsManager::class.java)
        else SmsManager.getDefault()

    private fun toast(s: String) = Snackbar.make(b.root, s, Snackbar.LENGTH_LONG).show()
}
