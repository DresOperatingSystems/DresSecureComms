/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.VideoProfile
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.dresos.dressecurecomms.databinding.ActivityIncallBinding
import com.google.android.material.button.MaterialButton

class InCallActivity : AppCompatActivity() {
    private lateinit var b: ActivityIncallBinding

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = render()
        override fun onDetailsChanged(call: Call, details: Call.Details) = render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWhenLocked()
        b = ActivityIncallBinding.inflate(layoutInflater)
        setContentView(b.root)

        val call = CallManager.call
        if (call == null) { finish(); return }
        call.registerCallback(callback)

        b.answerBtn.setOnClickListener { CallManager.call?.answer(VideoProfile.STATE_AUDIO_ONLY) }
        b.declineBtn.setOnClickListener { decline() }

        b.muteBtn.setOnClickListener {
            CallManager.setMuted(!CallManager.isMuted())
            refreshControlStates()
        }
        b.speakerBtn.setOnClickListener {
            CallManager.setSpeaker(!CallManager.isSpeakerOn())
            refreshControlStates()
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
                    }
                    is android.view.ViewGroup -> attach(child)
                }
            }
        }
        attach(b.dtmfPanel)
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
        b.number.text = call.details.handle?.schemeSpecificPart ?: "Unknown"
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
        if (state == Call.STATE_DISCONNECTED) finish()
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
        CallManager.call?.unregisterCallback(callback)
    }
}
