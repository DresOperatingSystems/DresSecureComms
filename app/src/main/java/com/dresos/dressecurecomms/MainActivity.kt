/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import com.dresos.dressecurecomms.util.SecureKeys

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
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
import com.dresos.dressecurecomms.crypto.AppLockManager
import com.dresos.dressecurecomms.databinding.ActivityMainBinding
import com.dresos.dressecurecomms.databinding.CardItemBinding
import com.dresos.dressecurecomms.media.MetadataWiper
import com.dresos.dressecurecomms.net.VirusTotalClient
import com.dresos.dressecurecomms.scan.FileScanner
import com.dresos.dressecurecomms.util.applyScreenshotPolicy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private data class Scanned(
        val name: String,
        val sha: String,
        val text: String,
        val state: VirusTotalClient.State,
        val size: Long
    )

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private var cleanedImage: File? = null

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) onImagePicked(uri)
        }

    private val pickAnyFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scanFile(uri)
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
        if (prefs.getBoolean("app_lock", true)) {
            promptUnlock()
        } else {
            wireUi()
        }
    }

    private fun promptUnlock() {
        val bm = BiometricManager.from(this)
        when {
            bm.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS -> cryptoUnlock()
            bm.canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS -> credentialUnlock()
            else -> wireUi()
        }
    }

    private fun cryptoUnlock() {
        val enrolling = !AppLockManager.isEnrolled(this)
        val cipher = try {
            if (enrolling) AppLockManager.newEncryptCipher() else AppLockManager.newDecryptCipher(this)
        } catch (e: KeyPermanentlyInvalidatedException) {
            AppLockManager.reset(this)
            try { AppLockManager.newEncryptCipher() } catch (e2: Exception) { credentialUnlock(); return }
        } catch (e: Exception) {
            AppLockManager.reset(this)
            try { AppLockManager.newEncryptCipher() } catch (e2: Exception) { credentialUnlock(); return }
        }

        val reEnrolling = enrolling || !AppLockManager.isEnrolled(this)

        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val c = result.cryptoObject?.cipher ?: return denyUnlock()
                    val ok = try {
                        if (reEnrolling) { AppLockManager.finishEnroll(this@MainActivity, c); true }
                        else AppLockManager.verifyUnlock(this@MainActivity, c)
                    } catch (e: Exception) { false }
                    if (ok) wireUi() else denyUnlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) finish() else denyUnlock()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock DresSecureComms")
            .setSubtitle("Authenticate to decrypt your vault")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun credentialUnlock() {

        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = wireUi()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = finish()
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock DresSecureComms")
            .setAllowedAuthenticators(DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    private fun denyUnlock() {
        Toast.makeText(this, "Unlock failed", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun maybeRequestNotif() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun wireUi() {
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
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
        card(binding.cardFileScan, R.drawable.ic_scan, R.string.card_filescan_title, R.string.card_filescan_sub) { fileScanDialog() }
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

    private fun fileScanDialog() {
        if (SecureKeys.vtKey(this).isBlank()) {
            Snackbar.make(binding.root, "Add your VirusTotal API key in Settings first.", Snackbar.LENGTH_LONG).show()
            return
        }
        val options = arrayOf(
            getString(R.string.scan_a_file),
            getString(R.string.scan_one_app),
            getString(R.string.scan_all_apps)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.card_filescan_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> try {
                        pickAnyFile.launch(arrayOf("*/*"))
                    } catch (e: Exception) {
                        Snackbar.make(binding.root, "No file picker available.", Snackbar.LENGTH_LONG).show()
                    }
                    1 -> singleAppPrompt()
                    2 -> appScanPrompt()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun scanFile(uri: Uri) {
        val key = SecureKeys.vtKey(this)
        if (key.isBlank()) return
        val progress = busy(getString(R.string.scan_busy_file))
        lifecycleScope.launch {
            val scanned = withContext(Dispatchers.IO) {
                try {
                    val name = FileScanner.displayName(this@MainActivity, uri)
                    val sha = FileScanner.sha256(this@MainActivity, uri)
                    val verdict = VirusTotalClient.fileVerdict(sha, key)
                    Scanned(
                        name, sha, VirusTotalClient.describe(name, sha, verdict), verdict.state,
                        FileScanner.sizeOf(this@MainActivity, uri)
                    )
                } catch (e: Exception) {
                    Scanned(
                        "", "", "Could not read that file: ${e.message ?: "unexpected error"}",
                        VirusTotalClient.State.ERROR, -1L
                    )
                }
            }
            progress.dismiss()
            if (isFinishing || isDestroyed) return@launch
            showScan(scanned) {
                contentResolver.openInputStream(uri) ?: throw IllegalStateException("Could not open that file.")
            }
        }
    }

    private fun singleAppPrompt() {
        if (SecureKeys.vtKey(this).isBlank()) return
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { FileScanner.installedApps(this@MainActivity) }
            if (apps.isEmpty()) {
                Snackbar.make(binding.root, "No installed apps found to scan.", Snackbar.LENGTH_LONG).show()
                return@launch
            }
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.scan_pick_app)
                .setItems(apps.map { it.label }.toTypedArray()) { _, which -> scanApp(apps[which]) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun scanApp(target: FileScanner.Target) {
        val key = SecureKeys.vtKey(this)
        if (key.isBlank()) return
        val progress = busy(getString(R.string.scan_busy_app))
        lifecycleScope.launch {
            val scanned = withContext(Dispatchers.IO) {
                try {
                    val file = File(target.path)
                    val sha = FileScanner.sha256(file)
                    val verdict = VirusTotalClient.fileVerdict(sha, key)
                    Scanned(
                        target.label, sha, VirusTotalClient.describe(target.label, sha, verdict),
                        verdict.state, file.length()
                    )
                } catch (e: Exception) {
                    Scanned(
                        target.label, "", "Could not read that app: ${e.message ?: "unexpected error"}",
                        VirusTotalClient.State.ERROR, -1L
                    )
                }
            }
            progress.dismiss()
            if (isFinishing || isDestroyed) return@launch
            showScan(scanned) { File(target.path).inputStream() }
        }
    }

    private fun busy(message: String): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.scan_busy_title)
            .setMessage(message)
            .setCancelable(false)
            .create()
        dialog.applyScreenshotPolicy(this)
        dialog.show()
        return dialog
    }

    private fun showScan(scanned: Scanned, open: () -> InputStream) {
        if (scanned.state == VirusTotalClient.State.UNKNOWN) {
            resultDialog(getString(R.string.scan_result_title), scanned.text, getString(R.string.upload_action)) {
                confirmUpload(scanned, open)
            }
        } else {
            resultDialog(getString(R.string.scan_result_title), scanned.text)
        }
    }

    private fun confirmUpload(scanned: Scanned, open: () -> InputStream) {
        val size = if (scanned.size > 0) {
            String.format(Locale.US, "%.1f MB", scanned.size / 1048576.0)
        } else {
            getString(R.string.upload_unknown_size)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.upload_confirm_title)
            .setMessage(getString(R.string.upload_confirm_body, scanned.name, size))
            .setPositiveButton(R.string.upload_confirm_yes) { _, _ -> uploadAndWatch(scanned, open) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.applyScreenshotPolicy(this)
        dialog.show()
    }

    private fun uploadAndWatch(scanned: Scanned, open: () -> InputStream) {
        val key = SecureKeys.vtKey(this)
        if (key.isBlank()) return
        val progress: AlertDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.upload_progress_title)
            .setMessage(getString(R.string.upload_progress_sending))
            .setCancelable(false)
            .setNegativeButton(R.string.stop, null)
            .create()
        progress.applyScreenshotPolicy(this)
        progress.show()

        var stopped = false
        progress.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { stopped = true }

        lifecycleScope.launch {
            val upload = withContext(Dispatchers.IO) {
                VirusTotalClient.uploadFile(scanned.name, scanned.size, key, open)
            }
            if (upload.analysisId.isEmpty()) {
                progress.dismiss()
                if (!isFinishing && !isDestroyed) {
                    resultDialog(getString(R.string.upload_result_title), upload.error)
                }
                return@launch
            }
            var verdict = VirusTotalClient.Verdict(VirusTotalClient.State.PENDING)
            var tries = 0
            progress.setMessage(getString(R.string.upload_progress_waiting))
            while (tries < POLL_TRIES && !stopped) {
                var waited = 0
                while (waited < POLL_SECONDS && !stopped) {
                    delay(1000)
                    waited++
                }
                if (stopped) break
                verdict = withContext(Dispatchers.IO) { VirusTotalClient.analysis(upload.analysisId, key) }
                tries++
                if (verdict.state != VirusTotalClient.State.PENDING &&
                    verdict.state != VirusTotalClient.State.RATE_LIMITED
                ) {
                    break
                }
            }
            progress.dismiss()
            if (isFinishing || isDestroyed) return@launch
            val body = when {
                stopped || verdict.state == VirusTotalClient.State.PENDING ->
                    getString(R.string.upload_still_running, scanned.name)
                else -> VirusTotalClient.describe(scanned.name, scanned.sha, verdict)
            }
            resultDialog(getString(R.string.upload_result_title), body)
        }
    }

    private fun appScanPrompt() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { FileScanner.installedApps(this@MainActivity) }
            if (apps.isEmpty()) {
                Snackbar.make(binding.root, "No installed apps found to scan.", Snackbar.LENGTH_LONG).show()
                return@launch
            }
            val minutes = (apps.size * 16) / 60 + 1
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("Scan installed apps")
                .setMessage(
                    "${apps.size} apps to check. A free VirusTotal key allows four lookups a minute, " +
                        "so this takes roughly $minutes minutes. A sweep never uploads anything, only the fingerprint " +
                        "of each app is sent. " +
                        "You can stop it at any point."
                )
                .setPositiveButton("Start") { _, _ -> runAppScan(apps, SecureKeys.vtKey(this@MainActivity)) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun uploadUnseen(unseen: List<Pair<FileScanner.Target, String>>) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.upload_pick_app)
            .setItems(unseen.map { it.first.label }.toTypedArray()) { _, which ->
                val (target, sha) = unseen[which]
                val scanned = Scanned(
                    target.label, sha, "", VirusTotalClient.State.UNKNOWN, File(target.path).length()
                )
                confirmUpload(scanned) { File(target.path).inputStream() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun runAppScan(apps: List<FileScanner.Target>, key: String) {
        val progress: AlertDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Scanning apps")
            .setMessage("Starting")
            .setCancelable(false)
            .setNegativeButton("Stop", null)
            .create()
        progress.applyScreenshotPolicy(this)
        progress.show()

        var stopped = false
        progress.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { stopped = true }

        lifecycleScope.launch {
            val flagged = ArrayList<String>()
            val unseen = ArrayList<Pair<FileScanner.Target, String>>()
            val seen = HashMap<String, VirusTotalClient.Verdict>()
            var done = 0
            var note = ""
            for (app in apps) {
                if (stopped) break
                var digest = ""
                val verdict = withContext(Dispatchers.IO) {
                    try {
                        val sha = FileScanner.sha256(File(app.path))
                        digest = sha
                        seen[sha] ?: VirusTotalClient.fileVerdict(sha, key).also { seen[sha] = it }
                    } catch (e: Exception) {
                        VirusTotalClient.Verdict(
                            VirusTotalClient.State.ERROR,
                            detail = e.message ?: "could not read the package"
                        )
                    }
                }
                done++
                if (verdict.state == VirusTotalClient.State.UNKNOWN && digest.isNotEmpty()) {
                    unseen.add(app to digest)
                }
                when (verdict.state) {
                    VirusTotalClient.State.MALICIOUS ->
                        flagged.add("${app.label} flagged by ${verdict.malicious} of ${verdict.total} engines")
                    VirusTotalClient.State.SUSPICIOUS ->
                        flagged.add("${app.label} looks suspicious to ${verdict.suspicious} of ${verdict.total} engines")
                    VirusTotalClient.State.BAD_KEY -> {
                        note = verdict.detail
                        stopped = true
                    }
                    VirusTotalClient.State.RATE_LIMITED -> note = "Hit the VirusTotal rate limit part way through."
                    else -> {
                    }
                }
                progress.setMessage("Checked $done of ${apps.size}. Flagged so far: ${flagged.size}")
                if (!stopped && done < apps.size) {
                    var waited = 0
                    while (waited < 16 && !stopped) {
                        delay(1000)
                        waited++
                    }
                }
            }
            progress.dismiss()
            if (isFinishing || isDestroyed) return@launch
            val body = buildString {
                append("Checked ").append(done).append(" of ").append(apps.size).append(" apps.")
                if (note.isNotBlank()) append("\n").append(note)
                append("\n\n")
                if (flagged.isEmpty()) {
                    append("Nothing was flagged.")
                } else {
                    append("Flagged:\n")
                    flagged.forEach { append("  ").append(it).append("\n") }
                }
                if (unseen.isNotEmpty()) {
                    append("\n\n").append(getString(R.string.upload_unseen_count, unseen.size))
                }
            }
            if (unseen.isEmpty()) {
                resultDialog(getString(R.string.app_scan_result_title), body)
            } else {
                resultDialog(getString(R.string.app_scan_result_title), body, getString(R.string.upload_action)) {
                    uploadUnseen(unseen)
                }
            }
        }
    }

    private fun resultDialog(title: String, body: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
        if (actionLabel != null && action != null) {
            builder.setNeutralButton(actionLabel) { _, _ -> action() }
        }
        val dialog = builder.create()
        dialog.applyScreenshotPolicy(this)
        dialog.show()
    }

    private companion object {
        const val POLL_TRIES = 6
        const val POLL_SECONDS = 20
    }

    private fun scanUrlDialog() {
        val key = SecureKeys.vtKey(this)
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

    private fun onImagePicked(uri: Uri) {
        try {
            cleanedImage = MetadataWiper.wipeToCache(this, uri)
            saveImage.launch("clean_${System.currentTimeMillis()}.jpg")
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Could not process image: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }
}
