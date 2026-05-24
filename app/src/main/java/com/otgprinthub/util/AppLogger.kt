package com.otgprinthub.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-app log capture for USB/ESCPR debugging.
 * Stores last MAX_LINES in memory — readable via HomeScreen "View Logs" button.
 * Also forwards to Logcat so ADB users can filter by tag.
 */
object AppLogger {

    private const val MAX_LINES = 800
    private val lines = CopyOnWriteArrayList<String>()
    private val fmt   = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun d(tag: String, msg: String) { append("D", tag, msg); Log.d(tag, msg) }
    fun i(tag: String, msg: String) { append("I", tag, msg); Log.i(tag, msg) }
    fun w(tag: String, msg: String) { append("W", tag, msg); Log.w(tag, msg) }
    fun e(tag: String, msg: String) { append("E", tag, msg); Log.e(tag, msg) }

    fun separator(label: String) {
        append("═", "---", "══ $label ══")
        Log.i("---", "══ $label ══")
    }

    fun getAll(): String = lines.joinToString("\n")

    fun clear() = lines.clear()

    private fun append(level: String, tag: String, msg: String) {
        val line = "${fmt.format(Date())} $level/$tag: $msg"
        if (lines.size >= MAX_LINES) lines.removeAt(0)
        lines.add(line)
    }
}
