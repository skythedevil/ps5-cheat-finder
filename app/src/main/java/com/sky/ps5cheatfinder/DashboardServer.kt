package com.sky.ps5cheatfinder

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

object DashboardServer {
    const val PORT = 8765
    private val running = AtomicBoolean(false)

    fun start() {
        if (!running.compareAndSet(false, true)) return
        Thread({
            try {
                ServerSocket(PORT, 20, java.net.InetAddress.getByName("0.0.0.0")).use { server ->
                    while (running.get()) {
                        try {
                            val client = server.accept()
                            Thread({ handle(client) }, "ps5-dashboard-client").start()
                        } catch (_: Exception) {
                            if (!running.get()) break
                        }
                    }
                }
            } catch (_: Exception) {
                running.set(false)
            }
        }, "ps5-dashboard-server").apply { isDaemon = true; start() }
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 3000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII))
            val request = reader.readLine().orEmpty().split(" ")
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", true)) contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
            }
            val path = request.getOrNull(1)?.substringBefore("?") ?: "/"
            val method = request.firstOrNull() ?: "GET"
            val isApi = path == "/api/state"
            val isAction = path == "/api/action" && method == "POST"
            if (isAction) {
                val bodyBytes = ByteArray(contentLength.coerceIn(0, 4096))
                var got = 0
                while (got < bodyBytes.size) {
                    val n = client.getInputStream().read(bodyBytes, got, bodyBytes.size - got)
                    if (n < 0) break
                    got += n
                }
                try {
                    val json = org.json.JSONObject(String(bodyBytes, 0, got, StandardCharsets.UTF_8))
                    DashboardData.enqueue(json.optString("action"), json.optString("value"))
                } catch (_: Exception) { }
            }
            val body = when {
                isApi -> DashboardData.json()
                isAction -> "{\\"queued\\":true}"
                else -> PAGE
            }
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            val type = if (isApi || isAction) "application/json; charset=utf-8" else "text/html; charset=utf-8"
            val status = if (isAction) "202 Accepted" else "200 OK"
            val headers = "HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: Content-Type\r\n\r\n"
            val out: OutputStream = client.getOutputStream()
            out.write(headers.toByteArray(StandardCharsets.US_ASCII))
            out.write(bytes)
            out.flush()
        }
    }

    fun localIp(context: Context): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val addresses = interfaces.nextElement().inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) return address.hostAddress ?: "PHONE-IP"
                }
            }
        } catch (_: Exception) { }
        return "PHONE-IP"
    }

    private val PAGE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>PS5 Cheat Finder — Control Dashboard</title>
