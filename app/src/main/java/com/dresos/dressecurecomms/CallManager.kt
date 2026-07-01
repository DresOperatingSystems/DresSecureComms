/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

object CallManager {
    @Volatile var call: Call? = null
    @Volatile var inCallService: InCallService? = null

    fun setSpeaker(on: Boolean) {
        inCallService?.setAudioRoute(
            if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        )
    }

    fun isSpeakerOn(): Boolean = inCallService?.callAudioState?.route == CallAudioState.ROUTE_SPEAKER

    fun setMuted(muted: Boolean) { inCallService?.setMuted(muted) }
    fun isMuted(): Boolean = inCallService?.callAudioState?.isMuted == true

    fun playDtmf(c: Char) { call?.playDtmfTone(c) }
    fun stopDtmf() { call?.stopDtmfTone() }

    fun toggleHold() {
        val c = call ?: return
        if (c.state == Call.STATE_HOLDING) c.unhold() else c.hold()
    }
    fun isOnHold(): Boolean = call?.state == Call.STATE_HOLDING

    fun canHold(): Boolean = call?.details?.can(Call.Details.CAPABILITY_HOLD) == true
    fun canMute(): Boolean = call?.details?.can(Call.Details.CAPABILITY_MUTE) == true
}
