<div align="center">

# TranslateAssist
### Floating, on-demand English -> Gujarati translation & transliteration overlay for WhatsApp (and compatible messaging apps)

</div>

TranslateAssist is a personal-use Android utility that overlays a draggable "Translate" button on top of WhatsApp (and select messaging apps). When tapped, it extracts ONLY the currently visible messages (stateless - nothing is stored) and produces a streaming, line-by-line Gujarati output using:

1. Best-effort online translation (auto/English -> Gujarati) via Google's translate endpoint
2. Live phonetic transliteration ("kem cho" -> "કેમ છો") via Google Input Tools (unofficial endpoint)

The UI shows incremental results almost immediately while the rest of the lines finish processing.

---

## ✨ Feature Highlights

- Draggable floating overlay button (tap vs drag detection)
- On‑demand extraction: processes only when you tap (no background polling)
- Multi‑app support: WhatsApp + common SMS/Messaging packages (`com.whatsapp`, Google Messages, AOSP/Samsung messaging)
- Smart accessibility parsing with heavy filtering (skips UI chrome, timestamps, buttons, metadata)
- Deduplicates and keeps only the most recent N (default 8) visible message texts
- Selection-aware (can prefer selected text if logic extended; base extraction currently stateless)
- Dual online pipeline per Latin-script line:
   * Translation (auto/English -> Gujarati) through `OnlineTranslator`
   * Transliteration (phonetic Latin -> Gujarati script) through Google Input Tools
   * Transliteration is preferred when present, then translation, then original text
- Automatic language detection & script inspection:
  * English (Latin only) → translate + transliterate
  * Gujarati script already → left as original
  * Other scripts → best‑effort translate or fallback to original
- Streaming popup overlay:
  * Loader appears instantly
  * Each line appended as soon as ready
  * Scroll auto-follows latest line
  * Copy All button (enabled after stream completes)
- Per-line long‑press copy: copies translation + transliteration (or original fallback)
- Copy All aggregated output to clipboard
- Process-wide translation controller and popup so overlay taps keep working even when `MainActivity` is gone
- Overlay self-healing through the accessibility service when the user previously left it enabled
- Safe online calls (short timeouts, silent failure fallback)
- Entire processing stateless (no persisted logs/messages)
- Works with Android 6.0+ (minSdk 23, targetSdk 34)
- Minimal, clearly scoped permissions

---

## 🏗 Architecture Overview

```
Overlay Tap
   ↓
OverlayClickHandler
   ↓
TranslateAccessibilityService (extract + filter visible texts, or queue until service reconnects)
   ↓ (unique, recent window, newline-joined)
TranslationController (process-wide engine + popup)
   ↓
TranslationEngine
   ├─ ML Kit language detection / script heuristics
   ├─ OnlineTranslator (auto/English -> Gujarati, network, optional)
   └─ Google Input Tools transliterator (network, optional)
        ↓ (per line: original + translation + transliteration + chosenMode)
Streaming UI (TranslationPopup + RecyclerView adapter)
   ↓
Clipboard (line long‑press or Copy All)
```

Key design principles:
- On‑demand: the accessibility service only extracts when the floating button is tapped
- Fail-soft: any failing line simply uses whichever artifact succeeded (transliteration -> translation -> original)
- Lifecycle-tolerant: the overlay tap path does not require an Activity to be alive
- Ephemeral: no storage, no analytics

---

## 📲 Build & Install (USB)

See [install.md](install.md) for Windows + USB cable build/install steps and Redmi/HyperOS phone settings.

## 📁 Key Kotlin Components

| File | Responsibility |
|------|----------------|
| `App.kt` | Application-level translation engine creation, warm-up, and global crash logging |
| `MainActivity.kt` | Permission UI, status display, overlay start/stop, and one-time keep-alive guidance |
| `OverlayService.kt` | Draws & manages draggable overlay button |
| `OverlayClickHandler.kt` | Handles floating-dot taps when the Activity is alive or gone; queues extraction while Accessibility reconnects |
| `TranslateAccessibilityService.kt` | Extracts & filters visible message text nodes |
| `TranslationController.kt` | Process-wide bridge from extracted text to streaming popup/engine |
| `TranslationEngine.kt` | Language heuristics, streaming orchestration, online translation + transliteration fusion |
| `OnlineTranslator.kt` | Best-effort Google translate endpoint client (auto/English -> Gujarati) |
| `Transliterator.kt` | Google Input Tools POST client (phonetic -> Gujarati script) |
| `TranslationPopup.kt` | Full‑screen dim + popup list, streaming incremental rendering |
| `TranslationPairAdapter.kt` | Recycler adapter with per-line copy support |

