package com.sky.ps5cheatfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = lightColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF167D45),
                onPrimary = androidx.compose.ui.graphics.Color.White,
                secondary = androidx.compose.ui.graphics.Color(0xFFC62828),
                onSecondary = androidx.compose.ui.graphics.Color.White,
                error = androidx.compose.ui.graphics.Color(0xFFB71C1C),
                onError = androidx.compose.ui.graphics.Color.White,
                background = androidx.compose.ui.graphics.Color(0xFFF5F8F5),
                surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
            )
            MaterialTheme(colorScheme = colors) { AppScreen() }
        }
    }
}

private const val GAME_PROCESS = "eboot.bin"
private const val CHEAT_FILE_NAME = "cheats.json"

@Composable
fun AppScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var ip by remember { mutableStateOf("192.168.1.") }
    var status by remember { mutableStateOf("Not connected") }
    var conn by remember { mutableStateOf<Ps5Connection?>(null) }
    var pid by remember { mutableStateOf<Int?>(null) }
    var scanner by remember { mutableStateOf<MemoryScanner?>(null) }
    var typeIndex by remember { mutableIntStateOf(4) }
    var modeIndex by remember { mutableIntStateOf(0) }
    var searchText by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(0) }
    var aobPattern by remember { mutableStateOf("48 8B ?? ??") }
    var aobRadius by remember { mutableStateOf("128") }
    var aobResults by remember { mutableStateOf<List<Long>>(emptyList()) }
    var logText by remember { mutableStateOf("") }
    val table = remember { mutableStateListOf<CheatEntry>() }
    val types = ValueType.entries
    val modes = CompareMode.entries
    val type = types[typeIndex]
    val mode = modes[modeIndex]

    // AI settings live only in Compose memory; they are not written to files.
    val aiTypes = AiProviderType.entries
    var aiIndex by remember { mutableIntStateOf(0) }
    var aiKey by remember { mutableStateOf("") }
    var aiModel by remember { mutableStateOf(AiProviderType.GEMINI.defaultModel) }
    var aiBaseUrl by remember { mutableStateOf("https://api.openai.com/v1") }
    var aiQuestion by remember { mutableStateOf("") }
    val aiType = aiTypes[aiIndex]

    LaunchedEffect(conn, pid) {
        while (true) {
            val c = conn
            val p = pid
            if (c != null && p != null && table.any { it.lockedValue != null }) {
                withContext(Dispatchers.IO) { Freezer.applyLocks(c, p, table.toList()) }
            }
            delay(300)
        }
    }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("PS5 CHEAT FINDER", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text("Status: $status")
        Spacer(Modifier.height(12.dp))
        Text("1. Connect", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(ip, { ip = it }, label = { Text("PS5 IP address") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            scope.launch {
                val c = Ps5Connection(ip)
                val ok = withContext(Dispatchers.IO) { c.connect() }
                if (ok) { conn = c; status = "Connected" } else status = "Could not connect (is ps5debug-NG running?)"
            }
        }) { Text("Connect") }
        Button(onClick = {
            val c = conn
            if (c == null) { logText += "Connect first.\n"; return@Button }
            scope.launch {
                val found = withContext(Dispatchers.IO) { c.findPid(GAME_PROCESS) }
                if (found != null) { pid = found; logText += "Found $GAME_PROCESS, pid $found\n" }
                else logText += "$GAME_PROCESS not found. Start the game first.\n"
            }
        }) { Text("Find game") }

        Spacer(Modifier.height(12.dp))
        Text("2. Search", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { typeIndex = (typeIndex + 1) % types.size }) { Text("Value type: ${type.label} (tap to change)") }
        Button(onClick = { modeIndex = (modeIndex + 1) % modes.size }) { Text("Next-scan compare: ${mode.label} (tap to change)") }
        OutlinedTextField(searchText, { searchText = it }, label = { Text("Value you see in game") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val c = conn; val p = pid
            if (c == null || p == null) { logText += "Connect and find the game first.\n"; return@Button }
            scope.launch {
                val s = MemoryScanner(c, p, type)
                val n = withContext(Dispatchers.IO) { s.firstScan(searchText) }
                if (n < 0) { logText += "Type a valid number first.\n"; return@launch }
                scanner = s; count = n; logText += "First scan (exact): $n results\n"
            }
        }) { Text("First scan (exact value)") }
        Button(onClick = {
            val s = scanner
            if (s == null) { logText += "Do a first scan first.\n"; return@Button }
            scope.launch {
                val n = withContext(Dispatchers.IO) { s.nextScan(mode, searchText) }
                if (n < 0) { logText += "This compare needs a valid number in the box.\n"; return@launch }
                count = n; logText += "Next scan (${mode.label}): $n results\n"
            }
        }) { Text("Next scan") }

        Spacer(Modifier.height(12.dp))
        Text("3. Results: $count", style = MaterialTheme.typography.titleMedium)
        scanner?.candidates?.take(20)?.forEach { cand ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("0x" + cand.address.toString(16) + "  last: " + type.toText(cand.lastBytes), modifier = Modifier.weight(1f))
                Button(onClick = { table.add(CheatEntry("Found " + cand.address.toString(16), cand.address, type)) }) { Text("Add") }
            }
        }
        if (count > 20) Text("Showing first 20 of $count")

        Spacer(Modifier.height(12.dp))
        Text("4. AOB signature search (near scan results)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        Text("Searches for a byte pattern in a small memory window around your existing value-scan results. This is not a whole-process scan. Use ?? for wildcard bytes.")
        OutlinedTextField(aobPattern, { aobPattern = it }, label = { Text("Pattern (e.g. 48 8B ?? 01)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(aobRadius, { aobRadius = it }, label = { Text("Radius around each candidate (1–512 bytes)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = {
            val c = conn; val p = pid; val s = scanner
            val radius = aobRadius.toIntOrNull()
            if (c == null || p == null || s == null) { logText += "Connect, find the game, and run a value scan first.\\n"; return@Button }
            if (radius == null || radius !in 1..512) { logText += "Radius must be between 1 and 512 bytes.\\n"; return@Button }
            scope.launch {
                try {
                    val seeds = s.candidates.map { it.address }
                    val results = withContext(Dispatchers.IO) { AobScanner.scanNearCandidates(c, p, seeds, aobPattern, radius) }
                    aobResults = results
                    logText += "AOB search found ${results.size} matches near up to 300 anchors.\\n"
                } catch (e: Exception) {
                    logText += "AOB search failed: ${e.message ?: "unknown error"}\\n"
                }
            }
        }) { Text("Search pattern near candidates") }
        if (aobResults.isNotEmpty()) {
            Text("AOB matches: ${aobResults.size}", style = MaterialTheme.typography.titleSmall)
            aobResults.take(20).forEach { address ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("0x" + address.toString(16), modifier = Modifier.weight(1f))
                    Button(onClick = {
                        if (table.none { it.address == address }) table.add(CheatEntry("AOB " + address.toString(16), address, type))
                        else logText += "That address is already in the cheat table.\\n"
                    }) { Text("Add") }
                }
            }
            if (aobResults.size > 20) Text("Showing first 20 of ${aobResults.size} matches.")
        }

        Spacer(Modifier.height(12.dp))
        Text("5. Change value", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(newValue, { newValue = it }, label = { Text("New value (used by Freeze and Write)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val c = conn; val p = pid; val s = scanner
            if (c == null || p == null || s == null) { logText += "Do a scan first.\n"; return@Button }
            scope.launch {
                val n = withContext(Dispatchers.IO) { s.writeToAll(newValue) }
                if (n < 0) logText += "Type a valid new value first.\n" else logText += "Wrote $newValue to $n addresses\n"
            }
        }) { Text("Write new value to all results") }

        Spacer(Modifier.height(12.dp))
        Text("5. Cheat table", style = MaterialTheme.typography.titleMedium)
        if (table.isEmpty()) Text("Empty. Add addresses from the results above.")
        table.forEachIndexed { index, entry ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text("${entry.label}  0x${entry.address.toString(16)}  (${entry.type.label})")
                Text(if (entry.lockedValue == null) "Not frozen" else "Frozen at ${entry.lockedValue}")
                Row {
                    Button(onClick = {
                        if (entry.lockedValue != null) table[index] = entry.copy(lockedValue = null)
                        else if (newValue.isBlank() || entry.type.encode(newValue) == null) logText += "Type a valid New value first.\n"
                        else table[index] = entry.copy(lockedValue = newValue)
                    }) { Text(if (entry.lockedValue == null) "Freeze" else "Unfreeze") }
                    Button(onClick = { table.removeAt(index) }) { Text("Delete") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("6. Save and load", style = MaterialTheme.typography.titleMedium)
        Row {
            Button(onClick = {
                try { File(context.filesDir, CHEAT_FILE_NAME).writeText(CheatFile.toJson(table.toList())); logText += "Saved ${table.size} entries\n" }
                catch (e: Exception) { logText += "Save failed: ${e.message}\n" }
            }) { Text("Save table") }
            Button(onClick = {
                try {
                    val file = File(context.filesDir, CHEAT_FILE_NAME)
                    if (!file.exists()) { logText += "No saved file yet.\n"; return@Button }
                    table.clear(); table.addAll(CheatFile.fromJson(file.readText())); logText += "Loaded ${table.size} entries\n"
                } catch (e: Exception) { logText += "Load failed: ${e.message}\n" }
            }) { Text("Load table") }
        }

        // 7. AI help (optional, bring your own provider key).
        Spacer(Modifier.height(12.dp))
        Text("7. AI help (optional)", style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
            aiIndex = (aiIndex + 1) % aiTypes.size
            aiModel = aiTypes[(aiIndex + 1) % aiTypes.size].defaultModel
        }) { Text("Provider: ${aiType.label} (tap to change)") }
        OutlinedTextField(
            value = aiKey,
            onValueChange = { aiKey = it },
            label = { Text("API key (kept only while app is open)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(aiModel, { aiModel = it }, label = { Text("Model name") }, modifier = Modifier.fillMaxWidth())
        if (aiType == AiProviderType.OPENAI_COMPATIBLE) {
            OutlinedTextField(aiBaseUrl, { aiBaseUrl = it }, label = { Text("Base URL (e.g. https://api.groq.com/openai/v1)") }, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(aiQuestion, { aiQuestion = it }, label = { Text("What are you trying to do?") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            if (aiKey.isBlank()) { logText += "Enter an API key first.\n"; return@Button }
            if (aiQuestion.isBlank()) { logText += "Describe what you want to do first.\n"; return@Button }
            val settings = AiSettings(aiType, aiKey.trim(), aiModel.trim(), aiBaseUrl.trim())
            val prompt = "This is for an offline single-player PS5 game cheat tool. " +
                "The user wants to: $aiQuestion. " +
                "Progress so far:\n$logText\n" +
                "Suggest the number to search for, what to change in game, and which next-scan type to use " +
                "(exact, bigger, smaller, increased, decreased, changed, unchanged). Answer in short numbered steps."
            logText += "Asking ${aiType.label}...\n"
            scope.launch {
                val reply = withContext(Dispatchers.IO) { AiClient().ask(settings, prompt) }
                logText += "AI: $reply\n"
            }
        }) { Text("Ask AI") }

        val exporterAiSettings = if (aiKey.isNotBlank()) AiSettings(aiType, aiKey.trim(), aiModel.trim(), aiBaseUrl.trim()) else null
        CheatExporterPanel(aiSettings = exporterAiSettings) { line -> logText += line + "\n" }

        Spacer(Modifier.height(12.dp))
        Text("Log:\n$logText")
    }
}
