package com.sky.ps5cheatfinder

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class ValueType(val label: String, val size: Int, val wireCode: Int) {
    UINT8("1-byte unsigned", 1, 0), INT8("1-byte signed", 1, 1), UINT16("2-byte unsigned", 2, 2), INT16("2-byte signed", 2, 3), UINT32("4-byte unsigned", 4, 4), INT32("4-byte signed", 4, 5), INT64("8-byte signed", 8, 7), FLOAT("Float (decimal)", 4, 8), DOUBLE("Double (decimal)", 8, 9);
    fun encode(text: String): ByteArray? {
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        return when (this) {
            FLOAT -> text.toFloatOrNull()?.let { buffer.putFloat(it); buffer.array() }
            DOUBLE -> text.toDoubleOrNull()?.let { buffer.putDouble(it); buffer.array() }
            else -> text.toLongOrNull()?.let { n -> when (size) { 1 -> buffer.put(n.toByte()); 2 -> buffer.putShort(n.toShort()); 4 -> buffer.putInt(n.toInt()); else -> buffer.putLong(n) }; buffer.array() }
        }
    }
    fun toNumber(bytes: ByteArray): Double {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return when (this) {
            UINT8 -> (bytes[0].toInt() and 0xFF).toDouble(); INT8 -> bytes[0].toDouble(); UINT16 -> (b.getShort().toInt() and 0xFFFF).toDouble(); INT16 -> b.getShort().toDouble(); UINT32 -> (b.getInt().toLong() and 0xFFFFFFFFL).toDouble(); INT32 -> b.getInt().toDouble(); INT64 -> b.getLong().toDouble(); FLOAT -> b.getFloat().toDouble(); DOUBLE -> b.getDouble()
        }
    }
    fun toText(bytes: ByteArray): String = if (this == FLOAT || this == DOUBLE) toNumber(bytes).toString() else toNumber(bytes).toLong().toString()
}
enum class CompareMode(val label: String) { EXACT("Exact value"), BIGGER("Bigger than"), SMALLER("Smaller than"), INCREASED("Increased"), DECREASED("Decreased"), CHANGED("Changed"), UNCHANGED("Unchanged") }
