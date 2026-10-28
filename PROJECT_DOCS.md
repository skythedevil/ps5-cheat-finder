# PS5 Cheat Finder — Project Documentation

## Goal
Android app for manual memory scanning against ps5debug-NG, cheat-table management, value writing/freezing, and an optional AI helper. Intended for offline, single-player use.

## Features implemented in source
- Connect to a PS5 IP on TCP port 744
- Find the `eboot.bin` process
- Exact first scan and next scans (exact, bigger, smaller, increased, decreased, changed, unchanged)
- Integer, float and double value types
- Cheat table with freeze/unfreeze, JSON save/load, write to results
- Optional Anthropic AI helper (requires user-provided API key)

## Roadmap
Unknown-value snapshots, AOB searches, pointer scans, hex/disassembly, and verified MC4/SHN export remain unsupported. The protocol implementation and app still need testing against a real PS5 and ps5debug-NG version.

## Build
GitHub Actions uses Java 17, Gradle 8.9 and Android Gradle Plugin 8.5.2 to build `assembleDebug`; the APK is uploaded as `PS5CheatFinder-debug`.

## Important
Do not assume the protocol implementation has been validated simply because the APK compiles. Test read-only connection/process listing first and use only offline single-player games. Never expose an API key in source control.
