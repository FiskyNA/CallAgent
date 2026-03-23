package com.callagent.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────
//  ClaudeClient — calls the Anthropic messages API
// ─────────────────────────────────────────────────────────────

data class Message(
    val role: String,   // "user" | "assistant"
    val content: String,
)

class ClaudeClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json".toMediaType()

    /**
     * Send the full conversation history to Claude and get the next response.
     * Returns the assistant's reply text, or null if the call failed.
     */
    suspend fun chat(
        systemPrompt: String,
        history: List<Message>,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val messagesArray = JSONArray()
            history.forEach { msg ->
                messagesArray.put(
                    JSONObject()
                        .put("role", msg.role)
                        .put("content", msg.content)
                )
            }

            val body = JSONObject()
                .put("model", "claude-sonnet-4-20250514")
                .put("max_tokens", 150)          // Keep responses short for calls
                .put("system", systemPrompt)
                .put("messages", messagesArray)
                .toString()
                .toRequestBody(JSON)

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) {
                println("Claude API error ${response.code}: $responseBody")
                return@withContext null
            }

            val json = JSONObject(responseBody)
            val content = json.getJSONArray("content")
            if (content.length() == 0) return@withContext null

            content.getJSONObject(0).getString("text").trim()

        } catch (e: Exception) {
            println("ClaudeClient exception: ${e.message}")
            null
        }
    }

    /**
     * Generate a short summary of a call transcript.
     */
    suspend fun summarizeCall(transcript: String): String = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("model", "claude-sonnet-4-20250514")
                .put("max_tokens", 100)
                .put("messages", JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content",
                            "Summarize this phone call in 1 sentence. " +
                            "Focus on what the caller wanted and the outcome.\n\n$transcript"
                        )
                ))
                .toString()
                .toRequestBody(JSON)

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: return@withContext "")
            json.getJSONArray("content").getJSONObject(0).getString("text").trim()

        } catch (e: Exception) {
            "Call ended"
        }
    }
}
