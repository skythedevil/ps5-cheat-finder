package com.sky.ps5cheatfinder

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** GoldHEN/etaHEN cheat-file export helpers for JSON, SHN (XML), and MC4. */
object CheatFormats {
    enum class Format(val extension: String, val mimeType: String) {
        JSON("json", "application/json"),
        SHN("shn", "application/xml"),
        MC4("mc4", "application/octet-stream")
    }

    data class Metadata(
        val gameName: String,
        val titleId: String,
        val version: String,
        val process: String = "eboot.bin",
        val author: String = "PS5 Cheat Finder"
    )

    data class Patch(
        val name: String,
        val description: String,
        val offset: String,
        val valueOn: String,
        val valueOff: String
    )

    fun cheatFileName(titleId: String, version: String, extension: String): String =
        "${titleId}_${version}.${extension}"

    /** Validates fields shared by all three formats before creating a file. */
    fun validate(metadata: Metadata, patches: List<Patch>) {
        require(metadata.gameName.isNotBlank()) { "Enter the game name." }
        require(metadata.titleId.matches(Regex("[A-Za-z0-9]{5,16}"))) { "Title ID should contain 5–16 letters or digits (for example PPSA01342)." }
        require(metadata.version.matches(Regex("[A-Za-z0-9._-]{1,24}"))) { "Enter a valid game version (for example 01.004.000)." }
        require(metadata.process.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "Enter a valid process name." }
        require(patches.isNotEmpty()) { "Add at least one cheat patch." }
        patches.forEachIndexed { index, patch ->
            require(patch.name.isNotBlank()) { "Cheat ${index + 1}: enter a cheat name." }
            require(patch.offset.matches(Regex("(0x)?[0-9A-Fa-f]{1,16}"))) { "Cheat ${index + 1}: offset must be hexadecimal." }
            val on = normalizeHex(patch.valueOn)
            val off = normalizeHex(patch.valueOff)
            require(on.isNotEmpty() && off.isNotEmpty()) { "Cheat ${index + 1}: enter both On and Off bytes." }
            require(on.length == off.length) { "Cheat ${index + 1}: On and Off bytes must be the same length so the original patch can be restored." }
        }
    }

    fun export(format: Format, metadata: Metadata, patches: List<Patch>): ByteArray {
        validate(metadata, patches)
        val text = when (format) {
            Format.JSON -> toGoldHenJson(metadata, patches)
            Format.SHN -> toShnXml(metadata, patches)
            Format.MC4 -> encryptShnToMc4(toShnXml(metadata, patches)).toString(StandardCharsets.US_ASCII)
        }
        return text.toByteArray(if (format == Format.SHN) StandardCharsets.UTF_8 else StandardCharsets.UTF_8)
    }

    fun toGoldHenJson(metadata: Metadata, patches: List<Patch>): String {
        val mods = JSONArray()
        patches.forEach { p ->
            val memory = JSONArray().put(
                JSONObject()
                    .put("offset", normalizeOffset(p.offset))
                    .put("on", normalizeHex(p.valueOn))
                    .put("off", normalizeHex(p.valueOff))
            )
            mods.put(
                JSONObject()
                    .put("name", p.name)
                    .put("description", p.description)
                    .put("type", "checkbox")
                    .put("memory", memory)
            )
        }
        return JSONObject()
            .put("name", metadata.gameName)
            .put("id", metadata.titleId.uppercase())
            .put("version", metadata.version)
            .put("process", metadata.process)
            .put("credits", JSONArray().put(metadata.author.ifBlank { "PS5 Cheat Finder" }))
            .put("mods", mods)
            .toString(2)
    }

    fun toShnXml(metadata: Metadata, patches: List<Patch>): String {
        val xml = StringBuilder()
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        xml.append("<Trainer Game=\"${xmlEscape(metadata.gameName)}\" Moder=\"${xmlEscape(metadata.author.ifBlank { "PS5 Cheat Finder" })}\" Cusa=\"${xmlEscape(metadata.titleId.uppercase())}\" Version=\"${xmlEscape(metadata.version)}\" Process=\"${xmlEscape(metadata.process)}\">\n")
        xml.append("  <Genres Name=\"Other\" />\n  <Items />\n")
        patches.forEach { p ->
            xml.append("  <Cheat Text=\"${xmlEscape(p.name)}\" Description=\"${xmlEscape(p.description)}\">\n")
            xml.append("    <Cheatline>\n")
            xml.append("      <Section>0</Section>\n")
            xml.append("      <Offset>${normalizeOffset(p.offset)}</Offset>\n")
            xml.append("      <ValueOn>${hyphenHex(p.valueOn)}</ValueOn>\n")
            xml.append("      <ValueOff>${hyphenHex(p.valueOff)}</ValueOff>\n")
            xml.append("    </Cheatline>\n  </Cheat>\n")
        }
        xml.append("</Trainer>\n")
        return xml.toString()
    }

    /** MC4 is base64(AES-256-CBC(PKCS#7-padded UTF-8 SHN XML)). */
    private fun encryptShnToMc4(xml: String): ByteArray {
        val key = "304c6528f659c766110239a51cl5dd9c".toByteArray(StandardCharsets.US_ASCII)
        val iv = "u@}kzW2u[u(8DWar".toByteArray(StandardCharsets.US_ASCII)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val encrypted = cipher.doFinal(xml.toByteArray(StandardCharsets.UTF_8))
        return Base64.encode(encrypted, Base64.NO_WRAP)
    }

    private fun normalizeHex(value: String): String =
        value.replace("0x", "", ignoreCase = true).replace(Regex("[\\s:-]"), "").uppercase()

    private fun normalizeOffset(value: String): String =
        value.trim().removePrefix("0x").removePrefix("0X").uppercase()

    private fun hyphenHex(value: String): String =
        normalizeHex(value).chunked(2).joinToString("-")

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", "&apos;")
}
