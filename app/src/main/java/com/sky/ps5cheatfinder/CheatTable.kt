package com.sky.ps5cheatfinder

import org.json.JSONArray
import org.json.JSONObject

data class CheatEntry(val label: String, val address: Long, val type: ValueType, val lockedValue: String? = null)
object CheatFile {
    fun toJson(entries: List<CheatEntry>): String {
        val list = JSONArray()
        entries.forEach { e -> list.put(JSONObject().put("label", e.label).put("address", "0x" + e.address.toString(16)).put("type", e.type.name).put("locked_value", e.lockedValue ?: JSONObject.NULL)) }
        return JSONObject().put("format", "ps5-cheat-finder").put("version", 1).put("entries", list).toString(2)
    }
    fun fromJson(text: String): List<CheatEntry> {
        val list = JSONObject(text).getJSONArray("entries")
        return (0 until list.length()).map { i -> val o = list.getJSONObject(i); CheatEntry(o.getString("label"), o.getString("address").removePrefix("0x").toLong(16), ValueType.valueOf(o.getString("type")), if (o.isNull("locked_value")) null else o.getString("locked_value")) }
    }
}
object Freezer {
    fun applyLocks(conn: Ps5Connection, pid: Int, entries: List<CheatEntry>) {
        for (e in entries) { val text = e.lockedValue ?: continue; val bytes = e.type.encode(text) ?: continue; conn.writeMemory(pid, e.address, bytes) }
    }
}
