package com.davmux.app

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.davmux.app.ai.DAVMuxAISession
import com.davmux.app.terminal.DAVMuxTerminalClient
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * DAVMux MainActivity — Remix of DAVMux UI × Termux terminal engine × AI session
 *
 * Layout: DAVMux original aesthetic (#000000 bg, #00FF00 text, monospace)
 * Terminal: Upgraded from Runtime.exec() → Termux TerminalSession (full PTY, VT100)
 * AI: DAVMuxAISession panel toggled via [AI] button — streams Claude Sonnet responses
 */
class MainActivity : Activity() {

    private lateinit var terminalView: TerminalView
    private lateinit var etCommand: EditText
    private lateinit var btnRun: Button
    private lateinit var btnAI: Button
    private lateinit var aiPanel: LinearLayout

    private var terminalSession: TerminalSession? = null
    private val aiSession by lazy { DAVMuxAISession(this) }
    private var aiPanelVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        terminalView = findViewById(R.id.terminalView)
        etCommand    = findViewById(R.id.etCommand)
        btnRun       = findViewById(R.id.btnRun)
        btnAI        = findViewById(R.id.btnAI)
        aiPanel      = findViewById(R.id.aiPanel)

        setupTerminal()
        setupInputBar()
        setupAIPanel()
    }

    // ── Terminal (Termux engine) ──────────────────────────────────────────────

    private fun setupTerminal() {
        val shell = "/system/bin/sh"
        val env = arrayOf("TERM=xterm-256color", "HOME=/data/data/com.davmux.app")
        val args = arrayOf(shell)

        val client = DAVMuxTerminalClient(terminalView)
        terminalSession = TerminalSession(shell, "/data/data/com.davmux.app", args, env, 4000, client)
        terminalView.attachSession(terminalSession)
        terminalView.setTerminalViewClient(client)

        // DAVMux green-on-black color scheme
        terminalView.setBackgroundColor(0xFF000000.toInt())
    }

    private fun setupInputBar() {
        btnRun.setOnClickListener { sendCommand() }
        etCommand.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                sendCommand(); true
            } else false
        }
    }

    private fun sendCommand() {
        val cmd = etCommand.text.toString().trim()
        if (cmd.isEmpty()) return
        etCommand.text.clear()

        // Check for AI slash commands first
        if (cmd.startsWith("/")) {
            val result = aiSession.handleSlashCommand(cmd)
            if (result != null) {
                terminalSession?.write("\r\n\u001b[32m$result\u001b[0m\r\n")
                return
            }
        }

        // Write command to real terminal PTY
        terminalSession?.write("$cmd\n")
    }

    // ── AI Panel ─────────────────────────────────────────────────────────────

    private fun setupAIPanel() {
        btnAI.setOnClickListener { toggleAIPanel() }

        val aiInput  = findViewById<EditText>(R.id.etAIInput)
        val aiSend   = findViewById<Button>(R.id.btnAISend)
        val aiOutput = findViewById<TextView>(R.id.tvAIOutput)

        aiSend.setOnClickListener {
            val msg = aiInput.text.toString().trim()
            if (msg.isEmpty()) return@setOnClickListener
            aiInput.text.clear()

            // Slash command?
            if (msg.startsWith("/")) {
                val result = aiSession.handleSlashCommand(msg)
                if (result != null) {
                    aiOutput.append("\n\u25cf DAVMux: $result\n")
                    return@setOnClickListener
                }
            }

            aiOutput.append("\n\u25b6 You: $msg\n\u25cf DAVMux AI: ")

            aiSession.sendMessage(msg, object : DAVMuxAISession.AIResponseListener {
                override fun onToken(token: String) {
                    runOnUiThread { aiOutput.append(token) }
                }
                override fun onComplete(full: String) {
                    runOnUiThread {
                        aiOutput.append("\n")
                        // If AI outputs a $ command, write it to terminal
                        full.lines().filter { it.startsWith("$ ") }.forEach { line ->
                            terminalSession?.write("${line.removePrefix("$ ")}\n")
                        }
                    }
                }
                override fun onError(error: String) {
                    runOnUiThread { aiOutput.append("\n[Error: $error]\n") }
                }
            })
        }
    }

    private fun toggleAIPanel() {
        aiPanelVisible = !aiPanelVisible
        aiPanel.visibility = if (aiPanelVisible) android.view.View.VISIBLE else android.view.View.GONE
        btnAI.text = if (aiPanelVisible) "HIDE AI" else "AI"
    }

    override fun onDestroy() {
        super.onDestroy()
        terminalSession?.finishIfRunning()
    }
}
