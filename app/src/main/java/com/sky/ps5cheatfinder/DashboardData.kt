package com.sky.ps5cheatfinder

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentLinkedQueue

data class DashboardRow(val address: Long, val baseline: String, val current: String)

data class DashboardAction(val action: String, val value: String = "")
object DashboardData {
    private val actions = ConcurrentLinkedQueue<DashboardAction>()
    fun enqueue(action: String, value: String) { actions.offer(DashboardAction(action, value)) }
    fun pollAction(): DashboardAction? = actions.poll()

    private val snapshot = AtomicReference(
        """{"status":"Waiting for app data","pid":null,"type":"—","count":0,"rows":[]}"""
    )

    fun update(status: String, pid: Int?, type: String, count: Int, rows: List<DashboardRow>, message: String = "") {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        val pidJson = pid?.toString() ?: "null"
        val rowJson = rows.joinToString(",") {
            """{"address":"0x${it.address.toString(16)}","baseline":"${esc(it.baseline)}","current":"${esc(it.current)}"}"""
        }
        snapshot.set("""{"status":"${esc(status)}","pid":${pidJson},"type":"${esc(type)}","count":${count},"message":"${esc(message)}","rows":[${rowJson}]}""")
    }

    fun json(): String = snapshot.get()
}
