/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.VideoProfile
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dresos.dressecurecomms.databinding.ActivityIncallBinding
import com.dresos.dressecurecomms.util.Actions
import com.dresos.dressecurecomms.util.Contacts
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InCallActivity : AppCompatActivity() {
    private lateinit var b: ActivityIncallBinding
    private var callerName: String? = null
    private var phoneNumber: String? = null
    private var proximity: PowerManager.WakeLock? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = render()
        override fun onDetailsChanged(call: Call, details: Call.Details) = render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWhenLocked()
        b = ActivityIncallBinding.inflate(layoutInflater)
        setContentView(b.root)
        setUpProximity()

        val call = CallManager.call
        if (call == null) { finish(); return }
        call.registerCallback(callback)

        val number = call.details.handle?.schemeSpecificPart
        phoneNumber = number
        if (!number.isNullOrBlank()) {
            b.numberActions.visibility = View.VISIBLE
            lifecycleScope.launch {
                val info = withContext(Dispatchers.IO) {
                    Contacts.nameFor(this@InCallActivity, number) to Contacts.isSaved(this@InCallActivity, number)
                }
                callerName = info.first
                b.number.text = info.first
                b.saveContactBtn.visibility = if (info.second) View.GONE else View.VISIBLE
            }
        }

        b.copyNumberBtn.setOnClickListener {
            phoneNumber?.let { Actions.copy(this, "number", it); toast(getString(R.string.number_copied)) }
        }
        b.saveContactBtn.setOnClickListener {
            phoneNumber?.let { Actions.saveToContacts(this, it); b.saveContactBtn.visibility = View.GONE }
        }

        b.answerBtn.setOnClickListener { CallManager.call?.answer(VideoProfile.STATE_AUDIO_ONLY) }
        b.declineBtn.setOnClickListener { decline() }

        b.muteBtn.setOnClickListener {
            CallManager.setMuted(!CallManager.isMuted())
            refreshControlStates()
        }
        b.speakerBtn.setOnClickListener {
            CallManager.setSpeaker(!CallManager.isSpeakerOn())
            refreshControlStates()
            updateProximity()
        }
        b.holdBtn.setOnClickListener {
            CallManager.toggleHold()
            refreshControlStates()
        }
        b.addCallBtn.setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_DIAL)) } catch (_: Exception) {}
        }
        b.keypadBtn.setOnClickListener {
            b.dtmfPanel.visibility =
                if (b.dtmfPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        wireDtmf()
        render()
    }

    private fun wireDtmf() {
        fun attach(group: android.view.ViewGroup) {
            for (i in 0 until group.childCount) {
                when (val child = group.getChildAt(i)) {
                    is MaterialButton -> child.setOnClickListener {
                        val ch = child.text?.firstOrNull() ?: return@setOnClickListener
                        CallManager.playDtmf(ch)
                        child.postDelayed({ CallManager.stopDtmf() }, 150)
                        appendDigit(ch)
                    }
                    is android.view.ViewGroup -> attach(child)
                }
            }
        }
        attach(b.dtmfPanel)

        b.dtmfPaste.setOnClickListener {
            val digits = Actions.clipboardText(this)?.filter { it.isDigit() || it == '*' || it == '#' }.orEmpty()
            if (digits.isEmpty()) { toast(getString(R.string.clipboard_empty)); return@setOnClickListener }
            var delay = 0L
            for (ch in digits) {
                b.dtmfDigits.postDelayed({
                    CallManager.playDtmf(ch)
                    b.dtmfDigits.postDelayed({ CallManager.stopDtmf() }, 150)
                    appendDigit(ch)
                }, delay)
                delay += 250
            }
        }
        b.dtmfBackspace.setOnClickListener {
            val cur = b.dtmfDigits.text?.toString().orEmpty()
            if (cur.isNotEmpty()) b.dtmfDigits.text = cur.dropLast(1)
        }
        b.dtmfBackspace.setOnLongClickListener {
            b.dtmfDigits.text = ""; true
        }
    }

    private fun appendDigit(ch: Char) {
        b.dtmfDigits.text = "${b.dtmfDigits.text}$ch"
    }

    private fun setUpProximity() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        try {
            if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            proximity = pm.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "dressecurecomms:proximity"
            )
        } catch (e: Exception) {
        }
    }

    private fun updateProximity() {
        val lock = proximity ?: return
        val state = CallManager.call?.state
        val active = state == Call.STATE_ACTIVE || state == Call.STATE_DIALING ||
            state == Call.STATE_CONNECTING || state == Call.STATE_HOLDING
        val wanted = active && !CallManager.isSpeakerOn()
        try {
            if (wanted && !lock.isHeld) lock.acquire(60 * 60 * 1000L)
            if (!wanted && lock.isHeld) lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        } catch (e: Exception) {
        }
    }

    private fun releaseProximity() {
        val lock = proximity ?: return
        try {
            if (lock.isHeld) lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        } catch (e: Exception) {
        }
        proximity = null
    }

    private fun showWhenLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun render() {
        val call = CallManager.call ?: run { finish(); return }
        b.number.text = callerName ?: call.details.handle?.schemeSpecificPart ?: "Unknown"
        val state = call.state
        b.status.text = when (state) {
            Call.STATE_RINGING -> getString(R.string.call_incoming)
            Call.STATE_DIALING, Call.STATE_CONNECTING -> getString(R.string.call_dialing)
            Call.STATE_ACTIVE -> getString(R.string.call_active)
            Call.STATE_HOLDING -> getString(R.string.call_holding)
            else -> ""
        }
        val ringing = state == Call.STATE_RINGING
        val showControls = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING ||
            state == Call.STATE_ACTIVE || state == Call.STATE_HOLDING
        b.answerBtn.visibility = if (ringing) View.VISIBLE else View.GONE
        b.controlsGrid.visibility = if (showControls) View.VISIBLE else View.GONE
        if (!showControls) b.dtmfPanel.visibility = View.GONE
        b.declineBtn.text = if (ringing) getString(R.string.decline) else getString(R.string.hang_up)
        if (showControls) refreshControlStates()
        updateProximity()
        if (state == Call.STATE_DISCONNECTED) { releaseProximity(); finish() }
    }

    private fun refreshControlStates() {
        b.muteBtn.isEnabled = CallManager.canMute()
        b.muteBtn.setText(if (CallManager.isMuted()) R.string.unmute else R.string.mute)
        b.speakerBtn.setText(if (CallManager.isSpeakerOn()) R.string.speaker_on else R.string.speaker)
        b.holdBtn.isEnabled = CallManager.canHold()
        b.holdBtn.setText(if (CallManager.isOnHold()) R.string.resume else R.string.hold)
    }

    @Suppress("DEPRECATION")
    private fun decline() {
        val call = CallManager.call ?: return
        if (call.state == Call.STATE_RINGING) call.reject(false, null) else call.disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseProximity()
        CallManager.call?.unregisterCallback(callback)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