---

## 🔐 Permissions & Why

| Permission | Why Needed |
|------------|------------|
| `SYSTEM_ALERT_WINDOW` | Draw floating overlay & popup across apps |
| `BIND_ACCESSIBILITY_SERVICE` | Read visible text nodes from messaging UI when user taps |
| `INTERNET` | Online translation + transliteration HTTP calls |
| `ACCESS_NETWORK_STATE` | Network availability checks for online calls |
| `FOREGROUND_SERVICE` | Keep the overlay service running while the floating button is visible |
| `POST_NOTIFICATIONS` | Android 13+ foreground-service notification permission |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Lets the user exempt the app from battery optimization on aggressive OEM builds |

No contacts, storage, microphone, or location permissions are used.

---

## 🧠 Translation & Transliteration Logic

Per line decision flow:
```
contains Gujarati script? → show original
else if contains Latin letters only → do BOTH:
   Online translate auto/EN→GU
    Google Input Tools transliterate phonetic → GU script
    prefer transliteration if present else translation
else → try translate → fallback original
```
Streaming: Each processed line immediately emits a `TranslationLinePair` to the popup; UI remains responsive.

Resilience:
- Language identifier is recreated if ML Kit closes unexpectedly
- Translation/transliteration calls have short timeouts; failures are silent (line just uses the other result or original)
- Duplicate suppression + last-window cap prevents ballooning payloads

---

## 🪟 Windows Development Setup

### 1. Prerequisites

Install / prepare on Windows (PowerShell recommended):
1. **Git**: https://git-scm.com/download/win
2. **JDK 17** (Temurin / Oracle). Set `JAVA_HOME` (optional but recommended):
   ```powershell
   # Example (adjust path):
   setx JAVA_HOME "C:\Program Files\Java\jdk-17"
   setx PATH "$env:PATH;%JAVA_HOME%\bin"
   ```
3. **Android Studio (latest)**: https://developer.android.com/studio
   - Install SDK Platforms (Android 34 + 23 for minSdk testing)
   - Install SDK Tools: Platform Tools (adb), Build Tools, Android SDK Command-line Tools
4. **USB Driver (Windows)**: Install OEM (e.g., Google USB Driver via SDK Manager) or use ADB over Wi‑Fi.
5. **Device**: Android 6.0+ with WhatsApp installed.

### 2. Clone Repository
```powershell
git clone https://github.com/<your-user-or-fork>/TranslateAssist.git
cd TranslateAssist/android
```

### 3. Open in Android Studio
File → Open → select `TranslateAssist/android` (root containing `app/`). Let Gradle sync (ensure JDK 17 selected in Project Structure).

### 4. Build (GUI)
Build → Make Project.

### 5. Build (Command Line)
From `TranslateAssist/android`:
```powershell
./gradlew.bat assembleDebug
```
Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### 6. Install on Device
Enable Developer Options & USB Debugging:
Settings → About Phone → tap Build Number 7x → Back → Developer Options → enable USB Debugging.

