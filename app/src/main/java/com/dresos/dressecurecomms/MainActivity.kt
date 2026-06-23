/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.dresos.dressecurecomms.databinding.ActivityMainBinding
import com.dresos.dressecurecomms.databinding.CardItemBinding
import com.dresos.dressecurecomms.media.MetadataWiper
import com.dresos.dressecurecomms.net.VirusTotalClient
import com.dresos.dressecurecomms.util.applyScreenshotPolicy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private var cleanedImage: File? = null

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) onImagePicked(uri)
        }

    private val saveImage =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { dest ->
            val src = cleanedImage
            if (dest != null && src != null) {
                runCatching {
                    contentResolver.openOutputStream(dest)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                }
                Snackbar.make(binding.root, "Saved cleaned image", Snackbar.LENGTH_LONG).show()
            }
            cleanedImage = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyScreenshotPolicy()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // The notification prompt is requested AFTER unlock (in wireUi). Launching a system
        // permission dialog here, before the biometric prompt, made Android cancel the prompt
        // on first launch and the app closed.
        if (prefs.getBoolean("app_lock", true)) {
            binding.root.visibility = View.INVISIBLE
            lockThenShow()
        } else {
            wireUi()
        }
    }

    private fun maybeRequestNotif() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun lockThenShow() {
        val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            wireUi(); return
        }
        try {
            val prompt = BiometricPrompt(
                this, ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = wireUi()
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // Close only when the user themselves dismisses the unlock prompt.
                        // Any other error opens the app rather than leaving it stuck or closing.
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) finish() else wireUi()
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock DresSecureComms")
                .setAllowedAuthenticators(authenticators)
                .build()
            prompt.authenticate(info)
        } catch (e: Exception) {
            wireUi()
        }
    }

    private fun wireUi() {
        binding.root.visibility = View.VISIBLE
        card(binding.cardMessages, R.drawable.ic_sms, R.string.card_messages_title, R.string.card_messages_sub) {
            startActivity(Intent(this, MessagesActivity::class.java))
        }
        card(binding.cardCalls, R.drawable.ic_call, R.string.card_calls_title, R.string.card_calls_sub) {
            startActivity(Intent(this, CallsActivity::class.java))
        }
        card(binding.cardContacts, R.drawable.ic_contacts, R.string.card_contacts_title, R.string.card_contacts_sub) {
            startActivity(Intent(this, ContactsActivity::class.java))
        }
        card(binding.cardScan, R.drawable.ic_scan, R.string.card_scan_title, R.string.card_scan_sub) { scanUrlDialog() }
        card(binding.cardMeta, R.drawable.ic_meta, R.string.card_meta_title, R.string.card_meta_sub) {
            pickImage.launch(arrayOf("image/*"))
        }
        card(binding.cardSettings, R.drawable.ic_settings, R.string.card_settings_title, R.string.card_settings_sub) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        card(binding.cardAbout, R.drawable.ic_info, R.string.card_about_title, R.string.card_about_sub) {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        maybeRequestNotif()
    }

    private fun card(
        b: CardItemBinding, @DrawableRes icon: Int, @StringRes title: Int, @StringRes sub: Int, onClick: () -> Unit
    ) {
        b.ic.setImageResource(icon)
        b.cardTitle.setText(title)
        b.cardSub.setText(sub)
        b.root.setOnClickListener { onClick() }
    }

    // ---- Threat scan ----
    private fun scanUrlDialog() {
        val key = prefs.getString("virustotal_api_key", "").orEmpty()
        if (key.isBlank()) {
            Snackbar.make(binding.root, "Add your VirusTotal API key in Settings first.", Snackbar.LENGTH_LONG).show()
            return
        }
        val input = EditText(this).apply { hint = "https://example.com" }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val wrap = LinearLayout(this).apply { setPadding(pad, pad, pad, 0); addView(input) }
        MaterialAlertDialogBuilder(this)
            .setTitle("Scan a URL")
            .setView(wrap)
            .setPositiveButton("Scan") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) runScan(url, key)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runScan(url: String, key: String) {
        val progress: AlertDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Scanning")
            .setMessage("Checking with VirusTotal...")
            .setCancelable(false)
            .create()
        progress.applyScreenshotPolicy(this)
        progress.show()
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { VirusTotalClient.scanUrl(url, key) }
            }.getOrElse { "Scan failed: ${it.message ?: "unexpected error"}" }
            progress.dismiss()
            if (!isFinishing && !isDestroyed) {
                val dialog = MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Scan result")
                    .setMessage(result)
                    .setPositiveButton("OK", null)
                    .create()
                dialog.applyScreenshotPolicy(this@MainActivity)
                dialog.show()
            }
        }
    }

    // ---- Metadata wipe ----
    private fun onImagePicked(uri: Uri) {
        try {
            cleanedImage = MetadataWiper.wipeToCache(this, uri)
            saveImage.launch("clean_${System.currentTimeMillis()}.jpg")
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Could not process image: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }
}
