/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

/** Receives calls when DresSecureComms is the default phone app and shows the in-call screen. */
class DialerInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.inCallService = this
        CallManager.call = call
        startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (CallManager.call === call) CallManager.call = null
    }

    override fun onDestroy() {
        super.onDestroy()
        CallManager.inCallService = null
    }
}
