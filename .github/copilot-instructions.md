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

The module targets SDK 36, supports Android API 29+, and compiles Java with source/target compatibility 11. CI currently builds with both JDK 11 (`.github/workflows/android.yml`) and JDK 17 (`.github/workflows/gradle.yml` and `gradle-publish.yml`), so keep changes compatible with the configured Java 11 source level. There is no separate formatter or linter configuration in the repository; `lintDebug` is the Android lint task supplied by the Android Gradle Plugin.

## Architecture

- `app` is the only Gradle module. `MainActivity` is the launcher activity and renders `app/src/main/assets/Maindex.html` inside a `WebView`. The HTML/CSS/JavaScript UI and map resources are packaged as Android assets rather than served by a separate web application.
- `MainActivity` binds to `SpinalCord`, an exported=false foreground service declared in `AndroidManifest.xml`. The activity starts it on launch/start, while `BootReceiver` and `WatchdogReceiver` support startup and recovery after reboot or service termination.
- `SpinalCord` is the runtime coordinator. It holds the P2PQuake WebSocket client, initializes the CSV-backed `EpspArea` lookup, and owns `TTSConnection` and `NotifiConnection`. It uses a wake lock, a persistent foreground notification, `START_STICKY`, and exact alarms for watchdog/self-restart behavior; preserve the distinction between intentional stops and recovery restarts.
- `P2PQuakeWebSocketClient` connects to `wss://api.p2pquake.net/v2/ws` and broadcasts connection/message events to listeners. `SpinalCord.onMessage()` is the central data path: extract the P2PQuake `code`, format a brief message for notifications, format a full message for TTS, then publish the raw JSON to the UI.
- UI updates have two paths. The `AndroidBridge` JavaScript interface calls into `MainActivity`/`SpinalCord`, and `LocalWebSocketServer` broadcasts service state, connection state, earthquake data, and setting changes on `ws://127.0.0.1:9001`. Keep both paths consistent when changing an exposed state or event.
- `P2PConverts` is the Java formatter for notification/TTS text. The JavaScript formatter in `app/src/main/assets/MainScript.js` formats the same P2PQuake payloads for detailed UI display. `EpspArea` loads `assets/accompanying/MYepsp-area.csv` once and provides region names and coordinates used by earthquake/map behavior.

## Repository-specific conventions

- P2PQuake message codes are part of the application contract: `551` JMA earthquake, `552` tsunami, `554` EEW detection, `555` area peers, `556` EEW, `561` user earthquake, and `9611` user-earthquake evaluation. When adding or changing a code, update the relevant Java conversion/notification logic, JavaScript formatting/filters, and any tests or documentation together.
- Emergency handling is intentionally stronger than ordinary information: codes `554` and `556` use interrupting TTS, `555` is not read aloud, low-confidence `9611` results are suppressed, and tsunami/EEW cancellation messages cancel existing notifications. Preserve these semantics when refactoring message dispatch.
- The service lifecycle is deliberately resilient. `SpinalCord` starts/stops the local server and child components as a unit, schedules the watchdog, and releases the wake lock/TTS/WebSocket resources in `onDestroy()`. Changes to lifecycle code must account for activity binding, boot startup, watchdog restart, and intentional user stop.
- JavaScript-to-Android calls must use the existing `AndroidBridge` method names, and Java-to-JavaScript callbacks must be dispatched on the main thread through `evaluateJavascript`. The WebView loads local assets via `file:///android_asset/`; do not assume a network origin or a separate web build.
- Keep user-facing strings and existing comments consistent with the current Japanese UI. Java production code is under `com.example.koiyurepublic`, uses Java 11, and follows the existing AndroidX/AppCompat setup.
- Runtime data files under `app/src/main/assets/accompanying/` are inputs to parsing and map behavior; preserve their paths and expected CSV/GeoJSON formats when modifying `EpspArea` or the JavaScript map.
- `google-services.json`, `local.properties`, build outputs, and keystores are intentionally ignored. Do not add generated build artifacts or Firebase configuration/credentials to commits.
- The Gradle version catalog in `gradle/libs.versions.toml` is the source of truth for AndroidX, Material, Firebase Analytics, test dependencies, and the Android/Google Services plugins. Prefer its aliases for catalogued dependencies; the Java-WebSocket dependency is currently declared directly in `app/build.gradle`.

## Project references

Use `README.md` for the repository's top-level project reference and `docs/specification.yaml` for the external/data specification. The GitHub Actions workflows under `.github/workflows/` are also authoritative examples of the supported CI build commands and JDK setup.
