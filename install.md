# TranslateAssist - Build & Install (USB Cable)

This guide explains how to build and install TranslateAssist on an Android phone using a USB cable (Windows + ADB), and the required phone settings (Redmi / HyperOS / Android 15 included).

---

## 1) Prerequisites (Windows)

### Required
- **JDK 17** (Android Gradle Plugin requires Java 17)
- **Android SDK Platform Tools** (ADB)
- A USB cable that supports **data** (not charge-only)

### Optional (recommended)
- **Android Studio** (simplest way to build/run and view Logcat)

### Confirm tools
Open PowerShell and verify:

```powershell
java -version
adb version
```

If `adb` is not found, install Platform Tools via Android Studio (SDK Manager) or download Platform Tools, then add it to your PATH.

---

## 2) Install via Android Studio (recommended)

This is the easiest route because Android Studio handles Gradle sync, app install, and Logcat.

### A) Open the project
1. Open **Android Studio**
2. File → Open…
3. Select the folder: `TranslateAssist/android`
4. Let **Gradle sync** finish (use **JDK 17** if prompted)

### B) Connect and select your phone
1. Connect the phone via USB (see the “Phone setup” section below)
2. In Android Studio, pick your device from the device dropdown (top toolbar)

### C) Build + install + run
1. Click **Run** (green ▶) for the `app` configuration
2. Android Studio will:
    - build a debug APK
    - install it to the phone
    - launch the app

### D) Useful debugging (optional)
- View logs: **Logcat** → filter by tags:
   - `TranslateAccessibility`
   - `TranslationEngine`
   - `OnlineTranslator`
   - `Transliterator`

---

## 3) Phone setup (one-time)

### Enable Developer options + USB debugging
1. Settings → About phone → tap **MIUI/HyperOS version** (or Build number) 7 times.
2. Settings → Additional settings → **Developer options**:
   - **USB debugging** → ON
   - (Optional but helpful) **USB debugging (Security settings)** → ON

### Connect via USB and authorize
1. Connect the phone to the PC.
2. On the phone, set USB mode to **File transfer (MTP)** if prompted.
3. Accept the **“Allow USB debugging?”** RSA prompt (check “Always allow”).

Verify from PC:

```powershell
adb devices
```

You should see your device listed as `device` (not `unauthorized`).

---

## 4) Build the APK (Debug)

From the repo root:

```powershell
cd .\android
.\gradlew.bat assembleDebug
```

The debug APK will be at:

- `android\app\build\outputs\apk\debug\app-debug.apk`

---

## 5) Install over USB

From `TranslateAssist\android`:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

If you see a signature mismatch error (e.g., `INSTALL_FAILED_UPDATE_INCOMPATIBLE`), uninstall the existing app first:

```powershell
adb uninstall com.translateassist
adb install .\app\build\outputs\apk\debug\app-debug.apk
```

---

## 6) First-run setup inside TranslateAssist

TranslateAssist's main screen shows these live statuses:
- **Overlay Service**: Running / Stopped
- **Accessibility Service**: Active / Enabled (waiting) / Disabled
- **Overlay Permission**: Granted / Not Granted
- **Notification Permission**: Granted / Not Granted / Not required

The UI has two primary buttons:
- **Start Overlay / Stop Overlay**: requests overlay permission if needed, then starts or stops the floating button.
- **Enable Accessibility Service**: opens Android Accessibility settings so you can enable TranslateAssist.

TranslateAssist needs 4 things to work reliably:
1. Notifications permission on Android 13+ for the foreground-service notification.
2. "Display over other apps" permission for the floating button and popup.
3. Accessibility Service enabled for on-demand visible-message extraction.
4. Autostart/background activity allowed on aggressive OEM builds so the accessibility service can reconnect after the process is killed.

### A) Allow restricted settings (important for sideloaded Accessibility)
On many Redmi/HyperOS devices, Accessibility for sideloaded apps requires this:
- Settings → Apps → Manage apps → **TranslateAssist**
- Tap **⋮ (More)** → **Allow restricted settings**

### B) Allow Notifications (Android 13+)
- Settings → Notifications & Control center → App notifications → **TranslateAssist** → Allow

