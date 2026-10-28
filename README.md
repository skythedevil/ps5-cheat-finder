# PS5 Cheat Finder

Android app for manual memory scanning and cheat-table management with a PS5 running ps5debug-NG. Intended for offline, single-player testing only.

## Build APK
Open **Actions → Build APK**. The workflow builds a debug APK and publishes it as the `PS5CheatFinder-debug` artifact.

## Requirements
- Jailbroken PS5 with ps5debug-NG running
- Android phone and PS5 on the same Wi-Fi
- Android 8.0 or later

## Features
- Connect to PS5 by IP address and find the running game process
- Exact first scan and comparative next scans
- Integer, float and double value types
- Cheat table, write values, freeze values, save/load JSON
- Multi-cheat studio: name multiple cheats, remove draft entries, and export the collection once as GoldHEN/etaHEN JSON, SHN, or MC4
- Green/red UI theme
- AI-assisted cheat-file drafting from the selected provider; AI output is treated as an unverified draft and must be checked independently
- Optional AI help through Gemini, Claude, OpenAI, Groq, OpenRouter, or another OpenAI-compatible service

## Set up Gemini AI (free tier may be available)
1. Open [Google AI Studio](https://aistudio.google.com/) and create an API key.
2. Open PS5 Cheat Finder and scroll to **7. AI help (optional)**.
3. Tap the **Provider** button until it shows **Gemini (free key)**.
4. Paste your key into **API key** and leave the suggested model, or enter a model name currently supported by Google.
5. Type what you are trying to find in the game, then tap **Ask AI**.

Google's free-tier availability and usage limits can change. Model names also change. If you get an error, check the model list and limits on Google AI Studio, confirm the key is valid, and try again later if you reached a quota.

## Other providers
- **Claude:** create a key in the [Anthropic Console](https://console.anthropic.com/). Billing may need to be enabled. Select Claude, enter the key, and check the current model name if the default is unavailable.
- **OpenAI:** use a key from the [OpenAI platform](https://platform.openai.com/api-keys), base URL `https://api.openai.com/v1`, and a model available to your account.
- **Groq:** use a key from [Groq Console](https://console.groq.com/keys), base URL `https://api.groq.com/openai/v1`, and a model listed by Groq.
- **OpenRouter:** use a key from [OpenRouter](https://openrouter.ai/keys), base URL `https://openrouter.ai/api/v1`, and a model listed by OpenRouter.

Tap the Provider button to cycle through Gemini, Claude, and OpenAI-compatible. For the OpenAI-compatible option, set the base URL and model for the service you chose. Free models and limits vary by provider and may change.

**Key privacy:** keys are masked while typing and kept only in app memory while the app is open. They are not saved to the cheat-table file or any app file and are not written to app logs. Closing the app clears the key. The key is sent to the provider you selected to authorize the AI request.

## Important limitations
Unknown initial-value scans, AOB searches, pointer scans, and a hex viewer/disassembler are not implemented. Export formatting is implemented, but the generated files and AI-suggested offsets have not been validated on a real PS5 for every game/version. AI may not know rare cheats such as double jump and must never be treated as an authoritative source for offsets or restore bytes. Use only verified patch bytes for the exact game build, and back up saves before testing.
