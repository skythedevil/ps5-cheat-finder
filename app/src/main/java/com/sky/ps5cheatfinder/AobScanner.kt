package com.sky.ps5cheatfinder

/**
 * Searches byte signatures in bounded windows around known scan results.
 * This is intentionally not presented as a whole-process AOB scan because the
 * current ps5debug client does not expose process memory-map enumeration.
 */
object AobScanner {
    private data class Token(val value: Int?, val wildcard: Boolean)

    private fun parse(pattern: String): List<Token> {
        val parts = pattern.trim().split(Regex("[\\s,]+")).filter { it.isNotBlank() }
        require(parts.isNotEmpty()) { "Enter a byte pattern, e.g. 48 8B ?? 01." }
        require(parts.size <= 128) { "Pattern is limited to 128 bytes." }
        return parts.mapIndexed { i, part ->
            if (part == "?" || part == "??" || part == "**") Token(null, true)
            else {
                require(part.matches(Regex("[0-9A-Fa-f]{2}"))) { "Byte ${i + 1} ('$part') must be two hex digits or ??." }
                Token(part.toInt(16), false)
            }
        }
    }

    fun scanNearCandidates(
        connection: Ps5Connection,
        pid: Int,
        seedAddresses: List<Long>,
        patternText: String,
        radius: Int = 128,
        maxSeeds: Int = 300
    ): List<Long> {
        require(radius in 1..512) { "Radius must be between 1 and 512 bytes." }
        val pattern = parse(patternText)
        require(seedAddresses.isNotEmpty()) { "Run a normal value scan first to provide nearby search anchors." }
        val found = linkedSetOf<Long>()
        val seeds = seedAddresses.distinct().take(maxSeeds)
        for (address in seeds) {
            val start = (address - radius).coerceAtLeast(0L)
            val length = (address - start).toInt() + radius + pattern.size
            val memory = connection.readMemory(pid, start, length) ?: continue
            val lastStart = memory.size - pattern.size
            for (i in 0..lastStart) {
                var matches = true
                for (j in pattern.indices) {
                    val expected = pattern[j].value
                    if (expected != null && (memory[i + j].toInt() and 0xFF) != expected) {
                        matches = false
                        break
                    }
                }
                if (matches) found.add(start + i)
            }
        }
        return found.toList().sorted()
    }
}
