package com.sky.ps5cheatfinder

data class Candidate(val address: Long, val lastBytes: ByteArray, val currentBytes: ByteArray = lastBytes)
class MemoryScanner(private val conn: Ps5Connection, private val pid: Int, val type: ValueType) {
    var candidates: List<Candidate> = emptyList(); private set
    fun firstScan(text: String): Int {
        val bytes = type.encode(text) ?: return -1
        candidates = conn.scanExact(pid, type, bytes).map { Candidate(it, bytes) }
        return candidates.size
    }
    fun refreshCurrentValues() {
        candidates = candidates.map { candidate ->
            val now = conn.readMemory(pid, candidate.address, type.size)
            if (now == null) candidate else candidate.copy(currentBytes = now)
        }
    }

    fun nextScan(mode: CompareMode, text: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int {
        val needsNumber = mode == CompareMode.EXACT || mode == CompareMode.BIGGER || mode == CompareMode.SMALLER
        if (needsNumber && type.encode(text) == null) return -1
        val target = text.toDoubleOrNull() ?: Double.NaN; val targetBytes = type.encode(text)
        val kept = ArrayList<Candidate>()
        val source = candidates
        // Read nearby candidates in one request (up to 4 KiB), then slice each value
        // locally. Sparse candidates naturally fall back to smaller individual reads.
        // This uses only the already-supported PROC_READ command; no guessed protocol opcodes.
        val maxBlock = 4096
        var index = 0
        while (index < source.size) {
            val first = source[index]
            var end = index + 1
            val blockEndLimit = first.address + maxBlock
            while (end < source.size && source[end].address >= first.address &&
                source[end].address + type.size <= blockEndLimit) {
                end++
            }
            val blockSize = (source[end - 1].address - first.address).toInt() + type.size
            val block = conn.readMemory(pid, first.address, blockSize)
            for (candidateIndex in index until end) {
                val c = source[candidateIndex]
                val offset = (c.address - first.address).toInt()
                val now = if (block != null && offset >= 0 && offset + type.size <= block.size)
                    block.copyOfRange(offset, offset + type.size)
                else null
                val n = if (now != null) type.toNumber(now) else Double.NaN
                val old = type.toNumber(c.lastBytes)
                val matches = now != null && when (mode) {
                    CompareMode.EXACT -> targetBytes != null && now.contentEquals(targetBytes)
                    CompareMode.BIGGER -> n > target
                    CompareMode.SMALLER -> n < target
                    CompareMode.INCREASED -> n > old
                    CompareMode.DECREASED -> n < old
                    CompareMode.CHANGED -> n != old
                    CompareMode.UNCHANGED -> n == old
                }
                if (matches) kept.add(Candidate(c.address, now!!))
                if (candidateIndex % 25 == 0 || candidateIndex == source.lastIndex)
                    onProgress(candidateIndex + 1, source.size)
            }
            index = end
        }
        candidates = kept; return candidates.size
    }
    fun writeToAll(text: String): Int { val bytes = type.encode(text) ?: return -1; return candidates.count { conn.writeMemory(pid, it.address, bytes) } }
}
