/* Copyright © 2026 The DresOS Foundation. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import com.dresos.dressecurecomms.data.SpamStore
import com.dresos.dressecurecomms.scan.SpamFilter

class CallScreenService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val builder = CallResponse.Builder()

        if (!isIncoming(callDetails)) {
            respondToCall(callDetails, builder.build())
            return
        }

        val number = callDetails.handle?.schemeSpecificPart
        val result = try {
            SpamFilter.evaluate(this, number)
        } catch (e: Exception) {
            SpamFilter.Result(SpamFilter.Action.ALLOW, "")
        }

        when (result.action) {
            SpamFilter.Action.BLOCK -> {
                builder.setDisallowCall(true)
                builder.setRejectCall(true)
                builder.setSkipNotification(true)
                record(number, result.reason)
            }
            SpamFilter.Action.SILENCE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setSilenceCall(true)
            }
            SpamFilter.Action.ALLOW -> {
            }
        }

        respondToCall(callDetails, builder.build())
    }

    private fun isIncoming(details: Call.Details): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return details.callDirection == Call.Details.DIRECTION_INCOMING
    }

    private fun record(number: String?, reason: String) {
        val n = number?.trim().orEmpty()
        if (n.isEmpty()) return
        try {
            if (SpamStore.ruleFor(this, n) == null) SpamStore.block(this, n, reason)
        } catch (e: Exception) {
        }
    }
}
