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
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val path = request.getOrNull(1)?.substringBefore("?") ?: "/"
            val isApi = path == "/api/state"
            val body = if (isApi) DashboardData.json() else PAGE
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            val type = if (isApi) "application/json; charset=utf-8" else "text/html; charset=utf-8"
            val headers = "HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\n\r\n"
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
<title>PS5 Cheat Finder — Live Dashboard</title>
<style>
:root{color-scheme:dark;--bg:#0b1110;--panel:#121c19;--line:#293b34;--text:#f4faf6;--muted:#b5c8be;--green:#4ade80;--red:#fb7185}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:16px/1.5 system-ui,Segoe UI,Arial,sans-serif}
header{padding:24px clamp(16px,4vw,48px);border-bottom:1px solid var(--line);background:#101916}
h1{font-size:clamp(24px,3vw,36px);margin:0 0 4px}p{margin:0;color:var(--muted)}main{padding:24px clamp(16px,4vw,48px);max-width:1400px;margin:auto}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:14px;margin-bottom:22px}.card{background:var(--panel);border:1px solid var(--line);border-radius:14px;padding:18px}.label{font-size:12px;text-transform:uppercase;letter-spacing:.08em;color:var(--muted)}.metric{font-size:24px;font-weight:750;overflow-wrap:anywhere}
.panel{background:var(--panel);border:1px solid var(--line);border-radius:14px;overflow:hidden} .panelhead{padding:18px 20px;border-bottom:1px solid var(--line);display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;align-items:center}
table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:14px 20px;border-bottom:1px solid var(--line);font-variant-numeric:tabular-nums}th{font-size:12px;text-transform:uppercase;letter-spacing:.07em;color:var(--muted)}td{font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:15px}.current{color:var(--green);font-weight:700}.changed{color:var(--green)}.empty{padding:30px;color:var(--muted)}.status{display:inline-flex;align-items:center;gap:8px}.dot{width:9px;height:9px;background:var(--green);border-radius:50%}.foot{margin-top:16px;font-size:13px;color:var(--muted)}@media(max-width:650px){th,td{padding:11px 10px;font-size:12px}.metric{font-size:20px}}
</style></head><body>
<header><h1>PS5 Cheat Finder</h1><p>Live memory results · local network dashboard</p></header>
<main><section class="cards">
<div class="card"><div class="label">Connection / scan</div><div id="status" class="metric">Loading…</div></div>
<div class="card"><div class="label">Game PID</div><div id="pid" class="metric">—</div></div>
<div class="card"><div class="label">Value type</div><div id="type" class="metric">—</div></div>
<div class="card"><div class="label">Candidate count</div><div id="count" class="metric">0</div></div>
</section>
<section class="panel"><div class="panelhead"><div><strong>Scan results</strong><p>Current values refresh automatically when fewer than 10 results remain.</p></div><div class="status"><span class="dot"></span><span id="updated">Connecting…</span></div></div>
<div style="overflow-x:auto"><table><thead><tr><th>Address</th><th>Last scan value</th><th>Current value</th></tr></thead><tbody id="rows"><tr><td colspan="3" class="empty">Waiting for the Android app…</td></tr></tbody></table></div></section>
<p class="foot">Read-only dashboard. Use the Android app to connect, scan, filter results, write values, or freeze entries. Keep the phone and laptop on the same Wi-Fi network.</p></main>
<script>
const el=id=>document.getElementById(id);let previous={};
async function refresh(){try{const r=await fetch('/api/state',{cache:'no-store'});const d=await r.json();el('status').textContent=d.status||'—';el('pid').textContent=d.pid??'—';el('type').textContent=d.type||'—';el('count').textContent=d.count??0;
const rows=d.rows||[];if(!rows.length){el('rows').innerHTML='<tr><td colspan="3" class="empty">No candidates to display yet. Connect, find the game and run First scan in the Android app.</td></tr>'}
else{el('rows').innerHTML=rows.map(x=>{const old=previous[x.address];const changed=old!==undefined&&old!==x.current;previous[x.address]=x.current;return '<tr><td>'+safe(x.address)+'</td><td>'+safe(x.baseline)+'</td><td class="current'+(changed?' changed':'')+'">'+safe(x.current)+(changed?' ↑':'')+'</td></tr>'}).join('')}
el('updated').textContent='Updated '+new Date().toLocaleTimeString()}catch(e){el('status').textContent='Waiting for app';el('updated').textContent='Retrying…'}}
function safe(s){return String(s??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
refresh();setInterval(refresh,1000);
</script></body></html>"""
}
