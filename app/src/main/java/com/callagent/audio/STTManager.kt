package com.callagent.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

// ─────────────────────────────────────────────────────────────
//  STTManager — listens to the caller and returns text
//
//  Uses Android's built-in SpeechRecognizer which streams audio
//  to Google's speech recognition service. Works well for most
//  Indian English and regional accents.
// ─────────────────────────────────────────────────────────────

class STTManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    /**
     * Listen for the caller's speech and return the transcribed text.
     * Suspends until the caller stops speaking (silence detected).
     * Returns null if recognition failed or timed out.
     */
    suspend fun listen(languageTag: String = "en-IN"): String? =
        suspendCancellableCoroutine { cont ->

            // SpeechRecognizer must be created on the main thread
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = sr

            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onPartialResults(partial: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onResults(results: Bundle?) {
                    val matches = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()
                    if (cont.isActive) cont.resume(text)
                    sr.destroy()
                }

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    // ERROR_SPEECH_TIMEOUT (6) = silence, still resume with null
                    if (cont.isActive) cont.resume(null)
                    sr.destroy()
                }
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Silence detection — stop after 2s of silence
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }

            sr.startListening(intent)

            cont.invokeOnCancellation {
                sr.stopListening()
                sr.destroy()
            }
        }

    fun destroy() {
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }
}
