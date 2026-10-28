# PS5 Cheat Finder — Project Documentation

## Goal
Android app for manual memory scanning against ps5debug-NG, cheat-table management, value writing/freezing, and AI help for offline, single-player games.

## Features
- Connect to a PS5 IP on TCP port 744 and look for the `eboot.bin` process
- Exact first scan and comparative next scans (exact, bigger, smaller, increased, decreased, changed, unchanged)
- Integer, float and double value types
- Cheat table with write, freeze/unfreeze, JSON save/load
- AI provider selector for Gemini, Claude, and OpenAI-compatible services
- Optional AI prompt helps plan the next value scan

## AI providers and API keys
- **Gemini:** Get a key from Google AI Studio at https://aistudio.google.com/. A free tier may be available, subject to Google's current usage limits.
- **Claude:** Get an API key from the Anthropic Console at https://console.anthropic.com/. API billing may need to be enabled.
- **OpenAI-compatible:** Supports OpenAI, Groq, OpenRouter and other compatible endpoints. Enter the provider's API key, model name, and base URL. Examples: OpenAI `https://api.openai.com/v1`, Groq `https://api.groq.com/openai/v1`, OpenRouter `https://openrouter.ai/api/v1`.
- Model names and provider limits change. If a request fails, check the provider's current docs and model list, plus your key, quota, and base URL.
- API keys are kept in app memory while the app is open. They are masked in the input and are not saved to the cheat-table file or any app file. Closing the app clears them. The key is sent to the selected provider to authenticate the request.

## Build
GitHub Actions uses Java 17, Gradle 8.9 and Android Gradle Plugin 8.5.2 to build `assembleDebug`; the APK is uploaded as `PS5CheatFinder-debug`.

## Change log
### v0.4
- Added Gemini, Claude, and OpenAI-compatible AI providers with editable model names.
- Added a custom base URL for OpenAI-compatible APIs, so users can configure OpenAI, Groq, OpenRouter, or similar services.
- Added a question field to tell the AI what the user is trying to find.
- API keys are password-masked and held only in app memory; they are not written to files or logs.
- Added network timeouts and clearer error replies for failed AI requests.
- Updated the AI response handling to collect text parts from providers.

## Roadmap
Unknown-value snapshots, AOB searches, pointer scans, hex/disassembly, image selection in the app, and verified MC4/SHN export remain unsupported. The protocol implementation and app still need testing against a real PS5 and ps5debug-NG version.

## Important
A successful APK build does not prove the PS5 protocol works on every firmware/version. Test read-only connection and process listing first. Use only offline single-player games. Never put API keys in source control.
