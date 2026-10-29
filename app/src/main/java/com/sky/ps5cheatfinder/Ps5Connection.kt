package com.sky.ps5cheatfinder

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ProcessInfo(val pid: Int, val name: String, val path: String, val titleId: String, val contentId: String)

class Ps5Connection(private val ip: String, private val port: Int = 744) {
    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null
    fun connect(): Boolean = try { val s = Socket(); s.connect(java.net.InetSocketAddress(ip, port), 8_000); s.soTimeout = 180_000; s.tcpNoDelay = true; socket = s; input = BufferedInputStream(s.getInputStream()); output = BufferedOutputStream(s.getOutputStream()); true } catch (_: Exception) { disconnect(); false }
    fun disconnect() { try { socket?.close() } catch (_: Exception) {}; socket = null; input = null; output = null }
    fun listProcesses(): List<Pair<String, Int>> {
        sendCommand(CMD_PROC_LIST)
        val status = readStatus()
        if (status != WIRE_SUCCESS) error("Process-list command rejected (status 0x${status.toUInt().toString(16)})")
        val count = readInt()
        if (count < 0 || count > 10000) error("Invalid process count from PS5Debug: $count")
        return (0 until count).map {
            String(readBytes(32), Charsets.UTF_8).substringBefore('\u0000').trim() to readInt()
        }
    }
    fun findPid(processName: String): Int? = listProcesses().firstOrNull { it.first == processName }?.second
    fun processInfo(pid: Int): ProcessInfo {
        val body = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(pid).array()
        sendCommand(CMD_PROC_INFO, body)
        val status = readStatus()
        if (status != WIRE_SUCCESS) error("Process-info command rejected (status 0x${status.toUInt().toString(16)})")
        val bytes = readBytes(188)
        fun field(offset: Int, size: Int) = String(bytes, offset, size, Charsets.UTF_8).substringBefore('\u0000').trim()
        return ProcessInfo(
            ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int,
            field(4, 40), field(44, 64), field(108, 16), field(124, 64)
        )
    }
    fun readMemory(pid: Int, address: Long, size: Int): ByteArray? { val body = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(pid).putLong(address).putInt(size).array(); sendCommand(CMD_PROC_READ, body); if (readStatus() != WIRE_SUCCESS) return null; return readBytes(size) }
    fun writeMemory(pid: Int, address: Long, data: ByteArray): Boolean { val body = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(pid).putLong(address).putInt(data.size).array(); sendCommand(CMD_PROC_WRITE, body); if (readStatus() != WIRE_SUCCESS) return false; sendRaw(data); return readStatus() == WIRE_SUCCESS }
    fun scanExact(pid: Int, type: ValueType, valueBytes: ByteArray): List<Long> {
        // PS5Debug-NG uses an ack/data/ack/address-stream exchange. Older PS5Debug
        // builds use the original PS4Debug framing: value bytes are part of the
        // command body and the reply starts with a uint32 result count.
        val body = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(pid).put(type.wireCode.toByte()).put(COMPARE_EXACT.toByte())
            .putInt(valueBytes.size).array()
        sendCommand(CMD_PROC_SCAN, body)
        val firstStatus = readStatus()
        if (firstStatus != WIRE_SUCCESS) {
            // Small positive values are not PS5Debug-NG status words. In the
            // legacy protocol they are the result count; reconnect before retrying
            // so the abandoned attempt cannot desynchronise subsequent commands.
            if (firstStatus in 0..MAX_LEGACY_RESULTS) {
                disconnect()
                if (!connect()) error("Legacy PS5Debug scan response detected, but reconnect failed.")
                return scanExactLegacy(pid, type, valueBytes)
            }
            error("Scan command rejected (raw response 0x${firstStatus.toUInt().toString(16)}). This looks like a legacy PS5Debug server or an unsupported scan protocol.")
        }
        sendRaw(valueBytes)
        val scanStatus = readStatus()
        if (scanStatus != WIRE_SUCCESS) error("PS5Debug could not complete the scan (status 0x${scanStatus.toUInt().toString(16)}).")
        val results = ArrayList<Long>()
        while (true) {
            val address = readLong()
            if (address == END_OF_LIST) break
            results.add(address)
        }
        return results
    }

    private fun scanExactLegacy(pid: Int, type: ValueType, valueBytes: ByteArray): List<Long> {
        // Legacy command framing places the compared value directly after the
        // 10-byte scan header inside packet.data (header datalen includes both).
        val scanHeader = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(pid).put(type.wireCode.toByte()).put(COMPARE_EXACT.toByte())
            .putInt(valueBytes.size).array()
        val body = ByteArray(scanHeader.size + valueBytes.size)
        System.arraycopy(scanHeader, 0, body, 0, scanHeader.size)
        System.arraycopy(valueBytes, 0, body, scanHeader.size, valueBytes.size)
        sendCommand(CMD_PROC_SCAN, body)

        // Original PS5Debug/PS4Debug replies with count followed by count addresses.
        val count = readInt()
        if (count < 0 || count > MAX_LEGACY_RESULTS) {
            disconnect()
            error("Legacy scan returned an invalid result count/status 0x${count.toUInt().toString(16)}. Update PS5Debug to a compatible build.")
        }
        val results = ArrayList<Long>(count)
        repeat(count) { results.add(readLong()) }
        return results
    }
    private fun sendCommand(cmd: Int, body: ByteArray = ByteArray(0)) { val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(PACKET_MAGIC).putInt(cmd).putInt(body.size).array(); sendRaw(header); if (body.isNotEmpty()) sendRaw(body) }
    private fun sendRaw(data: ByteArray) { val out = output ?: error("Not connected"); out.write(data); out.flush() }
    private fun readBytes(count: Int): ByteArray { val stream = input ?: error("Not connected"); val buffer = ByteArray(count); var got = 0; while (got < count) { val n = stream.read(buffer, got, count - got); if (n < 0) throw EOFException("PS5 closed the connection"); got += n }; return buffer }
    private fun readInt(): Int = ByteBuffer.wrap(readBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int
    private fun readLong(): Long = ByteBuffer.wrap(readBytes(8)).order(ByteOrder.LITTLE_ENDIAN).long
    private fun readStatus(): Int = readInt()
    companion object { val PACKET_MAGIC = 0xFFAABBCC.toInt(); val CMD_PROC_LIST = 0xBDAA0001.toInt(); val CMD_PROC_READ = 0xBDAA0002.toInt(); val CMD_PROC_WRITE = 0xBDAA0003.toInt(); val CMD_PROC_SCAN = 0xBDAA0009.toInt(); val CMD_PROC_INFO = 0xBDAA000A.toInt(); val WIRE_SUCCESS = 0x80000000.toInt(); const val END_OF_LIST = -1L; const val COMPARE_EXACT = 0; const val MAX_LEGACY_RESULTS = 2_000_000 }
}