Or accept the runtime prompt when TranslateAssist asks. If notifications are denied, Android may prevent the foreground overlay service from staying up.

### C) Allow “Display over other apps”
- Settings → Apps → Special permissions → **Display over other apps** → TranslateAssist → Allow

### D) Enable Accessibility Service
- Settings → Accessibility → Downloaded apps / Installed services → **TranslateAssist** → ON

### E) Use the in-app flow
1. Open TranslateAssist.
2. Accept the notification prompt if shown.
3. Tap **Start Overlay** and grant overlay permission if Android opens that settings screen.
4. Tap **Enable Accessibility Service**, turn on TranslateAssist, then return to the app.
5. Tap **Start Overlay** again. The floating dot should appear.
6. If the one-time setup dialog appears, tap **Open Autostart** and enable Autostart/Auto-launch when your phone shows that page. Also allow background activity / no battery restrictions when prompted.

The app remembers your explicit overlay choice. If you tap **Stop Overlay**, auto-start paths will respect that and should not revive the floating dot until you start it again.

---

## 7) Redmi / HyperOS anti-kill settings (highly recommended)

These prevent the overlay/accessibility process from being killed in the background. This matters because the floating dot can only extract text after Android has rebound the accessibility service.

1. **Battery saver**: Settings → Apps → Manage apps → TranslateAssist → **Battery saver** → **No restrictions**
2. **Autostart**: Settings → Apps → Manage apps → TranslateAssist → **Autostart** → ON
3. **Lock in Recents**:
   - Open TranslateAssist once
   - Open Recents screen
   - Long-press TranslateAssist card → **Lock**

---

## 8) Usage test

1. Open TranslateAssist.
2. Confirm the status panel shows overlay permission granted and accessibility active or enabled.
3. Tap **Start Overlay** if the floating dot is not already visible.
4. Open WhatsApp or a supported Messages app.
5. Scroll so the messages you want are visible.
6. Tap the floating dot.

You should see the translation popup with streaming results.

---

## 9) Troubleshooting

### `adb devices` shows `unauthorized`
- Unplug/replug USB
- On phone: revoke USB debugging authorizations (Developer options) then reconnect
- Accept the RSA prompt again

### Overlay dot disappears or stops responding
- Ensure Notifications are allowed (Android 13+)
- Set Battery saver to **No restrictions** and enable **Autostart**
- Lock the app in Recents
- Reopen TranslateAssist and tap **Start Overlay** if you explicitly stopped it or cleared recent apps

HyperOS/MIUI extras (common):
- Settings → Apps → Manage apps → TranslateAssist → **Other permissions** → allow anything like:
   - “Display pop-up windows while running in background”
   - “Start in background” / “Run in background”
- Ensure the ongoing notification “TranslateAssist active” is not blocked.

### Accessibility toggles off by itself
- Re-enable it in Settings (only user can do this)
- Ensure “Allow restricted settings” is enabled for TranslateAssist
- Apply the anti-kill steps above

### Accessibility is enabled but the floating dot says the service is waking up
- Give Android a few seconds to rebind the service.
- If it still does not respond, reopen TranslateAssist and check that the status panel says **Accessibility Service: Active**.
- Enable Autostart/Auto-launch and background activity. On Redmi/HyperOS this is the setting that usually lets Android restart the service after clearing recents.

If Android/HyperOS shows “This app is malfunctioning” under the Accessibility toggle:
1. Settings → Apps → Manage apps → TranslateAssist → **⋮ (More)** → **Allow restricted settings**
   - This can get reset after reinstall/update from Android Studio.
2. Settings → Accessibility → TranslateAssist → toggle **OFF**, wait a moment, toggle **ON**
   - Unfortunately HyperOS enforces a cooldown; the goal is to avoid needing to do this often.
3. Settings → Apps → Manage apps → TranslateAssist → Battery saver → **No restrictions**
4. Reboot phone once (often clears the stuck “malfunctioning” state)

### Translation or transliteration fails
- Translation and transliteration are online best-effort calls. Ensure internet is allowed for TranslateAssist over Wi-Fi or mobile data.
- Watch Logcat for `TranslationEngine`, `OnlineTranslator`, and `Transliterator`.
- If transliteration fails but translation succeeds, the app falls back automatically.

---
