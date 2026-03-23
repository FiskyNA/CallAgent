package com.callagent.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────
//  AgentConfig — everything the user configures in the app
// ─────────────────────────────────────────────────────────────

data class AgentConfig(
    val name: String = "Aria",
    val company: String = "",
    val role: String = "customer support assistant",
    val tone: String = "professional and friendly",
    val greeting: String = "Hi! Thanks for calling. I'm a virtual assistant — how can I help you today?",
    val fallbackMessage: String = "I'm sorry, I didn't catch that. Could you say it again?",
    val language: String = "en-IN",
    val ringTimeoutSeconds: Int = 15,       // Wait this long before auto-answering
    val maxConversationTurns: Int = 10,
    val isEnabled: Boolean = false,
    val transferNumber: String = "",        // Human transfer fallback number
    val qaPairs: List<QAPair> = emptyList(),
    val questions: List<String> = emptyList(),
) {
    fun buildSystemPrompt(): String {
        val qaSection = if (qaPairs.isNotEmpty()) {
            "\n\nKnown Q&A — use these exact responses when relevant:\n" +
            qaPairs.joinToString("\n") { "- If asked about \"${it.trigger}\": say \"${it.response}\"" }
        } else ""

        val questionsSection = if (questions.isNotEmpty()) {
            "\n\nAsk the caller these questions during the conversation:\n" +
            questions.mapIndexed { i, q -> "${i + 1}. $q" }.joinToString("\n")
        } else ""

        return """You are $name, an AI phone assistant${if (company.isNotEmpty()) " for $company" else ""}.
Your role: $role.
Tone: $tone.

Rules:
- Keep every response to 1–2 SHORT sentences. This is a phone call — brevity matters.
- Never reveal you are an AI unless directly asked.
- If you cannot help, offer to take a message.
- Do not make up information you don't know.
- When the caller wants to end the call, say a polite goodbye and output exactly: [END]
- If the caller asks to speak to a human, say you'll transfer them and output: [TRANSFER]
$qaSection$questionsSection"""
    }
}

data class QAPair(val trigger: String, val response: String)

data class CallLog(
    val id: String,
    val callerNumber: String,
    val callerName: String = "Unknown",
    val startTime: Long,
    val durationSeconds: Int,
    val summary: String,
    val transcript: List<TranscriptLine>,
    val status: String, // "completed" | "transferred" | "error"
)

data class TranscriptLine(
    val speaker: String, // "caller" | "agent"
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
)

// ─────────────────────────────────────────────────────────────
//  ConfigRepository — reads/writes config from SharedPreferences
// ─────────────────────────────────────────────────────────────

class ConfigRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("callagent_prefs", Context.MODE_PRIVATE)

    // ── Config ──────────────────────────────────────────────

    fun getConfig(): AgentConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return AgentConfig()
        return try {
            val j = JSONObject(raw)
            val qaPairsArray = j.optJSONArray("qaPairs") ?: JSONArray()
            val questionsArray = j.optJSONArray("questions") ?: JSONArray()

            AgentConfig(
                name = j.optString("name", "Aria"),
                company = j.optString("company", ""),
                role = j.optString("role", "customer support assistant"),
                tone = j.optString("tone", "professional and friendly"),
                greeting = j.optString("greeting", "Hi! Thanks for calling. How can I help?"),
                fallbackMessage = j.optString("fallbackMessage", "Could you say that again?"),
                language = j.optString("language", "en-IN"),
                ringTimeoutSeconds = j.optInt("ringTimeoutSeconds", 15),
                maxConversationTurns = j.optInt("maxConversationTurns", 10),
                isEnabled = j.optBoolean("isEnabled", false),
                transferNumber = j.optString("transferNumber", ""),
                qaPairs = (0 until qaPairsArray.length()).map {
                    val qa = qaPairsArray.getJSONObject(it)
                    QAPair(qa.getString("trigger"), qa.getString("response"))
                },
                questions = (0 until questionsArray.length()).map {
                    questionsArray.getString(it)
                },
            )
        } catch (e: Exception) {
            AgentConfig()
        }
    }

    fun saveConfig(config: AgentConfig) {
        val j = JSONObject().apply {
            put("name", config.name)
            put("company", config.company)
            put("role", config.role)
            put("tone", config.tone)
            put("greeting", config.greeting)
            put("fallbackMessage", config.fallbackMessage)
            put("language", config.language)
            put("ringTimeoutSeconds", config.ringTimeoutSeconds)
            put("maxConversationTurns", config.maxConversationTurns)
            put("isEnabled", config.isEnabled)
            put("transferNumber", config.transferNumber)
            put("qaPairs", JSONArray().also { arr ->
                config.qaPairs.forEach { qa ->
                    arr.put(JSONObject().put("trigger", qa.trigger).put("response", qa.response))
                }
            })
            put("questions", JSONArray(config.questions))
        }
        prefs.edit().putString(KEY_CONFIG, j.toString()).apply()
    }

    // ── API Key ─────────────────────────────────────────────

    fun getApiKey(): String = prefs.getString(KEY_API_KEY, "") ?: ""

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    // ── Call Logs ────────────────────────────────────────────

    fun getCallLogs(): List<CallLog> {
        val raw = prefs.getString(KEY_LOGS, "[]") ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                val transcriptArr = j.optJSONArray("transcript") ?: JSONArray()
                CallLog(
                    id = j.getString("id"),
                    callerNumber = j.getString("callerNumber"),
                    callerName = j.optString("callerName", "Unknown"),
                    startTime = j.getLong("startTime"),
                    durationSeconds = j.getInt("durationSeconds"),
                    summary = j.optString("summary", ""),
                    status = j.optString("status", "completed"),
                    transcript = (0 until transcriptArr.length()).map { t ->
                        val tl = transcriptArr.getJSONObject(t)
                        TranscriptLine(
                            speaker = tl.getString("speaker"),
                            text = tl.getString("text"),
                            timestampMs = tl.optLong("timestampMs", 0L),
                        )
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addCallLog(log: CallLog) {
        val existing = getCallLogs().toMutableList()
        existing.add(0, log)
        val trimmed = existing.take(100)
        val arr = JSONArray()
        trimmed.forEach { l ->
            val transcriptArr = JSONArray()
            l.transcript.forEach { tl ->
                transcriptArr.put(JSONObject()
                    .put("speaker", tl.speaker)
                    .put("text", tl.text)
                    .put("timestampMs", tl.timestampMs))
            }
            arr.put(JSONObject()
                .put("id", l.id)
                .put("callerNumber", l.callerNumber)
                .put("callerName", l.callerName)
                .put("startTime", l.startTime)
                .put("durationSeconds", l.durationSeconds)
                .put("summary", l.summary)
                .put("status", l.status)
                .put("transcript", transcriptArr))
        }
        prefs.edit().putString(KEY_LOGS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_CONFIG  = "agent_config"
        private const val KEY_API_KEY = "anthropic_api_key"
        private const val KEY_LOGS    = "call_logs"
    }
}
