/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dresos.dressecurecomms.util.applyScreenshotPolicy
import androidx.preference.PreferenceManager
import com.dresos.dressecurecomms.data.ContactsStore
import com.dresos.dressecurecomms.data.SmsRepository
import com.dresos.dressecurecomms.databinding.ActivityMessagesBinding
import com.dresos.dressecurecomms.ui.TwoLineAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MessagesActivity : AppCompatActivity() {
    private lateinit var b: ActivityMessagesBinding
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private lateinit var adapter: TwoLineAdapter<SmsRepository.Conversation>

    private val requestSmsRead =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { reload() }
    private val requestSmsRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { reload() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyScreenshotPolicy()
        b = ActivityMessagesBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.title = getString(R.string.card_messages_title)
        b.toolbar.setNavigationIcon(R.drawable.ic_back)
        b.toolbar.setNavigationOnClickListener { finish() }

        adapter = TwoLineAdapter(this, emptyList()) { c -> nameFor(c.address) to c.snippet }
        b.list.adapter = adapter
        b.list.emptyView = b.empty
        b.list.setOnItemClickListener { _, _, pos, _ ->
            val c = adapter.getItem(pos)
            openThread(c.address, c.threadId)
        }
        b.list.setOnItemLongClickListener { _, _, pos, _ ->
            confirmDelete(adapter.getItem(pos)); true
        }
        b.fab.setOnClickListener { newMessage() }

        requestSmsRead.launch(Manifest.permission.READ_SMS)
    }

    override fun onResume() { super.onResume(); reload(); promptDefaultIfNeeded() }

    private var nameByNumber: Map<String, String> = emptyMap()

    private fun reload() {
        val key = prefs.getString("sms_shared_key", "").orEmpty()
        lifecycleScope.launch {
            val (items, names) = withContext(Dispatchers.IO) {
                val convs = SmsRepository.conversations(this@MessagesActivity, key)
                val map = ContactsStore.load(this@MessagesActivity).associate { it.number to it.name }
                convs to map
            }
            nameByNumber = names
            adapter.setItems(items)
        }
    }

    private fun nameFor(address: String): String = nameByNumber[address] ?: address

    private fun newMessage() {
        val input = EditText(this).apply { hint = getString(R.string.recipient_hint); inputType =
            android.text.InputType.TYPE_CLASS_PHONE }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val wrap = android.widget.LinearLayout(this).apply { setPadding(pad, pad, pad, 0); addView(input) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_message)
            .setView(wrap)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) openThread(n)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(c: SmsRepository.Conversation) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete conversation")
            .setMessage("Delete all messages with ${c.address}?")
            .setPositiveButton("Delete") { _, _ ->
                val result = SmsRepository.deleteThread(this, c.threadId, c.address)
                reload()
                val msg = when {
                    !result.isDefault ->
                        "Set DresSecureComms as the default SMS app to delete texts. Settings, Apps, Default apps, SMS app."
                    result.removed > 0 -> "Conversation deleted."
                    else -> "Conversation cleared."
                }
                com.google.android.material.snackbar.Snackbar.make(
                    b.root, msg, com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openThread(address: String, threadId: Long = -1L) {
        startActivity(
            Intent(this, ThreadActivity::class.java)
                .putExtra("address", address)
                .putExtra("threadId", threadId)
        )
    }

    private var defaultBanner: com.google.android.material.snackbar.Snackbar? = null

    private fun promptDefaultIfNeeded() {
        if (SmsRepository.isDefault(this)) {
            defaultBanner?.dismiss()
            defaultBanner = null
            return
        }
        if (defaultBanner?.isShown == true) return
        defaultBanner = com.google.android.material.snackbar.Snackbar.make(
            b.root,
            "DresSecureComms is not your default SMS app. Set it to send, receive, and delete texts.",
            com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
        ).setAction("Set default") { launchSmsRole() }.also { it.show() }
    }

    private fun launchSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(android.app.role.RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
                requestSmsRole.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS))
                return
            }
        }
        requestSmsRole.launch(
            Intent(android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        )
    }
}
