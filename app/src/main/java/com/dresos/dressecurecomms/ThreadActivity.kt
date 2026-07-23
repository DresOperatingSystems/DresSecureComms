/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import com.dresos.dressecurecomms.util.Actions

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.os.Bundle
import android.telephony.SmsManager
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.dresos.dressecurecomms.util.applyScreenshotPolicy
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dresos.dressecurecomms.crypto.ContactKeys
import com.dresos.dressecurecomms.crypto.SmsCrypto
import com.dresos.dressecurecomms.util.Contacts
import com.dresos.dressecurecomms.data.ContactsStore
import com.dresos.dressecurecomms.data.SmsRepository
import com.dresos.dressecurecomms.data.SmsStore
import com.dresos.dressecurecomms.databinding.ActivityThreadBinding
import com.dresos.dressecurecomms.ui.MessageAdapter
import com.google.android.material.snackbar.Snackbar

class ThreadActivity : AppCompatActivity() {
    private lateinit var b: ActivityThreadBinding
    private lateinit var address: String
    private var threadId: Long = -1L
    private lateinit var adapter: MessageAdapter
    private var pendingText: String? = null
    private var pendingImage: Uri? = null

    private val requestSmsRead =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { reload() }
    private val requestSmsSend =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val t = pendingText
            if (granted && t != null) reallySend(t) else toast("SMS permission denied")
            pendingText = null
        }
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) { pendingImage = uri; updateAttachUi() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyScreenshotPolicy()
        b = ActivityThreadBinding.inflate(layoutInflater)
        setContentView(b.root)
        address = intent.getStringExtra("address").orEmpty()
        threadId = intent.getLongExtra("threadId", -1L)

        val recipients = splitRecipients(address)
        b.toolbar.title = if (recipients.size > 1) {
            "${recipients.size} recipients"
        } else {
            ContactsStore.load(this).firstOrNull { it.number == address }?.name ?: address
        }
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
        if (recipients.size == 1) {
            b.toolbar.menu.add(0, 2, 0, "Call").setOnMenuItemClickListener {
                Actions.dial(this, address); true
            }
            if (!Contacts.isSaved(this, address)) {
                b.toolbar.menu.add(0, 3, 0, "Save to contacts").setOnMenuItemClickListener {
                    Actions.saveToContacts(this, address)
                    b.toolbar.menu.removeItem(3)
                    true
                }
            }
            b.toolbar.menu.add(0, 5, 0, "Encryption key for this contact").setOnMenuItemClickListener {
                contactKeyDialog(); true
            }
            b.toolbar.menu.add(0, 4, 0, "Copy number").setOnMenuItemClickListener {
                Actions.copy(this, "number", address); toast(getString(R.string.number_copied)); true
            }
        }

        adapter = MessageAdapter(this, emptyList())
        b.list.adapter = adapter
        b.list.setOnItemLongClickListener { _, _, position, _ ->
            val body = adapter.getItem(position).body
            if (body.isNotBlank()) { Actions.copy(this, "message", body); toast(getString(R.string.message_copied)) }
            true
        }
        b.encrypt.isChecked = ContactKeys.keyFor(this, address).isNotBlank()

        b.attachBtn.setOnClickListener { pickImage.launch("image/*") }
        b.attachPreview.setOnClickListener { pendingImage = null; updateAttachUi() }
        updateAttachUi()

        b.sendBtn.setOnClickListener {
            val text = b.input.text.toString()
            if (text.isBlank() && pendingImage == null) return@setOnClickListener
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

    private fun contactKeyDialog() {
        val current = ContactKeys.forAddress(this, address).orEmpty()
        val input = android.widget.EditText(this).apply {
            setText(current)
            hint = "Leave blank to use the shared key from Settings"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val wrap = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(input)
        }
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Encryption key for $address")
            .setMessage("Both of you must type the same code. It is used only for this contact.")
            .setView(wrap)
            .setPositiveButton("Save") { _, _ ->
                ContactKeys.set(this, address, input.text.toString().trim())
                b.encrypt.isChecked = ContactKeys.keyFor(this, address).isNotBlank()
                toast(
                    if (ContactKeys.has(this, address)) "Key saved for this contact."
                    else "Using the shared key from Settings."
                )
                reload()
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.applyScreenshotPolicy(this)
        dialog.show()
    }

    private fun splitRecipients(s: String): List<String> =
        s.split(Regex("[,;]")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun updateAttachUi() {
        val uri = pendingImage
        if (uri != null) {
            b.attachPreview.visibility = View.VISIBLE
            b.attachPreview.setImageURI(uri)
        } else {
            b.attachPreview.visibility = View.GONE
            b.attachPreview.setImageDrawable(null)
        }
    }

    private fun reload() {
        val key = ContactKeys.keyFor(this, address)
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                if (threadId <= 0) threadId = SmsRepository.threadIdForAddress(this@ThreadActivity, address)
                SmsRepository.threadById(this@ThreadActivity, threadId, address, key)
            }
            adapter.setItems(items)
            b.list.setSelection(adapter.count - 1)
        }
    }

    private fun reallySend(text: String) {
        try {
            val key = ContactKeys.keyFor(this, address)
            val encryptOn = b.encrypt.isChecked
            if (encryptOn && key.isBlank()) {
                toast("Set a key for this contact from the menu, or a shared key in Settings.")
                return
            }

            val recipients = splitRecipients(address)
            if (recipients.isEmpty()) { toast("No recipient."); return }
            val hasImage = pendingImage != null
            val isGroup = recipients.size > 1

            if (isGroup || hasImage) {
                sendMms(recipients, text, pendingImage, encryptOn)
            } else {
                val payload = if (encryptOn) SmsCrypto.encrypt(text, key) else text
                val sm = smsManager()
                sm.sendMultipartTextMessage(address, null, sm.divideMessage(payload), null, null)
                val now = System.currentTimeMillis()
                SmsStore.add(this, address, text, now)
                if (SmsRepository.isDefault(this)) {
                    try {
                        if (threadId <= 0) threadId = SmsRepository.threadIdForAddress(this, address)
                        val values = android.content.ContentValues().apply {
                            put(Telephony.Sms.ADDRESS, address)
                            put(Telephony.Sms.BODY, payload)
                            put(Telephony.Sms.DATE, now)
                            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                            if (threadId > 0) put(Telephony.Sms.THREAD_ID, threadId)
                        }
                        contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
                    } catch (_: Exception) {
                    }
                }
                b.input.setText("")
                reload()
            }
        } catch (e: Exception) {
            toast("Send failed: ${e.message}")
        }
    }

    private fun sendMms(recipients: List<String>, text: String, image: Uri?, encryptOn: Boolean) {
        toast(if (encryptOn) "Sending picture unencrypted..." else "Sending MMS...")
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    MmsSender.send(this@ThreadActivity, recipients, text.ifBlank { null }, image, threadId)
                    true
                } catch (e: Exception) {
                    e.printStackTrace(); false
                }
            }
            if (ok) {
                pendingImage = null
                updateAttachUi()
                b.input.setText("")
                reload()
            } else {
                toast("MMS failed. Be the default SMS app, enable mobile data, and try again.")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(SmsManager::class.java)
        else SmsManager.getDefault()

    private fun toast(s: String) = Snackbar.make(b.root, s, Snackbar.LENGTH_LONG).show()
}
