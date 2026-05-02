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
import com.termux.view.TerminalView

/**
 * DAVMux MainActivity v2 — full Termux bootstrap + AI
 *
 * On first launch: installs bootstrap packages (bash, apt, python, git, curl…)
 * Terminal: TerminalSession with bash from $PREFIX/bin/bash
 * AI: DAVMuxAISession (Claude Sonnet streaming, slash commands)
 */
class MainActivity : Activity() {

    private lateinit var terminalView: TerminalView
    private lateinit var etCommand: EditText
    private lateinit var btnRun: Button
    private lateinit var btnAI: Button
    private lateinit var aiPanel: LinearLayout

    private var terminalSession: TerminalSession? = null
    private val aiSession by lazy { DAVMuxAISession(this) }
    private var aiVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        terminalView = findViewById(R.id.terminalView)
        etCommand    = findViewById(R.id.etCommand)
        btnRun       = findViewById(R.id.btnRun)
        btnAI        = findViewById(R.id.btnAI)
        aiPanel      = findViewById(R.id.aiPanel)

        // Install bootstrap if needed, then start terminal
        DAVMuxInstaller.setupIfNeeded(this) {
            startTerminal()
            setupInput()
            setupAI()
        }
    }

    private fun startTerminal() {
        val shell = DAVMuxInstaller.getShell()            // $PREFIX/bin/bash
        val env   = DAVMuxInstaller.buildEnvironment(this) // PATH, HOME, PREFIX…
        val home  = DAVMuxInstaller.HOME_PATH

        val client = DAVMuxTerminalClient(terminalView)
        terminalSession = TerminalSession(
            shell, home,
            arrayOf(shell, "--login"),
            env,
            4000,
            client
        )
        terminalView.attachSession(terminalSession)
        terminalView.setTerminalViewClient(client)

        // Welcome banner written to PTY
        terminalSession?.write(
            "\r\n\u001b[32m  ▓ DAVMux v2  —  bash \u001b[0m· type 'apt install python' to add packages\r\n\r\n"
        )
    }

    private fun setupInput() {
        fun send() {
            val cmd = etCommand.text.toString().trim()
            if (cmd.isEmpty()) return
            etCommand.text.clear()
            if (cmd.startsWith("/")) {
                val result = aiSession.handleSlashCommand(cmd)
                if (result != null) {
                    terminalSession?.write("\r\n\u001b[32m$result\u001b[0m\r\n")
                    return
                }
            }
            terminalSession?.write("$cmd\n")
        }
        btnRun.setOnClickListener { send() }
        etCommand.setOnEditorActionListener { _, id, ev ->
            if (id == EditorInfo.IME_ACTION_SEND || ev?.keyCode == KeyEvent.KEYCODE_ENTER) {
                send(); true
            } else false
        }
    }

    private fun setupAI() {
        btnAI.setOnClickListener {
            aiVisible = !aiVisible
            aiPanel.visibility = if (aiVisible) android.view.View.VISIBLE else android.view.View.GONE
            btnAI.text = if (aiVisible) "HIDE AI" else "AI"
        }

        val aiInput  = findViewById<EditText>(R.id.etAIInput)
        val aiSend   = findViewById<Button>(R.id.btnAISend)
        val aiOutput = findViewById<TextView>(R.id.tvAIOutput)

        aiSend.setOnClickListener {
            val msg = aiInput.text.toString().trim().ifEmpty { return@setOnClickListener }
            aiInput.text.clear()
            if (msg.startsWith("/")) {
                val r = aiSession.handleSlashCommand(msg)
                if (r != null) { aiOutput.append("\n$r\n"); return@setOnClickListener }
            }
            aiOutput.append("\n▶ $msg\n◆ ")
            aiSession.sendMessage(msg, object : DAVMuxAISession.AIResponseListener {
                override fun onToken(t: String)  = runOnUiThread { aiOutput.append(t) }.let {}
                override fun onComplete(f: String) = runOnUiThread {
                    aiOutput.append("\n")
                    // Auto-execute shell commands the AI outputs
                    f.lines().filter { it.startsWith("$ ") }
                     .forEach { terminalSession?.write("${it.removePrefix("$ ")}\n") }
                }.let {}
                override fun onError(e: String) = runOnUiThread { aiOutput.append("[err: $e]\n") }.let {}
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        terminalSession?.finishIfRunning()
    }
}
