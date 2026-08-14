/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
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
import com.dresos.dressecurecomms.data.VCard
import com.dresos.dressecurecomms.databinding.ActivityContactsBinding
import com.dresos.dressecurecomms.ui.TwoLineAdapter
import androidx.core.widget.doAfterTextChanged
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

    private val pickVcf =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importVcf(uri)
        }

    private val saveVcf =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/vcard")) { uri ->
            if (uri != null) exportVcf(uri)
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
            Triple(c.name, if (c.email.isNotEmpty()) "${c.number}  ·  ${c.email}" else c.number, "")
        }
        b.list.adapter = adapter
        b.list.emptyView = b.empty
        b.list.setOnItemClickListener { _, _, pos, _ -> contactActions(adapter.getItem(pos)) }
        b.fab.setOnClickListener { addMenu() }
        b.search.doAfterTextChanged { applyFilter() }
    }

    override fun onResume() { super.onResume(); refresh() }

    private var all: List<ContactsStore.Contact> = emptyList()

    private fun refresh() {
        all = ContactsStore.load(this)
        applyFilter()
    }

    private fun applyFilter() {
        val q = b.search.text?.toString()?.trim()?.lowercase().orEmpty()
        adapter.setItems(
            if (q.isEmpty()) all
            else all.filter { it.name.lowercase().contains(q) || it.number.lowercase().contains(q) }
        )
    }

    private fun addMenu() {
        val options = arrayOf(
            getString(R.string.add_contact),
            getString(R.string.import_device),
            getString(R.string.import_file),
            getString(R.string.export_file)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_contact)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showForm(null)
                    1 -> requestReadContacts.launch(Manifest.permission.READ_CONTACTS)
                    2 -> pickVcf.launch(arrayOf("text/vcard", "text/x-vcard", "text/directory", "*/*"))
                    3 -> saveVcf.launch("contacts.vcf")
                }
            }
            .show()
    }

    private fun importVcf(uri: android.net.Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
        if (text == null) {
            Snackbar.make(b.root, getString(R.string.import_failed), Snackbar.LENGTH_LONG).show()
            return
        }
        val found = VCard.parse(text)
        if (found.isEmpty()) {
            Snackbar.make(b.root, getString(R.string.no_contacts_in_file), Snackbar.LENGTH_LONG).show()
            return
        }
        ContactsStore.addAll(this, found)
        refresh()
        Snackbar.make(b.root, getString(R.string.imported_file, found.size), Snackbar.LENGTH_LONG).show()
    }

    private fun exportVcf(uri: android.net.Uri) {
        val contacts = ContactsStore.load(this)
        if (contacts.isEmpty()) {
            Snackbar.make(b.root, getString(R.string.no_contacts_to_export), Snackbar.LENGTH_LONG).show()
            return
        }
        val ok = try {
            contentResolver.openOutputStream(uri)?.use { it.write(VCard.write(contacts).toByteArray()) }
            true
        } catch (e: Exception) {
            false
        }
        val msg = if (ok) getString(R.string.exported_file, contacts.size) else getString(R.string.export_failed)
        Snackbar.make(b.root, msg, Snackbar.LENGTH_LONG).show()
    }

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
