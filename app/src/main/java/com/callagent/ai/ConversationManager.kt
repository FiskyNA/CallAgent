package com.callagent.ai

import android.content.Context
import com.callagent.audio.STTManager
import com.callagent.audio.TTSManager
import com.callagent.data.AgentConfig
import com.callagent.data.CallLog
import com.callagent.data.ConfigRepository
import com.callagent.data.TranscriptLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

// ─────────────────────────────────────────────────────────────
//  ConversationManager — orchestrates the full AI call loop
//
//  Flow:
//    1. Greet the caller (TTS)
//    2. Listen (STT) → send to Claude → speak reply (TTS)
//    3. Repeat until [END], [TRANSFER], or max turns reached
//    4. Save transcript + summary to call log
// ─────────────────────────────────────────────────────────────

class ConversationManager(private val context: Context) {

    private val repo = ConfigRepository(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var tts: TTSManager? = null
    private var stt: STTManager? = null
    private var claude: ClaudeClient? = null

    private val conversationHistory = mutableListOf<Message>()
    private val transcript = mutableListOf<TranscriptLine>()

    var onTransferRequested: ((number: String) -> Unit)? = null
    var onCallEnded: (() -> Unit)? = null
    var onTranscriptUpdate: ((List<TranscriptLine>) -> Unit)? = null

    private var callStartTime = 0L
    private var callerNumber = ""

    // ── Start the conversation ───────────────────────────────

    fun start(callerNumber: String) {
        this.callerNumber = callerNumber
        this.callStartTime = System.currentTimeMillis()

        val config = repo.getConfig()
        val apiKey = repo.getApiKey()

        tts = TTSManager(context)
        stt = STTManager(context)
        claude = ClaudeClient(apiKey)

        scope.launch {
            runConversationLoop(config)
        }
    }

    private suspend fun runConversationLoop(config: AgentConfig) {
        val systemPrompt = config.buildSystemPrompt()
        var turns = 0

        // ── 1. Play greeting ──
        addToTranscript("agent", config.greeting)
        tts?.speak(config.greeting)

        // ── 2. Conversation loop ──
        while (turns < config.maxConversationTurns) {
            turns++

            // Listen to caller
            val callerSaid = stt?.listen(config.language)

            if (callerSaid.isNullOrBlank()) {
                // No speech detected — ask again once
                tts?.speak(config.fallbackMessage)
                addToTranscript("agent", config.fallbackMessage)
                val retry = stt?.listen(config.language)
                if (retry.isNullOrBlank()) {
                    // Still nothing — end gracefully
                    val goodbye = "Thank you for calling. We'll have someone follow up. Goodbye!"
                    tts?.speak(goodbye)
                    addToTranscript("agent", goodbye)
                    break
                }
                handleCallerSpeech(retry, systemPrompt, config)
                if (shouldEnd(conversationHistory.lastOrNull()?.content)) break
                continue
            }

            val shouldStop = handleCallerSpeech(callerSaid, systemPrompt, config)
            if (shouldStop) break
        }

        // ── 3. Save call log ──
        endCall(config)
    }

    /**
     * Processes what the caller said, gets Claude's reply, speaks it.
     * Returns true if the call should end.
     */
    private suspend fun handleCallerSpeech(
        callerSaid: String,
        systemPrompt: String,
        config: AgentConfig,
    ): Boolean {
        addToTranscript("caller", callerSaid)
        conversationHistory.add(Message("user", callerSaid))

        val reply = claude?.chat(systemPrompt, conversationHistory)
            ?: config.fallbackMessage

        conversationHistory.add(Message("assistant", reply))

        // Check for control signals before speaking
        return when {
            reply.contains("[TRANSFER]") -> {
                val cleanReply = reply.replace("[TRANSFER]", "").trim()
                if (cleanReply.isNotBlank()) {
                    addToTranscript("agent", cleanReply)
                    tts?.speak(cleanReply)
                }
                val transferMsg = "Please hold while I connect you."
                addToTranscript("agent", transferMsg)
                tts?.speak(transferMsg)
                onTransferRequested?.invoke(config.transferNumber)
                true
            }

            reply.contains("[END]") -> {
                val cleanReply = reply.replace("[END]", "").trim()
                val farewell = if (cleanReply.isBlank())
                    "Thank you for calling. Have a great day!"
                else cleanReply
                addToTranscript("agent", farewell)
                tts?.speak(farewell)
                true
            }

            else -> {
                addToTranscript("agent", reply)
                tts?.speak(reply)
                false
            }
        }
    }

    private suspend fun endCall(config: AgentConfig) {
        // Build transcript text for summary
        val transcriptText = transcript.joinToString("\n") { tl ->
            "${if (tl.speaker == "caller") "Caller" else "Agent"}: ${tl.text}"
        }

        val summary = if (transcriptText.isNotBlank()) {
            claude?.summarizeCall(transcriptText) ?: "Call ended"
        } else "No conversation recorded"

        val duration = ((System.currentTimeMillis() - callStartTime) / 1000).toInt()

        val log = CallLog(
            id = UUID.randomUUID().toString(),
            callerNumber = callerNumber,
            startTime = callStartTime,
            durationSeconds = duration,
            summary = summary,
            transcript = transcript.toList(),
            status = "completed",
        )

        repo.addCallLog(log)
        cleanup()
        onCallEnded?.invoke()
    }

    // ── Helpers ─────────────────────────────────────────────

    private fun addToTranscript(speaker: String, text: String) {
        val line = TranscriptLine(speaker, text)
        transcript.add(line)
        onTranscriptUpdate?.invoke(transcript.toList())
    }

    private fun shouldEnd(lastReply: String?) =
        lastReply?.contains("[END]") == true || lastReply?.contains("[TRANSFER]") == true

    fun cleanup() {
        tts?.destroy()
        stt?.destroy()
        tts = null
        stt = null
    }
}
