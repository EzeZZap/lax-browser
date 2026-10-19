package com.javzzap.laxbrowser.platform

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps a local log of app crashes so testers can share it. Logs stay on the device and are never
 * sent anywhere. They contain the stack trace and device details only, no page addresses or
 * content.
 */
object CrashLog {
    private const val DIR = "crash_logs"
    private const val NEW_MARKER = "new"
    private const val MAX_LOGS = 5

    data class Entry(val name: String, val createdAt: Long, val text: String)

    /** Records uncaught exceptions, then lets the previous handler end the process. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val now = System.currentTimeMillis()
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val text = buildString {
            appendLine("Lax ${version.orEmpty()} crash report")
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date(now))}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(stack)
        }
        File(dir, "crash-$now.txt").writeText(text)
        File(dir, NEW_MARKER).writeText(now.toString())
        dir.listFiles { file -> file.name.startsWith("crash-") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_LOGS)
            ?.forEach { it.delete() }
    }

    /** Crash logs, newest first. Blocking: call off the main thread. */
    fun entries(context: Context): List<Entry> =
        File(context.filesDir, DIR).listFiles { file -> file.name.startsWith("crash-") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { Entry(it.name, it.lastModified(), runCatching { it.readText() }.getOrDefault("")) }
            .orEmpty()

    /** True once after a crash, so the app can offer to share the log. Blocking. */
    fun consumeNewCrash(context: Context): Boolean {
        val marker = File(File(context.filesDir, DIR), NEW_MARKER)
        return marker.exists() && marker.delete()
    }

    fun clear(context: Context) {
        File(context.filesDir, DIR).deleteRecursively()
    }
}
