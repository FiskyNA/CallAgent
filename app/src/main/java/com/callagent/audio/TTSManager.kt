package com.callagent.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

// ─────────────────────────────────────────────────────────────
//  TTSManager — speaks the agent's replies through the call audio
//
//  During an active call, Android routes TTS audio through the
//  earpiece/speaker, which the caller hears on their end.
// ─────────────────────────────────────────────────────────────

class TTSManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.95f)   // Slightly slower for clarity on a call
                tts?.setPitch(1.0f)
                isReady = true
            }
        }
    }

    /**
     * Speak [text] and suspend until the utterance is fully done.
     * Returns when the caller can start speaking again.
     */
    suspend fun speak(text: String): Unit = suspendCancellableCoroutine { cont ->
        if (!isReady || tts == null) {
            cont.resume(Unit)
            return@suspendCancellableCoroutine
        }

        val utteranceId = "utt_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (cont.isActive) cont.resume(Unit)
            }
            override fun onError(utteranceId: String?) {
                if (cont.isActive) cont.resume(Unit)
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

        cont.invokeOnCancellation { tts?.stop() }
    }

    fun stop() {
        tts?.stop()
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
