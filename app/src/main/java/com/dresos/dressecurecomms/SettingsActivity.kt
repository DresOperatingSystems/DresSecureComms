/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.dresos.dressecurecomms.location.MockLocation
import com.dresos.dressecurecomms.util.applyScreenshotPolicy
import kotlin.random.Random

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyScreenshotPolicy()
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, Prefs())
            .commit()
    }

    class Prefs : PreferenceFragmentCompat() {

        private val roleLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<Preference>("default_sms")?.setOnPreferenceClickListener { requestSms(); true }
            findPreference<Preference>("default_phone")?.setOnPreferenceClickListener { requestPhone(); true }
            findPreference<Preference>("default_screen")?.setOnPreferenceClickListener { requestScreen(); true }

            findPreference<Preference>("geo_random")?.setOnPreferenceClickListener {
                val lat = Random.nextDouble(-90.0, 90.0)
                val lng = Random.nextDouble(-180.0, 180.0)
                findPreference<EditTextPreference>("mock_lat")?.text = String.format("%.6f", lat)
                findPreference<EditTextPreference>("mock_long")?.text = String.format("%.6f", lng)
                toast(String.format("Random: %.4f, %.4f", lat, lng))
                true
            }

            findPreference<Preference>("geo_apply")?.setOnPreferenceClickListener {
                val lat = findPreference<EditTextPreference>("mock_lat")?.text?.toDoubleOrNull()
                val lng = findPreference<EditTextPreference>("mock_long")?.text?.toDoubleOrNull()
                if (lat == null || lng == null) toast("Set or randomize a location first")
                else toast(MockLocation.apply(requireContext(), lat, lng))
                true
            }
        }

        private fun requestSms() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestRole(RoleManager.ROLE_SMS)
            } else {
                roleLauncher.launch(
                    Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                        .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, requireContext().packageName)
                )
            }
        }

        private fun requestPhone() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestRole(RoleManager.ROLE_DIALER)
            } else {
                roleLauncher.launch(
                    Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                        .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, requireContext().packageName)
                )
            }
        }

        private fun requestScreen() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) requestRole(RoleManager.ROLE_CALL_SCREENING)
            else toast("Requires Android 10 or newer")
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        private fun requestRole(role: String) {
            val rm = requireContext().getSystemService(RoleManager::class.java)
            if (rm == null || !rm.isRoleAvailable(role)) { toast("Not available on this device"); return }
            if (rm.isRoleHeld(role)) { toast("Already set as default"); return }
            roleLauncher.launch(rm.createRequestRoleIntent(role))
        }

        private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }
}
