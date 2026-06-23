/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.text.format.DateUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dresos.dressecurecomms.data.CallHistory
import com.dresos.dressecurecomms.databinding.ActivityCallsBinding
import com.dresos.dressecurecomms.ui.TwoLineAdapter
import com.dresos.dressecurecomms.util.applyScreenshotPolicy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class CallsActivity : AppCompatActivity() {
    private lateinit var b: ActivityCallsBinding
    private lateinit var adapter: TwoLineAdapter<CallHistory.Entry>
    private var pendingDeleteId: Long? = null
    private var pendingClear = false

    private val requestCall =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
            if (g) place() else snack("Call permission denied")
        }
    private val requestReadLog =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { reload() }
    private val requestWriteLog =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
            if (g) {
                when {
                    pendingClear -> { CallHistory.clear(this); pendingClear = false }
                    pendingDeleteId != null -> { CallHistory.delete(this, pendingDeleteId!!); pendingDeleteId = null }
                }
                reload()
            } else snack("Call log permission denied")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyScreenshotPolicy()
        b = ActivityCallsBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.title = getString(R.string.card_calls_title)
        b.toolbar.setNavigationIcon(R.drawable.ic_back)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.toolbar.menu.add(0, 1, 0, "Clear call history").setOnMenuItemClickListener { confirmClear(); true }

        adapter = TwoLineAdapter(this, emptyList()) { e ->
            e.number to "${CallHistory.typeLabel(e.type)} · ${DateUtils.getRelativeTimeSpanString(e.date)}"
        }
        b.list.adapter = adapter
        b.list.setOnItemClickListener { _, _, pos, _ -> b.number.setText(adapter.getItem(pos).number) }
        b.list.setOnItemLongClickListener { _, _, pos, _ -> entryActions(adapter.getItem(pos)); true }

        intent.getStringExtra("number")?.let { b.number.setText(it) }
        (if (intent?.data?.scheme == "tel") intent.data?.schemeSpecificPart else null)?.let { b.number.setText(it) }

        b.callBtn.setOnClickListener {
            if (b.number.text.toString().isBlank()) return@setOnClickListener
            if (hasCall()) place() else requestCall.launch(Manifest.permission.CALL_PHONE)
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED)
            reload()
        else
            requestReadLog.launch(Manifest.permission.READ_CALL_LOG)
    }

    private fun reload() { adapter.setItems(CallHistory.load(this)) }

    private fun entryActions(e: CallHistory.Entry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(e.number)
            .setItems(arrayOf("Call", "Delete")) { _, which ->
                when (which) {
                    0 -> { b.number.setText(e.number); if (hasCall()) place() else requestCall.launch(Manifest.permission.CALL_PHONE) }
                    1 -> deleteEntry(e.id)
                }
            }
            .show()
    }

    private fun deleteEntry(id: Long) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            CallHistory.delete(this, id); reload()
        } else {
            pendingDeleteId = id
            requestWriteLog.launch(Manifest.permission.WRITE_CALL_LOG)
        }
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear call history")
            .setMessage("Delete all call history?")
            .setPositiveButton("Delete") { _, _ ->
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                    CallHistory.clear(this); reload()
                } else {
                    pendingClear = true
                    requestWriteLog.launch(Manifest.permission.WRITE_CALL_LOG)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hasCall() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    private fun place() {
        val n = b.number.text.toString().trim()
        if (n.isEmpty()) return
        try {
            val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.placeCall(Uri.fromParts("tel", n, null), Bundle())
        } catch (e: SecurityException) {
            snack("Call permission denied")
        } catch (e: Exception) {
            snack("Could not place the call: ${e.message}")
        }
    }

    private fun snack(s: String) = Snackbar.make(b.root, s, Snackbar.LENGTH_LONG).show()
}
