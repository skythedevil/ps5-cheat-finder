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
    @Synchronized fun connect(): Boolean = try { val s = Socket(); s.connect(java.net.InetSocketAddress(ip, port), 8_000); s.soTimeout = 180_000; s.tcpNoDelay = true; socket = s; input = BufferedInputStream(s.getInputStream()); output = BufferedOutputStream(s.getOutputStream()); true } catch (_: Exception) { disconnect(); false }
    @Synchronized fun disconnect() { try { socket?.close() } catch (_: Exception) {}; socket = null; input = null; output = null }
    @Synchronized fun listProcesses(): List<Pair<String, Int>> {
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
    @Synchronized fun processInfo(pid: Int): ProcessInfo {
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
    @Synchronized fun readMemory(pid: Int, address: Long, size: Int): ByteArray? { val body = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(pid).putLong(address).putInt(size).array(); sendCommand(CMD_PROC_READ, body); if (readStatus() != WIRE_SUCCESS) return null; return readBytes(size) }
    @Synchronized fun writeMemory(pid: Int, address: Long, data: ByteArray): Boolean { val body = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(pid).putLong(address).putInt(data.size).array(); sendCommand(CMD_PROC_WRITE, body); if (readStatus() != WIRE_SUCCESS) return false; sendRaw(data); return readStatus() == WIRE_SUCCESS }
    @Synchronized fun scanExact(pid: Int, type: ValueType, valueBytes: ByteArray): List<Long> {
        // This is a two-acknowledgement exchange followed by a streamed list of
        // uint64 addresses. The first acknowledgement must be consumed before
        // the comparison value is sent. The result stream must be drained to
        // the all-FF sentinel before another command uses this socket.
        try {
            val body = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(pid).put(type.wireCode.toByte()).put(COMPARE_EXACT.toByte())
                .putInt(valueBytes.size).array()
            sendCommand(CMD_PROC_SCAN, body)

            val firstStatus = readStatus()
            if (firstStatus != WIRE_SUCCESS) {
                error("Scan handshake failed: expected PS5Debug success acknowledgement but received raw 0x${firstStatus.toUInt().toString(16)}. The socket may contain stale scan data; reconnect before retrying.")
            }

            sendRaw(valueBytes)
            val scanStatus = readStatus()
            if (scanStatus != WIRE_SUCCESS) {
                error("PS5Debug could not start the scan (raw status 0x${scanStatus.toUInt().toString(16)}).")
            }

            val results = ArrayList<Long>()
            while (true) {
                val address = readLong()
                if (address == END_OF_LIST) break
                results.add(address)
            }
            return results
        } catch (e: Exception) {
            // A failed/incomplete exchange can leave address-stream bytes queued.
            // Closing the socket is safer than letting the next command interpret
            // an address as a status word.
            disconnect()
            throw e
        }
    }

    private fun sendCommand(cmd: Int, body: ByteArray = ByteArray(0)) { val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(PACKET_MAGIC).putInt(cmd).putInt(body.size).array(); sendRaw(header); if (body.isNotEmpty()) sendRaw(body) }
    private fun sendRaw(data: ByteArray) { val out = output ?: error("Not connected"); out.write(data); out.flush() }
    private fun readBytes(count: Int): ByteArray { val stream = input ?: error("Not connected"); val buffer = ByteArray(count); var got = 0; while (got < count) { val n = stream.read(buffer, got, count - got); if (n < 0) throw EOFException("PS5 closed the connection"); got += n }; return buffer }
    private fun readInt(): Int = ByteBuffer.wrap(readBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int
    private fun readLong(): Long = ByteBuffer.wrap(readBytes(8)).order(ByteOrder.LITTLE_ENDIAN).long
    private fun readStatus(): Int = readInt()
    companion object { val PACKET_MAGIC = 0xFFAABBCC.toInt(); val CMD_PROC_LIST = 0xBDAA0001.toInt(); val CMD_PROC_READ = 0xBDAA0002.toInt(); val CMD_PROC_WRITE = 0xBDAA0003.toInt(); val CMD_PROC_SCAN = 0xBDAA0009.toInt(); val CMD_PROC_INFO = 0xBDAA000A.toInt(); val WIRE_SUCCESS = 0x80000000.toInt(); const val END_OF_LIST = -1L; const val COMPARE_EXACT = 0 }
}
