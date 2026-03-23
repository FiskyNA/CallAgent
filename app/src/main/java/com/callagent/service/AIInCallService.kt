package com.callagent.service

import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.callagent.ai.ConversationManager
import com.callagent.data.ConfigRepository

// ─────────────────────────────────────────────────────────────
//  AIInCallService — the heart of CallAgent
//
//  Android calls this service for EVERY incoming/outgoing call
//  because we're registered as the default phone app.
//
//  When a call comes in:
//    - If the agent is enabled AND the user doesn't pick up
//      within ringTimeoutSeconds → auto-answer + start AI
//    - If the user picks up manually → do nothing (normal call)
//    - If the user declines → auto-answer + start AI
// ─────────────────────────────────────────────────────────────

class AIInCallService : InCallService() {

    private val handler = Handler(Looper.getMainLooper())
    private var autoAnswerRunnable: Runnable? = null
    private var conversationManager: ConversationManager? = null
    private var activeCall: Call? = null

    // ── Call lifecycle callbacks ─────────────────────────────

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        val config = ConfigRepository(this).getConfig()

        // Only intercept incoming calls when agent is enabled
        if (!config.isEnabled || call.details.callDirection != Call.Details.DIRECTION_INCOMING) {
            return
        }

        activeCall = call
        registerCallCallback(call, config.ringTimeoutSeconds.toLong())
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (call == activeCall) {
            cancelAutoAnswer()
            conversationManager?.cleanup()
            conversationManager = null
            activeCall = null
        }
    }

    // ── Register a callback to watch for user picking up / declining ──

    private fun registerCallCallback(call: Call, timeoutSeconds: Long) {
        call.registerCallback(object : Call.Callback() {

            override fun onStateChanged(call: Call, state: Int) {
                when (state) {
                    // User manually answered — cancel our auto-answer timer
                    Call.STATE_ACTIVE -> {
                        cancelAutoAnswer()
                    }

                    // User declined — immediately take over
                    Call.STATE_DISCONNECTING,
                    Call.STATE_DISCONNECTED -> {
                        cancelAutoAnswer()
                    }
                }
            }
        })

        // Schedule auto-answer after timeout
        val runnable = Runnable {
            if (call.state == Call.STATE_RINGING) {
                answerWithAI(call)
            }
        }
        autoAnswerRunnable = runnable
        handler.postDelayed(runnable, timeoutSeconds * 1000)
    }

    // ── Answer the call and hand off to AI ──────────────────

    private fun answerWithAI(call: Call) {
        try {
            // Answer as audio-only call
            call.answer(VideoProfile.STATE_AUDIO_ONLY)

            val callerNumber = call.details.handle?.schemeSpecificPart ?: "Unknown"

            // Start the AI conversation
            conversationManager = ConversationManager(this).apply {
                onCallEnded = {
                    // Hang up when AI says goodbye
                    handler.post { call.disconnect() }
                }
                onTransferRequested = { transferNumber ->
                    // For now, disconnect. In production, use TelecomManager to redirect.
                    handler.post { call.disconnect() }
                }
                start(callerNumber)
            }

        } catch (e: SecurityException) {
            // ANSWER_PHONE_CALLS permission not granted
            e.printStackTrace()
        }
    }

    // ── Cancel the pending auto-answer ───────────────────────

    private fun cancelAutoAnswer() {
        autoAnswerRunnable?.let { handler.removeCallbacks(it) }
        autoAnswerRunnable = null
    }
}
