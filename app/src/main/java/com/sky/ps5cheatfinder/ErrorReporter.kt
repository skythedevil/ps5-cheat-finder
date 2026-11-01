package com.sky.ps5cheatfinder

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ErrorReporter {
    fun build(
        stage: String,
        error: Throwable,
        ps5Ip: String,
        pid: Int?,
        valueType: String,
        compareMode: String,
        appVersion: String,
        recentLogs: String
    ): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        val code = "PS5CF-" + stage.uppercase(Locale.US).replace(Regex("[^A-Z0-9]+"), "_") +
            "-" + error.javaClass.simpleName.uppercase(Locale.US).take(24)
        val stack = error.stackTraceToString().take(12000)
        val logs = recentLogs.lineSequence().filter { it.isNotBlank() }.toList().takeLast(100).joinToString("\n")
        return buildString {
            appendLine("PS5 CHEAT FINDER ERROR REPORT")
            appendLine("Error code: $code")
            appendLine("Time: $stamp")
            appendLine("App version: $appVersion")
            appendLine("Stage: $stage")
            appendLine("PS5Debug target: ${ps5Ip.ifBlank { "not set" }}:744")
            appendLine("Game PID: ${pid ?: "not selected"}")
            appendLine("Value type: $valueType")
            appendLine("Compare mode: $compareMode")
            appendLine()
            appendLine("ERROR")
            appendLine(error.message ?: error.javaClass.name)
            appendLine()
            appendLine("STACK TRACE")
            appendLine(stack)
            appendLine()
            appendLine("RECENT APP LOGS (last 100 lines)")
            appendLine(if (logs.isBlank()) "(no app log entries captured)" else logs)
            appendLine()
            appendLine("Privacy: API keys and memory contents are not included.")
        }
    }
}
