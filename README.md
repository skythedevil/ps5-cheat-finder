# PS5 Cheat Finder

Android app for manual memory scanning and cheat-table management with a PS5 running ps5debug-NG. Intended for offline, single-player testing only.

## Build APK
Open **Actions → Build APK**. The workflow builds a debug APK and publishes it as the `PS5CheatFinder-debug` artifact.

## Requirements
- Jailbroken PS5 with ps5debug-NG running
- Android phone and PS5 on the same Wi-Fi
- Android 8.0 or later

## Features
- Connect to PS5 by IP address
- Find the running game process
- Exact first scan and comparative next scans
- Integer, float and double value types
- Cheat table, write values, freeze values
- Save/load cheat tables as JSON
- Optional AI helper (requires an Anthropic API key)

## Not yet supported
Unknown initial-value scans, AOB searches, pointer scans, hex viewer/disassembler, and verified MC4/SHN export.