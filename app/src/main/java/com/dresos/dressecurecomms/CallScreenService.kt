/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * Registering this service makes the app selectable as the caller ID and spam app.
 * It currently allows every call through; blocking rules can be added later.
 */
class CallScreenService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        respondToCall(callDetails, CallResponse.Builder().build())
    }
}
