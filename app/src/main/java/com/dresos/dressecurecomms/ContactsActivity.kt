/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.dresos.dressecurecomms.data.ContactsStore
import com.dresos.dressecurecomms.databinding.ActivityContactsBinding
import com.dresos.dressecurecomms.ui.TwoLineAdapter
import com.dresos.dressecurecomms.util.applyScreenshotPolicy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ContactsActivity : AppCompatActivity() {
    private lateinit var b: ActivityContactsBinding
    private lateinit var adapter: TwoLineAdapter<ContactsStore.Contact>

    private val requestReadContacts =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) importDevice()
            else Snackbar.make(b.root, "Contacts permission denied", Snackbar.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyScreenshotPolicy()
        b = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.title = getString(R.string.card_contacts_title)
        b.toolbar.setNavigationIcon(R.drawable.ic_back)
        b.toolbar.setNavigationOnClickListener { finish() }

        adapter = TwoLineAdapter(this, emptyList()) { c ->
            c.name to if (c.email.isNotEmpty()) "${c.number}  ·  ${c.email}" else c.number
        }
        b.list.adapter = adapter
        b.list.emptyView = b.empty
        b.list.setOnItemClickListener { _, _, pos, _ -> contactActions(adapter.getItem(pos)) }
        b.fab.setOnClickListener { addMenu() }
    }

    override fun onResume() { super.onResume(); refresh() }
    private fun refresh() = adapter.setItems(ContactsStore.load(this))

    private fun addMenu() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_contact)
            .setItems(arrayOf(getString(R.string.add_contact), getString(R.string.import_device))) { _, which ->
                if (which == 0) showForm(null)
                else requestReadContacts.launch(Manifest.permission.READ_CONTACTS)
            }
            .show()
    }

    /** Add (existing == null) or edit a contact, with name, number and email. */
    private fun showForm(existing: ContactsStore.Contact?) {
        val name = EditText(this).apply {
            hint = getString(R.string.name_hint); setText(existing?.name ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val number = EditText(this).apply {
            hint = getString(R.string.number_hint); setText(existing?.number ?: "")
            inputType = InputType.TYPE_CLASS_PHONE
        }
        val email = EditText(this).apply {
            hint = getString(R.string.email_hint); setText(existing?.email ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, 0)
            addView(name); addView(number); addView(email)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.add_contact else R.string.edit_contact)
            .setView(wrap)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val n = name.text.toString().trim()
                val p = number.text.toString().trim()
                val e = email.text.toString().trim()
                if (n.isNotEmpty() && p.isNotEmpty()) {
                    val updated = ContactsStore.Contact(n, p, e)
                    if (existing == null) ContactsStore.add(this, updated)
                    else ContactsStore.update(this, existing, updated)
                    refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importDevice() {
        val found = ArrayList<ContactsStore.Contact>()
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, cols, null, null, null)?.use { c ->
            val ni = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val pi = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                val n = c.getString(ni) ?: continue
                val p = c.getString(pi) ?: continue
                found.add(ContactsStore.Contact(n, p))
            }
        }
        ContactsStore.addAll(this, found)
        refresh()
        Snackbar.make(b.root, "Imported ${found.size} contacts", Snackbar.LENGTH_LONG).show()
    }

    private fun contactActions(c: ContactsStore.Contact) {
        val options = if (c.email.isNotEmpty())
            arrayOf("Message", "Call", "Email", "Edit", "Delete")
        else
            arrayOf("Message", "Call", "Edit", "Delete")
        MaterialAlertDialogBuilder(this)
            .setTitle(c.name)
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Message" -> startActivity(Intent(this, ThreadActivity::class.java).putExtra("address", c.number))
                    "Call" -> startActivity(Intent(this, CallsActivity::class.java).putExtra("number", c.number))
                    "Email" -> startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:${c.email}")),
                            "Email"
                        )
                    )
                    "Edit" -> showForm(c)
                    "Delete" -> { ContactsStore.delete(this, c); refresh() }
                }
            }
            .show()
    }
}
