package com.callagent.service

import android.telecom.Call
import android.telecom.CallScreeningService

// ─────────────────────────────────────────────────────────────
//  AICallScreeningService — runs before the phone rings
//
//  Currently passes all calls through unchanged.
//  You can extend this to:
//    - Block spam numbers
//    - Look up the caller in a CRM
//    - Pre-screen calls with a quick "who's calling?" question
// ─────────────────────────────────────────────────────────────

class AICallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // Let all calls through for now — InCallService handles the AI part
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)
    }
}
