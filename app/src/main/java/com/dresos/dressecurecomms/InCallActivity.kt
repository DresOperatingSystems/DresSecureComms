/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.preference.PreferenceManager
import com.dresos.dressecurecomms.databinding.ActivityIncallBinding
import com.dresos.dressecurecomms.util.Actions
import com.dresos.dressecurecomms.util.Contacts
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

class InCallActivity : AppCompatActivity() {
    private lateinit var b: ActivityIncallBinding
    private var callerName: String? = null
    private var phoneNumber: String? = null
    private var proximity: PowerManager.WakeLock? = null
    private var sensors: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var motionSensor: Sensor? = null
    private var near = false
    private var blackoutOff = false
    private var lastMagnitude = 0f
    private var motionHits = 0
    private var watched: Call? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = render()
        override fun onDetailsChanged(call: Call, details: Call.Details) = render()
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor?.type) {
                Sensor.TYPE_PROXIMITY -> onProximityValue(event.values.firstOrNull())
                Sensor.TYPE_ACCELEROMETER -> onMotionValues(event.values)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWhenLocked()
        b = ActivityIncallBinding.inflate(layoutInflater)
        setContentView(b.root)
        setUpProximity()

        val call = CallManager.call
        if (call == null) { finish(); return }
        watched = call
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
            try { startActivity(Intent(Intent.ACTION_DIAL)) } catch (e: Exception) {}
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
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_SCREEN_OFF, true)) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return
        try {
            if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            proximity = pm.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "dressecurecomms:proximity"
            )
            sensors = sm
            proximitySensor = sensor
            motionSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } catch (e: Exception) {
        }
    }

    private fun onProximityValue(value: Float?) {
        if (value == null) return
        val limit = minOf(proximitySensor?.maximumRange ?: NEAR_CM, NEAR_CM)
        near = value < limit
        if (!near) {
            blackoutOff = false
            motionHits = 0
        }
        updateProximity()
    }

    private fun onMotionValues(values: FloatArray) {
        if (values.size < 3) return
        val magnitude = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        val previous = lastMagnitude
        lastMagnitude = magnitude
        if (previous == 0f) return
        if (proximity?.isHeld != true) { motionHits = 0; return }
        if (abs(magnitude - previous) > MOTION_DELTA) motionHits++ else motionHits = 0
        if (motionHits >= MOTION_HITS) {
            blackoutOff = true
            motionHits = 0
            updateProximity()
        }
    }

    private fun updateProximity() {
        val lock = proximity ?: return
        val state = CallManager.call?.state
        val onCall = state == Call.STATE_ACTIVE || state == Call.STATE_DIALING
        val wanted = onCall && near && !blackoutOff && !CallManager.isSpeakerOn()
        try {
            if (wanted && !lock.isHeld) lock.acquire(60 * 60 * 1000L)
            if (!wanted && lock.isHeld) lock.release()
        } catch (e: Exception) {
        }
    }

    private fun releaseProximity() {
        val lock = proximity
        try {
            if (lock != null && lock.isHeld) lock.release()
        } catch (e: Exception) {
        }
        proximity = null
        sensors?.unregisterListener(sensorListener)
    }

    override fun onStart() {
        super.onStart()
        val sm = sensors ?: return
        near = false
        lastMagnitude = 0f
        motionHits = 0
        proximitySensor?.let { sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        motionSensor?.let { sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onStop() {
        super.onStop()
        sensors?.unregisterListener(sensorListener)
        near = false
        updateProximity()
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
        val call = CallManager.call ?: run { releaseProximity(); finish(); return }
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
        watched?.unregisterCallback(callback)
        watched = null
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private companion object {
        const val PREF_SCREEN_OFF = "call_screen_off"
        const val NEAR_CM = 5f
        const val MOTION_DELTA = 3f
        const val MOTION_HITS = 2
    }
}