Then:
```powershell
adb devices            # authorize prompt on device
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

If using Wi‑Fi ADB:
```powershell
adb pair <host>:<port>
adb connect <device-ip>:5555
```

### 7. First Launch Setup UI
The main screen shows live status for the overlay service, accessibility service, overlay permission, and notification permission.

1. Launch the app and accept the Android 13+ notification prompt if it appears.
2. Tap "Start Overlay". If overlay permission is missing, Android opens "Display over other apps" for TranslateAssist.
3. Tap "Enable Accessibility Service" and turn on TranslateAssist in Android Accessibility settings.
4. Return to TranslateAssist and tap "Start Overlay" again. The floating button appears.
5. On the one-time setup dialog, open Autostart/Auto-launch when available and allow background activity / no battery restrictions.

The app remembers explicit overlay intent: "Stop Overlay" disables the auto-start paths, while a previously enabled overlay may be restored when the accessibility service reconnects.

### 8. First Translation Network Use
Translation and transliteration are network-backed. Make sure the device has internet access for the first and subsequent translation attempts.

---

## ▶️ Usage Flow
1. Open WhatsApp (or supported messaging app)
2. Scroll so the messages you want are visible
3. Tap the floating green button
4. Popup appears with loader → lines stream in
5. Long‑press any line to copy its translated/transliterated text combination
6. Tap “Copy” (Copy All) after stream completes to copy every rendered line
7. Tap outside popup or Close (X) to dismiss

Tip: Keep the number of visible messages modest for fastest response.

---

## 🌐 Supported / Observed Apps
- WhatsApp (`com.whatsapp`)
- Google Messages (`com.google.android.apps.messaging`)
- AOSP / OEM messaging (`com.android.mms`, `com.samsung.android.messaging`)

Other messaging apps may partially work if their accessibility node structure is similar, but only the above are filtered explicitly.

---

## 🧪 Filtering & Extraction Heuristics
Removes UI noise such as:
- Buttons (attach, camera, emoji, send)
- Action labels (voice call, search, info)
- Timestamps, participant counts (e.g., "35 online")
- File size indicators ("2 MB") & attachment names
- Reaction / status tokens (delivered/read) & placeholders

Keeps plausible message lines containing meaningful characters or Gujarati script. Short acknowledgements (ok, haan, હા) are allowed.

---

## 📦 Dependencies Snapshot
- Kotlin + Coroutines (`kotlinx-coroutines-android`)
- AndroidX Core/AppCompat/Material/ConstraintLayout/RecyclerView
- ML Kit: `language-id`, `text-recognition` (OCR reserved for potential fallback, currently not invoked directly)
- OkHttp (online translation and transliteration HTTP endpoints)

Chaquopy / Python stack: REMOVED (previous transliteration pipeline replaced by lightweight HTTP transliteration).

---

## 🛡 Privacy & Data Handling
| Aspect | Behavior |
|--------|----------|
| Message Storage | None (in-memory only, discarded after popup closed) |
| Network Calls | Online translation endpoint and transliteration endpoint per Latin-script line |
| Analytics / Tracking | None |
| Sensitive Permissions | Overlay, accessibility, notifications, foreground service, internet/network state, battery optimization exemption request |
| Scope of Accessibility | Reads current foreground nodes only on tap |

Note: Translation and transliteration use unofficial Google endpoints; content of Latin-script lines you request to process is sent over HTTPS. Short-circuit `OnlineTranslator.kt` and/or `Transliterator.kt` if you need offline-only behavior.

---

## 🧯 Troubleshooting

| Issue | Checks / Fixes |
|-------|----------------|
| Overlay button not visible | Overlay permission granted? Notifications allowed on Android 13+? Service started? Battery optimization killing app? |
| Accessibility enabled but not responding | Enable Autostart/Auto-launch, allow background activity, and reopen TranslateAssist if the service does not reconnect. |
| Popup shows but empty | Are messages actually visible? Try scrolling slightly; ensure app is a supported package. |
| Repeated "Translation failed" | Check internet access and watch Logcat for `TranslationEngine`, `OnlineTranslator`, or `Transliterator` errors. |
| Transliteration missing | Network blocked, endpoint slow, or input not phonetic Gujarati. Falls back to translation. |
| Slow first run | App/process cold start or network latency; subsequent taps are usually faster. |
| Copies wrong text | Long‑press copies translation + transliteration; use Copy All after stream for full list. |
| Not targeting right chat | Make sure WhatsApp is foreground before tapping. |

### Logcat Tags
- `TranslateAccessibility` – extraction diagnostics
- `TranslationEngine` – per-line detection, errors
- `OnlineTranslator` – online translation attempts
- `Transliterator` – network transliteration attempts

---

## 🔧 Extensibility Ideas
- Add settings UI: toggle transliteration, adjust max visible lines, choose online/offline behavior
- OCR fallback (ML Kit Text Recognition) for image-based messages (dependency already present)
- Add per-app extraction profiles
- Support bi-directional (GU → EN) translation
- Cache last N translation results for quick re-display (currently intentionally stateless)

---

## ⚠ Legal & Ethical Disclaimer
This tool leverages Android Accessibility APIs solely for user-initiated translation of currently visible messages. Use must comply with WhatsApp’s Terms of Service and local regulations. No modification of other apps occurs. Distribution beyond personal use may require additional review of trademark and API usage policies (especially the unofficial transliteration endpoint).

---

## 🙋‍♂️ Support / Maintenance
Personal project: no guaranteed updates. Feel free to fork, audit, and adapt. Submit improvements via pull request in your fork; original repository may remain minimal.

---

## ✅ Quick Start Summary (Windows)
```text
1. Install JDK 17 + Android Studio
2. git clone ... & open /android project
3. Build & Run (or ./gradlew.bat assembleDebug)
4. Install APK (adb install)
5. Grant notifications, overlay + accessibility
6. Open WhatsApp → tap floating button → see streaming Gujarati output
```

---

## ✍ Attribution
Built with AndroidX, Google ML Kit Language ID, OkHttp, Google translate endpoint access, and Google Input Tools transliteration (unofficial usage). All trademarks belong to their respective owners.

---

Enjoy faster bilingual chat reading! 🕶️