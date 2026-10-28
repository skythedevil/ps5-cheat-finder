package com.sky.ps5cheatfinder

data class Candidate(val address: Long, val lastBytes: ByteArray)
class MemoryScanner(private val conn: Ps5Connection, private val pid: Int, val type: ValueType) {
    var candidates: List<Candidate> = emptyList(); private set
    fun firstScan(text: String): Int {
        val bytes = type.encode(text) ?: return -1
        candidates = conn.scanExact(pid, type, bytes).map { Candidate(it, bytes) }
        return candidates.size
    }
    fun nextScan(mode: CompareMode, text: String): Int {
        val needsNumber = mode == CompareMode.EXACT || mode == CompareMode.BIGGER || mode == CompareMode.SMALLER
        if (needsNumber && type.encode(text) == null) return -1
        val target = text.toDoubleOrNull() ?: Double.NaN; val targetBytes = type.encode(text)
        val kept = ArrayList<Candidate>()
        for (c in candidates) {
            val now = conn.readMemory(pid, c.address, type.size) ?: continue
            val n = type.toNumber(now); val old = type.toNumber(c.lastBytes)
            val matches = when (mode) { CompareMode.EXACT -> targetBytes != null && now.contentEquals(targetBytes); CompareMode.BIGGER -> n > target; CompareMode.SMALLER -> n < target; CompareMode.INCREASED -> n > old; CompareMode.DECREASED -> n < old; CompareMode.CHANGED -> n != old; CompareMode.UNCHANGED -> n == old }
            if (matches) kept.add(Candidate(c.address, now))
        }
        candidates = kept; return candidates.size
    }
    fun writeToAll(text: String): Int { val bytes = type.encode(text) ?: return -1; return candidates.count { conn.writeMemory(pid, it.address, bytes) } }
}
