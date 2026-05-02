package com.davmux.app.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * DAVMuxAISession — Claude Sonnet streaming client for DAVMux.
 *
 * Integrates AI responses directly into the DAVMux terminal:
 * - Shell commands prefixed with "$ " are auto-sent to the PTY session
 * - Slash commands: /model /autopilot /clear /help
 * - Conversation history maintained per app session
 */
class DAVMuxAISession(private val context: Context) {

    interface AIResponseListener {
        fun onToken(token: String)
        fun onComplete(fullResponse: String)
        fun onError(error: String)
    }

    private val history = mutableListOf<JSONObject>()
    private var model = "claude-sonnet-4-5"
    private var autopilot = false
    private val client = OkHttpClient()

    fun sendMessage(message: String, listener: AIResponseListener) {
        history.add(JSONObject().put("role", "user").put("content", message))

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 2048)
            put("stream", true)
            put("system", buildSystemPrompt())
            put("messages", JSONArray(history))
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("x-api-key", getApiKey())
            .header("anthropic-version", "2023-06-01")
            .build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
                    val full = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        if (!line!!.startsWith("data: ")) continue
                        val data = line!!.removePrefix("data: ")
                        if (data == "[DONE]") break

                        val delta = JSONObject(data)
                            .optJSONObject("delta")?.optString("text") ?: continue
                        if (delta.isNotEmpty()) {
                            full.append(delta)
                            Handler(Looper.getMainLooper()).post { listener.onToken(delta) }
                        }
                    }

                    history.add(JSONObject().put("role", "assistant").put("content", full.toString()))
                    Handler(Looper.getMainLooper()).post { listener.onComplete(full.toString()) }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post { listener.onError(e.message ?: "Unknown error") }
            }
        }.start()
    }

    fun handleSlashCommand(command: String): String? = when {
        command.startsWith("/model ") -> {
            model = command.removePrefix("/model ").trim()
            "Model: $model"
        }
        command == "/autopilot" -> {
            autopilot = !autopilot
            if (autopilot) "⚡ Autopilot ON" else "✋ Autopilot OFF"
        }
        command == "/clear" -> { history.clear(); "History cleared" }
        command == "/help" ->
            "DAVMux AI commands:\n  /model <name>\n  /autopilot\n  /clear\n  /help\nModel: $model | Autopilot: $autopilot"
        else -> null
    }

    private fun buildSystemPrompt() =
        "You are DAVMux AI, running inside a terminal on Android. " +
        "Be concise. Prefix shell commands with '\$ ' on their own line. " +
        "Model: $model. Autopilot: $autopilot."

    private fun getApiKey() =
        System.getenv("DAVMUX_API_KEY")
            ?: context.getSharedPreferences("davmux", Context.MODE_PRIVATE)
                      .getString("api_key", "") ?: ""
}
