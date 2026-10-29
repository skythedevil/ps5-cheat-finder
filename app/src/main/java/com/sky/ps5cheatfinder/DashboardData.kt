package com.sky.ps5cheatfinder

import java.util.concurrent.atomic.AtomicReference

data class DashboardRow(val address: Long, val baseline: String, val current: String)

object DashboardData {
    private val snapshot = AtomicReference(
        """{"status":"Waiting for app data","pid":null,"type":"—","count":0,"rows":[]}"""
    )

    fun update(status: String, pid: Int?, type: String, count: Int, rows: List<DashboardRow>) {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        val pidJson = pid?.toString() ?: "null"
        val rowJson = rows.joinToString(",") {
            """{"address":"0x${it.address.toString(16)}","baseline":"${esc(it.baseline)}","current":"${esc(it.current)}"}"""
        }
        snapshot.set("""{"status":"${esc(status)}","pid":${pidJson},"type":"${esc(type)}","count":${count},"rows":[${rowJson}]}""")
    }

    fun json(): String = snapshot.get()
}
