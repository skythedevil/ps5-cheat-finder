package com.sky.ps5cheatfinder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Multi-cheat builder for GoldHEN/etaHEN. Each named entry can contain one or more patch rows. */
@Composable
fun CheatExporterPanel(aiSettings: AiSettings?, onLog: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var gameName by remember { mutableStateOf("") }
    var titleId by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("01.00") }
    var process by remember { mutableStateOf("eboot.bin") }
    var author by remember { mutableStateOf("PS5 Cheat Finder") }
    var cheatName by remember { mutableStateOf("Maximum Health") }
    var description by remember { mutableStateOf("") }
    var offset by remember { mutableStateOf("") }
    var valueOn by remember { mutableStateOf("") }
    var valueOff by remember { mutableStateOf("") }
    var desiredCheat by remember { mutableStateOf("Create a double jump cheat") }
    var aiBusy by remember { mutableStateOf(false) }
    var aiStatus by remember { mutableStateOf("") }
    var formatIndex by remember { mutableStateOf(0) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingName by remember { mutableStateOf("cheats.json") }
    val patches = remember { mutableStateListOf<CheatFormats.Patch>() }
    val formats = CheatFormats.Format.entries
    val format = formats[formatIndex]

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = pendingBytes
        if (uri == null || bytes == null) {
            onLog("Export cancelled.")
        } else {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                            ?: error("Could not open the destination file.")
                    }
                    onLog("Exported $pendingName with ${patches.size} named cheat entries (${bytes.size} bytes). Verify title ID, version, offsets and ON/OFF bytes before use.")
                } catch (e: Exception) {
                    onLog("Export failed: ${e.message ?: "unknown error"}")
                } finally {
                    pendingBytes = null
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("8. Cheat file studio", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text("Add as many named cheats as you need, then export the whole collection once. Each entry needs a verified offset and reversible ON/OFF bytes.")
        OutlinedTextField(gameName, { gameName = it }, label = { Text("Game name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(titleId, { titleId = it }, label = { Text("Title ID (e.g. PPSA01342)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(version, { version = it }, label = { Text("Exact game version (e.g. 01.004.000)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(process, { process = it }, label = { Text("Process (usually eboot.bin)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(author, { author = it }, label = { Text("Author / credit") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        Spacer(Modifier.height(8.dp))
        Text("AI cheat assistant", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        OutlinedTextField(desiredCheat, { desiredCheat = it }, label = { Text("Describe the cheat you want") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                val settings = aiSettings
                if (settings == null) {
                    aiStatus = "Set an AI provider and API key in section 7 first."
                    return@Button
                }
                if (gameName.isBlank() || titleId.isBlank() || version.isBlank()) {
                    aiStatus = "Enter the game name, exact Title ID, and game version first."
                    return@Button
                }
                if (desiredCheat.isBlank()) {
                    aiStatus = "Describe the cheat first."
                    return@Button
                }
                aiBusy = true
                aiStatus = "Asking AI for a structured draft…"
                val prompt = """
You help create PS5 GoldHEN/etaHEN cheat-file drafts for offline single-player use.
Game: $gameName
Title ID: $titleId
Exact version: $version
Process: $process
Requested cheat: $desiredCheat

Return ONLY valid JSON with this shape:
{"patches":[{"name":"Readable cheat name","description":"short description","offset":"hex offset","on":"90 90","off":"original bytes"}],"note":"brief confidence/source note"}
IMPORTANT: Never invent offsets, machine code, or restore bytes. Include only exact patch data you genuinely know for this exact game ID and version. If you do not know verified values, return {"patches":[],"note":"No verified patch data known; explain what memory scan or disassembly evidence is needed."}. ON and OFF byte strings must be same length. Do not claim a cheat works unless its data is verified. Do not produce scripts or executable payloads.
                """.trimIndent()
                scope.launch {
                    try {
                        val reply = withContext(Dispatchers.IO) { AiClient().ask(settings, prompt) }
                        val jsonText = reply.substring(reply.indexOf('{').coerceAtLeast(0), reply.lastIndexOf('}') + 1)
                        val obj = JSONObject(jsonText)
                        val arr = obj.optJSONArray("patches") ?: JSONArray()
                        var added = 0
                        for (i in 0 until arr.length()) {
                            val p = arr.optJSONObject(i) ?: continue
                            val patch = CheatFormats.Patch(
                                p.optString("name", "").trim(),
                                p.optString("description", "").trim(),
                                p.optString("offset", "").trim(),
                                p.optString("on", ""),
                                p.optString("off", "")
                            )
                            try {
                                CheatFormats.validate(
                                    CheatFormats.Metadata(gameName, titleId, version, process, author),
                                    listOf(patch)
                                )
                                if (patches.none { it.name.equals(patch.name, ignoreCase = true) && it.offset.equals(patch.offset, ignoreCase = true) }) {
                                    patches.add(patch)
                                    added++
                                }
                            } catch (_: Exception) { /* Ignore invalid AI-generated rows. */ }
                        }
                        aiStatus = if (added > 0) {
                            "Added $added AI draft(s). Verify every offset and byte against a trusted source before exporting."
                        } else {
                            "No usable verified patch was returned. ${obj.optString("note", "AI may not know this exact game/version.")}"
                        }
                        onLog("AI cheat draft: $aiStatus")
                    } catch (e: Exception) {
                        aiStatus = "AI response could not be parsed: ${e.message ?: "unknown error"}"
                        onLog(aiStatus)
                    } finally {
                        aiBusy = false
                    }
                }
            },
            enabled = !aiBusy
        ) { Text(if (aiBusy) "Working…" else "Ask AI to draft cheat") }
        if (aiStatus.isNotBlank()) Text(aiStatus, style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(8.dp))
        Text("Add a known patch manually", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(cheatName, { cheatName = it }, label = { Text("Cheat name (e.g. No Reload)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(offset, { offset = it }, label = { Text("Hex offset (e.g. 60D3000)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(valueOn, { valueOn = it }, label = { Text("ON bytes (e.g. 90 90 90)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(valueOff, { valueOff = it }, label = { Text("OFF / restore bytes (same length)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                try {
                    val patch = CheatFormats.Patch(cheatName.trim(), description.trim(), offset.trim(), valueOn, valueOff)
                    CheatFormats.validate(CheatFormats.Metadata(gameName.ifBlank { "Draft" }, titleId.ifBlank { "PPSA00000" }, version, process, author), listOf(patch))
                    require(patches.none { it.name.equals(patch.name, ignoreCase = true) && it.offset.equals(patch.offset, ignoreCase = true) }) { "That named cheat/offset already exists." }
                    patches.add(patch)
                    cheatName = ""
                    description = ""
                    offset = ""
                    valueOn = ""
                    valueOff = ""
                    onLog("Added named cheat: ${patch.name}")
                } catch (e: Exception) {
                    onLog("Cannot add cheat: ${e.message}")
                }
            }) { Text("Add cheat") }
            Button(onClick = { patches.clear(); onLog("Cleared all draft cheats.") }) { Text("Clear all") }
        }

        Text("Draft collection · ${patches.size} cheats", style = MaterialTheme.typography.titleMedium)
        if (patches.isEmpty()) Text("No cheats added yet.")
        patches.forEachIndexed { i, patch ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("${i + 1}. ${patch.name}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text("Offset 0x${patch.offset} · ON ${patch.valueOn} · OFF ${patch.valueOff}", style = MaterialTheme.typography.bodySmall)
                if (patch.description.isNotBlank()) Text(patch.description, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { patches.removeAt(i) }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Remove") }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = { formatIndex = (formatIndex + 1) % formats.size }) {
            Text("Output format: .${format.extension} (tap to change)")
        }
        Button(
            onClick = {
                try {
                    val metadata = CheatFormats.Metadata(gameName.trim(), titleId.trim(), version.trim(), process.trim(), author.trim())
                    val bytes = CheatFormats.export(format, metadata, patches.toList())
                    pendingBytes = bytes
                    pendingName = CheatFormats.cheatFileName(metadata.titleId.uppercase(), metadata.version, format.extension)
                    createDocument.launch(pendingName)
                } catch (e: Exception) {
                    onLog("Export validation failed: ${e.message ?: "unknown error"}")
                }
            },
            enabled = patches.isNotEmpty()
        ) { Text("Export all ${patches.size} cheats as .${format.extension}") }
        Text("AI can draft a structure, but it may be wrong or return no data for rare cheats such as double jump. Never trust generated offsets without independent verification.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
