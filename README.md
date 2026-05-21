# MigsScan

A no-frills Android document scanner. Open the app, tap **Scan**, point your
phone at the page, share the result as a PDF (or JPEG / PNG). No ads, no
sign-in, no cloud upload, no "premium" tier.

The actual scanning — edge detection on creased and folded paper, perspective
correction, multi-page batching — is handled by Google's on-device ML Kit
document scanner, so the magic is the same one Google Drive uses. This app
is just a thin wrapper around it: launch the scanner, save the result, share.

## Features

- Capture single or multi-page scans (up to 50 pages per document)
- Automatic edge detection and perspective correction
- Import existing photos from the gallery instead of capturing live
- Share each scan as **PDF**, **JPEG**, or **PNG** via the standard Android
  share sheet (Gmail, Drive, Messages, AirDrop-equivalents, anything)
- **Rename** any scan from the action sheet — the new name is what Gmail,
  Drive, etc. see as the file's name when you share it
- **Search** the scan list by name from the bar at the top
- Scans persist between launches on local storage; delete from a long-press
  sheet when you're done with them
- Adaptive launcher icon, branded splash, light + dark mode

## Not (yet) in the app

- Thumbnails in the scan list — rows are text-only for now
- Tap a scan to preview pages in-app — sharing, renaming, or deleting are
  the actions today
- Star scans, bulk delete, folders / tags
- Settings screen
- Cloud sync — by design, this is a local-only app

## Requirements

- **Android 7.0 (API 24)** or newer
- Google Play Services on the device (the ML Kit document scanner module is
  downloaded through Play Services on first use — comes pre-installed on
  every consumer Android phone)

## Build & install

The project is a standard Gradle Android build. You need:

- JDK 17 or 21 (the bundled `gradlew` script picks up whatever `java` you
  point `JAVA_HOME` at; Android Studio's JBR works out of the box)
- Android SDK with platform 35 + build-tools 35 (Android Studio installs
  these by default)

```sh
./gradlew :app:assembleDebug      # build a debug APK
./gradlew :app:installDebug       # install on a connected device
```

The debug APK lands in `app/build/outputs/apk/debug/`.

## Tests

```sh
./gradlew :app:testDebugUnitTest          # local JVM + Robolectric (no device)
./gradlew :app:pixel6api36DebugAndroidTest  # on-device suite on a managed headless emulator
./gradlew :app:connectedDebugAndroidTest  # on-device suite against a plugged-in phone
```

The `pixel6api36` device is provisioned automatically the first time
you run it (a `google_apis_playstore` system image is downloaded —
~1 GB). After that runs are fast and offline.

| Suite | Files | What it covers |
|---|---|---|
| Local | `ScanStoreTest`, `ShareFormatTest`, `SharingTest`, `ScanViewModelTest` | persistence, intent shape, PNG re-encode cache, ViewModel state |
| On-device | `SharingFileProviderTest`, `MigsScanAppUiTest`, `ScanFlowEndToEndTest` | real FileProvider URIs, Compose screens, scan → persist → share round-trip |

The local suite runs in CI on every push; the on-device suite is intended
to run against a plugged-in phone or an emulator.

## Tech stack

- **Kotlin** 2.1 + **Jetpack Compose** (Material 3)
- **CameraX-free** — all capture goes through ML Kit's
  `play-services-mlkit-document-scanner`, which launches its own polished
  scanner UI
- **AndroidX FileProvider** for share-sheet URIs
- **Robolectric** + **JUnit 4** locally, Compose `ui-test-junit4` on-device

## Project layout

```
app/
  src/main/java/dev/migs/scan/
    MainActivity.kt              # hosts the Compose tree
    data/                        # Scan, ScanPayload, ScanStore
    share/                       # ShareFormat, Sharing (intent builders)
    ui/                          # MigsScanApp, ScanViewModel, ScannerLauncher
  src/test/                      # local JVM / Robolectric suite
  src/androidTest/               # on-device suite
tools/icon.svg                   # source for the launcher icon (re-export with ImageMagick)
.github/workflows/ci.yml         # build + unit tests on every push
```
