package com.davmux.app

import android.app.Activity
import android.app.ProgressDialog
import android.os.Build
import android.system.Os
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * DAVMuxInstaller — installs the Termux bootstrap packages into:
 *   /data/data/com.termux/files/usr   ← $PREFIX (bash, apt, python, git, curl, …)
 *   /data/data/com.termux/files/home  ← $HOME
 *
 * The bootstrap ZIP is embedded in the native library libtermux-bootstrap.so
 * (same mechanism as Termux), compiled via NDK from termux-bootstrap-zip.S.
 *
 * On first launch this runs once. Subsequent launches skip it if $PREFIX exists.
 */
object DAVMuxInstaller {

    private const val TAG = "DAVMuxInstaller"

    // These paths MUST match com.termux — bootstrap binaries have them hardcoded
    val PREFIX_PATH  = "/data/data/com.termux/files/usr"
    val HOME_PATH    = "/data/data/com.termux/files/home"
    val STAGING_PATH = "/data/data/com.termux/files/usr-staging"

    val PREFIX  = File(PREFIX_PATH)
    val HOME    = File(HOME_PATH)
    val STAGING = File(STAGING_PATH)

    /** Call from MainActivity.onCreate() — runs installer if needed, then calls whenDone */
    fun setupIfNeeded(activity: Activity, whenDone: Runnable) {
        if (PREFIX.isDirectory && File("$PREFIX_PATH/bin/bash").exists()) {
            Log.d(TAG, "Bootstrap already installed, skipping")
            whenDone.run()
            return
        }

        val dialog = ProgressDialog(activity).apply {
            setMessage("DAVMux: Installing packages…")
            setCancelable(false)
            show()
        }

        Thread {
            try {
                install()
                activity.runOnUiThread {
                    dialog.dismiss()
                    whenDone.run()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bootstrap install failed", e)
                activity.runOnUiThread {
                    dialog.dismiss()
                    android.app.AlertDialog.Builder(activity)
                        .setTitle("DAVMux: Install failed")
                        .setMessage(e.message)
                        .setPositiveButton("Exit") { _, _ -> activity.finish() }
                        .show()
                }
            }
        }.start()
    }

    private fun install() {
        // Clean up any previous failed staging
        STAGING.deleteRecursively()
        STAGING.mkdirs()
        HOME.mkdirs()

        val symlinks = mutableListOf<Pair<String, String>>() // target → linkPath

        // Load bootstrap zip from native library
        val nativeLib = System.mapLibraryName("termux-bootstrap")
        val libs = listOf(
            "/data/data/com.termux/lib/$nativeLib",
            System.getProperty("java.library.path", "")!!.split(":").firstOrNull() + "/$nativeLib"
        )

        var zipBytes: ByteArray? = null
        for (libPath in libs) {
            val f = File(libPath)
            if (f.exists()) {
                zipBytes = extractBootstrapZipFromLib(f)
                if (zipBytes != null) break
            }
        }

        // Fallback: load directly from System.loadLibrary
        if (zipBytes == null) {
            try {
                System.loadLibrary("termux-bootstrap")
                zipBytes = getBootstrapZipBytes()
            } catch (e: UnsatisfiedLinkError) {
                throw RuntimeException("Could not load bootstrap library: ${e.message}")
            }
        }

        requireNotNull(zipBytes) { "Bootstrap zip bytes are null" }

        ZipInputStream(zipBytes.inputStream()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                if (entry.name == "SYMLINKS.txt") {
                    val reader = BufferedReader(InputStreamReader(zin))
                    var line = reader.readLine()
                    while (line != null) {
                        val parts = line.split("←")
                        if (parts.size == 2) {
                            symlinks.add(parts[0].trim() to "$STAGING_PATH/${parts[1].trim()}")
                        }
                        line = reader.readLine()
                    }
                } else {
                    val targetFile = File("$STAGING_PATH/${entry.name}")
                    targetFile.parentFile?.mkdirs()
                    if (!entry.isDirectory) {
                        FileOutputStream(targetFile).use { out -> zin.copyTo(out) }
                        if (entry.name.startsWith("bin/") || entry.name.startsWith("libexec") ||
                            entry.name.contains("/bin/") || entry.name.startsWith("lib/apt")) {
                            Os.chmod(targetFile.absolutePath, 0b111_101_101) // rwxr-xr-x
                        }
                    }
                }
                entry = zin.nextEntry
            }
        }

        // Create symlinks
        for ((target, linkPath) in symlinks) {
            val link = File(linkPath)
            link.parentFile?.mkdirs()
            Os.symlink(target, link.absolutePath)
        }

        // Atomic rename: staging → prefix
        if (!STAGING.renameTo(PREFIX)) {
            // Copy if rename fails (cross-device)
            STAGING.copyRecursively(PREFIX, overwrite = true)
            STAGING.deleteRecursively()
        }

        Log.d(TAG, "Bootstrap installed to $PREFIX_PATH")
    }

    private fun extractBootstrapZipFromLib(libFile: File): ByteArray? {
        // The bootstrap zip is appended after the ELF binary in termux-bootstrap-zip.S
        // Look for PK header (zip magic bytes 0x50 0x4B 0x03 0x04)
        val bytes = libFile.readBytes()
        for (i in 0 until bytes.size - 4) {
            if (bytes[i] == 0x50.toByte() && bytes[i+1] == 0x4B.toByte() &&
                bytes[i+2] == 0x03.toByte() && bytes[i+3] == 0x04.toByte()) {
                return bytes.copyOfRange(i, bytes.size)
            }
        }
        return null
    }

    // Native method — implemented in termux-bootstrap.c
    private external fun getBootstrapZipBytes(): ByteArray

    /** Build the environment array for TerminalSession using the installed $PREFIX */
    fun buildEnvironment(context: android.content.Context): Array<String> {
        val prefixBin = "$PREFIX_PATH/bin"
        val prefixLib = "$PREFIX_PATH/lib"
        return arrayOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "HOME=$HOME_PATH",
            "PREFIX=$PREFIX_PATH",
            "TMPDIR=$PREFIX_PATH/tmp",
            "LANG=en_US.UTF-8",
            "PATH=$prefixBin:/system/bin:/bin",
            "LD_LIBRARY_PATH=$prefixLib:/system/lib64",
            "SHELL=$prefixBin/bash",
            "DAVMUX=1"
        )
    }

    /** Shell to launch — bash from the installed prefix */
    fun getShell() = "$PREFIX_PATH/bin/bash"
}
