# Copilot instructions for KoiYurePublic

## Build, test, and lint

This is a single-module Android application. Use the Gradle Wrapper from the repository root; on Windows use `gradlew.bat`, and in CI/Linux use `./gradlew`.

```powershell
# Compile/package all configured variants and run the standard verification tasks
.\gradlew.bat build

# Build the debug APK
.\gradlew.bat assembleDebug

# Run local JVM tests
.\gradlew.bat test

# Run one local test class or one test method
.\gradlew.bat test --tests "com.example.koiyurepublic.ExampleUnitTest"
.\gradlew.bat test --tests "com.example.koiyurepublic.ExampleUnitTest.addition_isCorrect"

# Run Android instrumentation tests on a connected emulator/device
.\gradlew.bat connectedAndroidTest

# Run one instrumentation test class or method
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.koiyurepublic.ExampleInstrumentedTest
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.koiyurepublic.ExampleInstrumentedTest#useAppContext

# Run Android lint for the debug variant
.\gradlew.bat lintDebug
```

From Linux/macOS or CI, use the equivalent `./gradlew` commands. The module targets SDK 36, supports Android API 29+, uses Gradle 8.13 and compiles Java with source/target compatibility 11. CI builds with JDK 11 in `.github/workflows/android.yml` and JDK 17 in `.github/workflows/gradle.yml` and `gradle-publish.yml`; keep source compatible with Java 11. There is no separate formatter or linter configuration; `lintDebug` is the Android lint task supplied by the Android Gradle Plugin. The repository currently contains only example unit and instrumentation tests, so the single-test commands above are the available test patterns.

## Architecture

- `app` is the only Gradle module. The HTML/CSS/JavaScript UI and map resources are packaged as Android assets rather than served by a separate web application; the launcher loads `app/src/main/assets/Maindex.html` in a `WebView`.
- `MainActivity` renders the local asset `Maindex.html` in a `WebView`, exposes the JavaScript interface named `AndroidBridge`, and binds to `SpinalCord`, an exported=false foreground service declared in `AndroidManifest.xml`. The activity starts it on launch/start, while `BootReceiver` and `WatchdogReceiver` support startup and recovery after reboot or service termination.
- `SpinalCord` is the runtime coordinator. It holds the P2PQuake WebSocket client, initializes the CSV-backed `EpspArea` lookup, and owns `TTSConnection` and `NotifiConnection`. It uses a wake lock, a persistent foreground notification, `START_STICKY`, and exact alarms for watchdog/self-restart behavior; preserve the distinction between intentional stops and recovery restarts.
- `P2PQuakeWebSocketClient` connects to `wss://api.p2pquake.net/v2/ws`, emits listener events, and reconnects with backoff until explicitly disconnected. `SpinalCord.onMessage()` is the central data path: extract the P2PQuake `code`, format a brief message for notifications, format a full message for TTS, then broadcast the raw JSON to the UI.
- The active Java-to-WebView data path is `LocalWebSocketServer` on `ws://127.0.0.1:9001`: it broadcasts service state, P2P connection state, earthquake payloads, and TTS/notification status, and sends a state snapshot to newly connected pages. `AndroidBridge` is the JavaScript-to-Android command path for service and setting changes. Keep both paths consistent when changing an exposed state or event; do not assume the WebView can fetch a network-hosted frontend.
- `P2PConverts` is the Java formatter for notification/TTS text. `MainScript.js` parses and formats the same P2PQuake payloads for detailed UI display, while `settings.html` exposes per-code TTS and notification toggles. `EpspArea` loads `assets/accompanying/MYepsp-area.csv` once and provides region names and coordinates used by earthquake/map behavior.

## Repository-specific conventions

- P2PQuake message codes are part of the application contract: `551` JMA earthquake, `552` tsunami, `554` EEW detection, `555` area peers, `556` EEW, `561` user earthquake, and `9611` user-earthquake evaluation. When adding or changing a code, update the relevant Java conversion/notification logic, JavaScript formatting/filters, and any tests or documentation together.
- Emergency handling is intentionally stronger than ordinary information: codes `554` and `556` use interrupting TTS, `555` is not read aloud, low-confidence `9611` results are suppressed, and tsunami/EEW cancellation messages cancel existing notifications. Preserve these semantics when refactoring message dispatch.
- The service lifecycle is deliberately resilient. `SpinalCord` starts/stops the local server and child components as a unit, schedules the watchdog, and releases the wake lock/TTS/WebSocket resources in `onDestroy()`. Changes to lifecycle code must account for activity binding, boot startup, watchdog restart, and intentional user stop.
- JavaScript-to-Android calls must use the existing `AndroidBridge` method names and should be posted to the Android main thread as the bridge implementation does. UI state/data should be sent through the local WebSocket message types already handled by `MainScript.js` (`serviceStateChanged`, `connectionStateChanged`, `earthquakeData`, `ttsStatus`, and `notifStatus`). The WebView loads local assets via `file:///android_asset/`; do not assume a network origin or a separate web build.
- Keep user-facing strings and existing comments consistent with the current Japanese UI. Java production code is under `com.example.koiyurepublic`, uses Java 11, and follows the existing AndroidX/AppCompat setup.
- Runtime data files under `app/src/main/assets/accompanying/` are inputs to parsing and map behavior; preserve their paths and expected CSV/GeoJSON formats when modifying `EpspArea` or the JavaScript map. Asset filenames and the `file:///android_asset/` paths are part of the runtime contract.
- Changes to P2PQuake codes or filtering must stay synchronized across `P2PConverts`, `SpinalCord.SUPPORTED_MESSAGE_CODES`, `MainScript.js`, `settings.html`, and relevant tests/docs. Code `1112` is included in per-code settings even though it has no dedicated formatter branch in `P2PConverts`.
- `google-services.json`, `local.properties`, build outputs, and keystores are intentionally ignored. Do not add generated build artifacts or Firebase configuration/credentials to commits.
- The Gradle version catalog in `gradle/libs.versions.toml` is the source of truth for AndroidX, Material, Firebase Analytics, test dependencies, and the Android/Google Services plugins. Prefer its aliases for catalogued dependencies; the Java-WebSocket dependency is currently declared directly in `app/build.gradle`.

## Project references

Use `README.md` for the repository's top-level project reference and `docs/specification.yaml` for the external/data specification. The GitHub Actions workflows under `.github/workflows/` are also authoritative examples of the supported CI build commands and JDK setup.
