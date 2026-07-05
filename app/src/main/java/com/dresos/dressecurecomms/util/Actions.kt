/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.dresos.dressecurecomms.data.ContactsStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object Actions {
    fun copy(context: Context, label: String, text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun saveToContacts(context: Context, number: String) {
        val nameInput = EditText(context).apply { hint = "Name" }
        val numberInput = EditText(context).apply {
            setText(number)
            inputType = InputType.TYPE_CLASS_PHONE
        }
        val pad = (16 * context.resources.displayMetrics.density).toInt()
        val wrap = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(nameInput)
            addView(numberInput)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Save to contacts")
            .setView(wrap)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val num = numberInput.text.toString().trim()
                if (num.isNotEmpty()) {
                    ContactsStore.add(context, ContactsStore.Contact(name.ifEmpty { num }, num))
                    Toast.makeText(context, "Saved to contacts", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.applyScreenshotPolicy(context)
        dialog.show()
    }

    fun dial(context: Context, number: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        } catch (e: Exception) {
        }
    }
}
