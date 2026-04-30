package com.davmux.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvOutput = findViewById<TextView>(R.id.tvOutput)
        val etCommand = findViewById<EditText>(R.id.etCommand)
        val btnRun = findViewById<Button>(R.id.btnRun)

        btnRun.setOnClickListener {
            val cmd = etCommand.text.toString()
            if (cmd.isNotBlank()) {
                tvOutput.append("\n~$ $cmd\n")
                etCommand.text.clear()
                executeCommand(cmd, tvOutput)
            }
        }
    }

    private fun executeCommand(command: String, tvOutput: TextView) {
        // Kører processen i en baggrundstråd
        executor.execute {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                
                val output = StringBuilder()
                var line: String?

                // Læs standard output
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                // Læs error output
                while (errorReader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }

                process.waitFor()
                
                // Opdater UI-tråden med resultatet
                runOnUiThread {
                    tvOutput.append(output.toString())
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvOutput.append("DAVMux Error: ${e.message}\n")
                }
            }
        }
    }
}
