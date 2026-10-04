package com.millquarterlabs.backbuttonmapper

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small in-memory log shown on the main screen, so we can see what the watch actually sends. */
object EventLog {
    private const val MAX_LINES = 40
    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun add(message: String) {
        lines.addFirst("${time.format(Date())}  $message")
        while (lines.size > MAX_LINES) lines.removeLast()
    }

    @Synchronized
    fun dump(): String = if (lines.isEmpty()) "(nothing yet)" else lines.joinToString("\n")
}
