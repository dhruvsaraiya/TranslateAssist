# Release (Non-Debug) Build & Distribution Guide

This document explains how to produce a properly signed **release** APK (or AAB) for TranslateAssist and install it on a device. It complements the development instructions in the main `README.md`.

---
## 1. Overview

Release builds differ from debug builds by:
- Being signed with your **own keystore** (not the debug keystore)
- Optionally enabling code shrinking (`minifyEnabled` / `shrinkResources`)
- Using a higher optimization level and no debug flags
- Being ready for distribution (Play Store via AAB or direct APK sideload)

---
## 2. Generate a Keystore (One-Time)
Use the JDK `keytool` (ensure JDK 17 is installed and on PATH).

```powershell
keytool -genkeypair -v ^
  -keystore translateassist-release.keystore ^
  -alias translateassist ^
  -keyalg RSA -keysize 2048 -validity 3650
```

Prompts:
- Keystore password
- Distinguished Name fields (CN, OU, O, L, ST, C)
- Confirm (type `yes`)

Store the keystore somewhere **outside** version control (e.g., `android/keystore/`). Add the folder to `.gitignore` if necessary.

---
## 3. Securely Provide Signing Credentials
Add properties to a *private* `gradle.properties` (preferred: user-level `%USERPROFILE%\.gradle\gradle.properties`).

Example entries:
```
RELEASE_STORE_FILE=../keystore/translateassist-release.keystore
RELEASE_STORE_PASSWORD=yourStorePass
RELEASE_KEY_ALIAS=translateassist
RELEASE_KEY_PASSWORD=yourKeyPass
```

> If you put these in the project `gradle.properties`, ensure the keystore path is correct and that you **never commit secrets** to a public repo.

---
## 4. Configure `app/build.gradle`
Inside the `android { }` block add or adjust:

```groovy
signingConfigs {
    release {
        storeFile file(RELEASE_STORE_FILE)
        storePassword RELEASE_STORE_PASSWORD
        keyAlias RELEASE_KEY_ALIAS
        keyPassword RELEASE_KEY_PASSWORD
    }
}

buildTypes {
    release {
        // Enable shrinking for smaller APK (optional – verify functionality!)
        minifyEnabled true
        shrinkResources true
        signingConfig signingConfigs.release
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

### Recommended ProGuard / R8 Rules
Add to `proguard-rules.pro` if shrinking is enabled:
```
# Keep ML Kit translation & language ID internals
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# (Optional) Be conservative initially:
-dontwarn okhttp3.**
-dontwarn org.jetbrains.annotations.**
```
Test thoroughly before adding aggressive removals.

---
## 5. Increment Version
In `defaultConfig` (same file):
```groovy
versionCode 2      // increment for every release
versionName "1.1"  // human readable
```
`versionCode` must strictly increase for Play Store updates.

---
## 6. Build Release Artifacts
From the `android/` directory:

### APK
```powershell
./gradlew.bat clean assembleRelease
```
Produces:
```
app/build/outputs/apk/release/app-release.apk
```

### AAB (Play Store preferred)
```powershell
./gradlew.bat bundleRelease
```
Produces:
```
app/build/outputs/bundle/release/app-release.aab
```

---
## 7. Verify Signature (Optional but Recommended)
Use the SDK `apksigner` (replace `<build-tools-version>`):
```powershell
"$env:ANDROID_HOME\build-tools\<build-tools-version>\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```
Look for `Verified using v1 scheme (JAR signing): true` and certificate info matching your keystore.

---
## 8. Install Release APK (Sideload)
If a debug version is installed (different signature), uninstall first:
```powershell
adb uninstall com.translateassist
adb install app\build\outputs\apk\release\app-release.apk
```
If you want to keep user data across reinstalls, consider using `adb install -r` **only if** the signing key matches (otherwise install will fail).

---
## 9. First-Run Notes
- Model download: The ML Kit EN→GU model downloads on first translation attempt (Wi‑Fi required per current code). After that it’s offline.
- Accessibility + overlay permissions must be re-granted after a fresh install.

---
## 10. Distributing via Play Store (High-Level)
1. Use **AAB** (`bundleRelease`).
2. Create Play Console app (package `com.translateassist`).
3. Upload AAB under an internal testing track.
4. Provide app listing (descriptions, icon, screenshots).
5. Complete content rating & policy declarations (uses Accessibility Service – justify translation use case clearly).
6. Roll out to testers, then production.

> Accessibility declarations: You must describe exactly why you access other apps’ text (user-triggered translation). Google may request additional justification.

---
## 11. Reproducibility Tips
- Pin the same JDK version on CI and locally.
- Avoid committing the generated ML Kit model (it’s downloaded at runtime).
- Keep `gradle-wrapper.properties` consistent (don’t silently upgrade).

---
## 12. Hardening (Optional)
| Goal | Action |
|------|--------|
| Smaller size | Tune ProGuard rules, remove unused ML Kit modules (keep only translate, lang-id) |
| Faster cold start | Pre-warm translator after app launch (call `downloadModelIfNeeded`) |
| Offline-only | Disable transliteration network calls (return null in `Transliterator`) |
| Integrity | Consider Play Integrity API / app attestation (future) |

---
## 13. Common Release Issues
| Problem | Cause | Fix |
|---------|-------|-----|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Signature mismatch (debug vs release) | Uninstall old app first |
| App crashes only in release | Code stripped by R8 | Add keep rules for removed classes |
| Transliteration missing | Network blocked or endpoint change | Fallback already translation; inspect logs |
| ML model never downloads | Wi-Fi requirement unmet | Remove `.requireWifi()` in `DownloadConditions` |
| Play Console rejection | Insufficient Accessibility justification | Expand declaration; confirm user-triggered scope |

---
## 14. Minimal Automation (CI Sketch)
```yaml
# Example GitHub Actions snippet (conceptual)
steps:
  - uses: actions/checkout@v4
  - uses: actions/setup-java@v4
    with:
      distribution: temurin
      java-version: '17'
  - name: Decode Keystore
    run: |
      echo "$KEYSTORE_BASE64" | base64 -d > android/keystore/translateassist-release.keystore
  - name: Gradle Build
    working-directory: android
    run: ./gradlew assembleRelease
  - name: Archive APK
    uses: actions/upload-artifact@v4
    with:
      name: app-release-apk
      path: android/app/build/outputs/apk/release/app-release.apk
```
(Secrets: `KEYSTORE_BASE64`, plus signing passwords as env vars -> map to gradle.properties.)

---
## 15. Quick TL;DR
```text
keytool (create keystore)
Add signing creds to gradle.properties
Add signingConfigs + release buildType
./gradlew.bat assembleRelease
adb install app-release.apk
Grant permissions → use
```

---
**End of Guide**