<style>
:root{color-scheme:dark;--bg:#080d0c;--panel:#131c19;--line:#34473e;--text:#f7fff9;--muted:#d0ded5;--green:#4ade80;--red:#fb7185}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:18px/1.55 system-ui,Arial,sans-serif}
header{padding:24px clamp(16px,4vw,48px);border-bottom:1px solid var(--line);background:#101916}h1{font-size:clamp(27px,4vw,40px);margin:0}p{margin:4px 0;color:var(--muted)}main{padding:22px clamp(14px,4vw,44px);max-width:1450px;margin:auto}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:14px;margin-bottom:20px}.card,.panel{background:var(--panel);border:1px solid var(--line);border-radius:14px}.card{padding:18px}.label{font-size:14px;text-transform:uppercase;letter-spacing:.06em;color:var(--muted)}.metric{font-size:25px;font-weight:750;overflow-wrap:anywhere}
.panel{margin-bottom:18px;overflow:hidden}.panelhead{padding:17px 20px;border-bottom:1px solid var(--line)}.controls{display:flex;flex-wrap:wrap;gap:10px;padding:18px}.controls input,.controls select{background:#07100c;color:var(--text);border:1px solid #71877a;border-radius:9px;padding:12px;font:inherit;min-width:150px;flex:1}button{background:#176b3b;color:white;border:1px solid #4ade80;border-radius:9px;padding:12px 17px;font:inherit;font-weight:700;cursor:pointer}button.alt{background:#28352e;border-color:#71877a}button:disabled{opacity:.55}
table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:13px 18px;border-bottom:1px solid var(--line);font-variant-numeric:tabular-nums}th{color:var(--muted);font-size:14px;text-transform:uppercase}td{font-family:ui-monospace,Consolas,monospace;font-size:16px}.current{color:var(--green);font-weight:700}.empty{padding:24px;color:var(--muted)}#notice{padding:14px 18px;color:var(--green);min-height:32px}.foot{font-size:14px;color:var(--muted)}@media(max-width:650px){body{font-size:16px}th,td{padding:10px 8px;font-size:13px}.metric{font-size:21px}}
</style></head><body>
<header><h1>PS5 Cheat Finder</h1><p>Live dashboard · scan controls · process information</p></header>
<main><section class="cards">
<div class="card"><div class="label">Connection / status</div><div id="status" class="metric">Waiting for app</div></div>
<div class="card"><div class="label">Game process ID</div><div id="pid" class="metric">—</div></div>
<div class="card"><div class="label">Value type</div><div id="type" class="metric">—</div></div>
<div class="card"><div class="label">Candidates</div><div id="count" class="metric">0</div></div></section>
<section class="panel"><div class="panelhead"><strong>Game & scan controls</strong><p>Commands run through the Android app connected to your PS5.</p></div>
<div class="controls"><button onclick="act('find')">Find game</button><select id="typeSelect" aria-label="Value type"><option>1-byte unsigned</option><option>1-byte signed</option><option>2-byte unsigned</option><option>2-byte signed</option><option>4-byte unsigned</option><option>4-byte signed</option><option>8-byte signed</option><option>Float (decimal)</option><option>Double (decimal)</option></select><input id="value" placeholder="Value shown in game" inputmode="decimal"><button onclick="act('first')">First scan</button><select id="modeSelect" aria-label="Next scan comparison"><option>Exact value</option><option>Bigger than</option><option>Smaller than</option><option>Increased</option><option>Decreased</option><option>Changed</option><option>Unchanged</option></select><button onclick="act('next')">Next scan</button></div><div id="notice" role="status">Connect in the Android app first. This dashboard can send scan requests once the app is open.</div></section>
<section class="panel"><div class="panelhead"><strong>Scan results</strong><p>Live values refresh automatically when fewer than 10 candidates remain.</p></div><div style="overflow-x:auto"><table><thead><tr><th>Address</th><th>Baseline</th><th>Current value</th></tr></thead><tbody id="rows"><tr><td colspan="3" class="empty">No results yet.</td></tr></tbody></table></div></section>
<p class="foot">Keep your laptop and phone on the same trusted Wi-Fi network. Scanning and memory writes can affect game state; use offline single-player testing and verified addresses only.</p></main>
<script>
const el=id=>document.getElementById(id);let previous={};
async function act(action){const value=action==='first'||action==='next'?el('value').value:action==='type'?el('typeSelect').selectedIndex:action==='mode'?el('modeSelect').selectedIndex:'';try{const r=await fetch('/api/action',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action,value:String(value)})});if(!r.ok)throw Error('HTTP '+r.status);el('notice').textContent=({find:'Finding the game process…',first:'First scan queued…',next:'Next scan queued…'})[action]||'Request queued.'}catch(e){el('notice').textContent='Could not reach Android app: '+e.message}}
el('typeSelect').addEventListener('change',()=>act('type'));el('modeSelect').addEventListener('change',()=>act('mode'));
async function refresh(){try{const r=await fetch('/api/state',{cache:'no-store'});const d=await r.json();el('status').textContent=d.status||'—';el('pid').textContent=d.pid??'—';el('type').textContent=d.type||'—';el('count').textContent=d.count??0;el('notice').textContent=d.message||el('notice').textContent;
const rows=d.rows||[];if(!rows.length){el('rows').innerHTML='<tr><td colspan="3" class="empty">No candidates to display. Find the game and run First scan.</td></tr>'}
else{el('rows').innerHTML=rows.map(x=>{const old=previous[x.address];const changed=old!==undefined&&old!==x.current;previous[x.address]=x.current;return '<tr><td>'+safe(x.address)+'</td><td>'+safe(x.baseline)+'</td><td class="current">'+safe(x.current)+(changed?' • changed':'')+'</td></tr>'}).join('')}
}catch(e){el('status').textContent='Waiting for app'}}function safe(s){return String(s??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}refresh();setInterval(refresh,1000);
</script></body></html>"""
}
