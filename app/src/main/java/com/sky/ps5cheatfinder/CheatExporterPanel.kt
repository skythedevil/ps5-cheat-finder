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

/** UI to build GoldHEN/etaHEN JSON, SHN (XML), and MC4 cheat files. */
@Composable
fun CheatExporterPanel(onLog: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var gameName by remember { mutableStateOf("") }
    var titleId by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("01.00") }
    var process by remember { mutableStateOf("eboot.bin") }
    var author by remember { mutableStateOf("PS5 Cheat Finder") }
    var cheatName by remember { mutableStateOf("New cheat") }
    var description by remember { mutableStateOf("") }
    var offset by remember { mutableStateOf("") }
    var valueOn by remember { mutableStateOf("") }
    var valueOff by remember { mutableStateOf("") }
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
                    onLog("Exported $pendingName (${bytes.size} bytes). Verify title ID, version, offsets and ON/OFF bytes before use.")
                } catch (e: Exception) {
                    onLog("Export failed: ${e.message ?: "unknown error"}")
                } finally {
                    pendingBytes = null
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text("8. Cheat file builder (GoldHEN / etaHEN)", style = MaterialTheme.typography.titleMedium)
        Text("Build a cheat definition from known patch bytes. A scanned number or freeze entry alone is not a reversible patch.")
        OutlinedTextField(gameName, { gameName = it }, label = { Text("Game name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(titleId, { titleId = it }, label = { Text("Title ID (e.g. PPSA01342)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(version, { version = it }, label = { Text("Exact game version (e.g. 01.004.000)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(process, { process = it }, label = { Text("Process (usually eboot.bin)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(author, { author = it }, label = { Text("Cheat author / credit") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        Spacer(Modifier.height(8.dp))
        Text("Cheat patches (${patches.size})", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(cheatName, { cheatName = it }, label = { Text("Cheat name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(offset, { offset = it }, label = { Text("Hex offset (e.g. 60D3000)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(valueOn, { valueOn = it }, label = { Text("Bytes when ON (e.g. 90 90 90)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(valueOff, { valueOff = it }, label = { Text("Bytes when OFF / restore (same length)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                try {
                    val patch = CheatFormats.Patch(cheatName.trim(), description.trim(), offset.trim(), valueOn, valueOff)
                    CheatFormats.validate(
                        CheatFormats.Metadata(gameName.ifBlank { "Draft" }, titleId.ifBlank { "PPSA00000" }, version, process, author),
                        listOf(patch)
                    )
                    patches.add(patch)
                    cheatName = "New cheat ${patches.size + 1}"
                    description = ""
                    offset = ""
                    valueOn = ""
                    valueOff = ""
                    onLog("Added cheat patch: ${patch.name}")
                } catch (e: Exception) {
                    onLog("Cannot add patch: ${e.message}")
                }
            }) { Text("Add patch") }
            Button(onClick = {
                patches.clear()
                onLog("Cleared draft patches.")
            }) { Text("Clear patches") }
        }
        patches.forEachIndexed { i, patch ->
            Text("${i + 1}. ${patch.name} — offset ${patch.offset}, ${patch.valueOn.length} ON characters")
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = { formatIndex = (formatIndex + 1) % formats.size }) {
            Text("Output format: .${format.extension} (tap to change)")
        }
        Button(onClick = {
            try {
                val metadata = CheatFormats.Metadata(gameName.trim(), titleId.trim(), version.trim(), process.trim(), author.trim())
                val bytes = CheatFormats.export(format, metadata, patches.toList())
                pendingBytes = bytes
                pendingName = CheatFormats.cheatFileName(metadata.titleId.uppercase(), metadata.version, format.extension)
                createDocument.launch(pendingName)
            } catch (e: Exception) {
                onLog("Export validation failed: ${e.message ?: "unknown error"}")
            }
        }) { Text("Export .${format.extension}") }
        Text("JSON is plaintext; SHN is XML; MC4 is encrypted/base64 SHN. Only export known, reversible patches for the exact game build.")
    }
}
