# Handoff: voice-note recording in an opt-in `sdk-voice` module (SDK 0.4.0)

Branch `voice-notes`, base `origin/interim-lascade-publish`. The design is decided; read
`docs/adr/0006-voice-notes-in-opt-in-sdk-voice-module.md`, `docs/adr/0005-no-voice-note-recording.md`,
`CONTEXT.md` and `CLAUDE.md` first. The pre-0.3.0 recorder (`git show f6052e8^:sdk/src/...media/AudioRecorder*.kt`,
`f6052e8^:sdk/src/commonMain/.../ui/InputBar.kt`, `VoiceButton` in `ChatStyleHelpers.kt`) is a starting point, not a spec.

## Owned paths

`sdk/`, new `sdk-voice/`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, root
`build.gradle.kts`, `.github/workflows/publish.yml`, `sample-app/`, `iosApp/`, `README.md`, `CLAUDE.md`.
Do not touch `Package.swift` (CI rewrites it on publish). Do not publish to Maven Central or push.

## 1. Core `:sdk` (stays microphone-free)

`verifyNoRecordingApis` is unchanged and must keep passing. It is a case-insensitive substring scan of
all of `sdk/src` (comments too): never write `AudioRecord` (so not `AudioRecorder`), `MediaRecorder`,
`recordPermission`, `RECORD_AUDIO`, `AVAudioRecorder`, `AVAudioApplication`, `AVCaptureDevice` there.

Public API (package `com.chatwoot.android.sdk.voice`; exact names may change if a check forces it, keep the shape):

```kotlin
public enum class MicAccess { Granted, Askable, Blocked }   // Blocked = the system will not prompt again
public class VoiceNoteClip(public val fileName: String, public val mimeType: String, public val bytes: ByteArray)
public interface VoiceNoteRecorder {
    public val access: MicAccess                 // Compose-observable (snapshot state)
    public fun requestAccess()                   // system prompt; result updates [access]
    public fun openSettings()                    // the app's system settings page
    public fun start(): Boolean                  // false = could not start; core shows nothing new
    public suspend fun stop(): VoiceNoteClip?    // null/empty = nothing usable; core sends nothing
    public fun cancel()                          // discard; deletes any temp file
    public val interruptions: Flow<Unit>         // audio interruption (call, Siri, mic silenced)
}
```

- `ChatPage(show, onFinish, styleConfig = DefaultStyle, modifier = …, voiceNoteRecorder: VoiceNoteRecorder? = null)`.
  Null keeps the 0.3.0 input bar exactly (no mic node at all). Keep the param last so existing call sites compile.
- `StyleConfig` additions with defaults: `micIcon: (@Composable () -> Unit)? = null` (canvas default glyph as the old
  `VoiceButton`), `voiceNoteRecordLabel = "Record voice note"`, `voiceNoteCancelLabel = "Cancel recording"`,
  `voiceNoteSendLabel = "Send voice note"`, `micAccessOffMessage = "Microphone access is off. Turn it on in Settings to send voice notes."`,
  `openSettingsLabel = "Open Settings"`. Labels are accessibility content descriptions on the controls.
- Internal commonMain state holder (pure logic, unit-tested with a fake recorder and a controllable clock/timer):
  - mic shown only when the text field is empty and a recorder is supplied; disabled while the input bar is disabled;
  - tap with `Granted` → `start()`; `Askable` → `requestAccess()`, and start automatically once access becomes
    `Granted` from that request; `Blocked` → show the inline note (above the input pill) with Open Settings →
    `openSettings()`; the note hides when access becomes `Granted` (re-check on resume);
  - recording row: red dot, elapsed `m:ss`, cancel (✕) and send; send → `stop()` → the existing
    `ChatViewModel.sendAttachment` path (`PickedFile` stays internal; map the clip to it);
  - auto stop-and-send at 2 minutes (internal named constant `VOICE_NOTE_MAX_DURATION_MS = 120_000`, KDoc'd);
  - discard (cancel) on lifecycle `ON_STOP`, on composable dispose (chat closed / `show=false`), and on any
    `interruptions` emission; a double tap on send cannot send twice.
- Tests (commonTest): state transitions above, the cap with the fake clock, discard paths, empty/null clip sends
  nothing, `Blocked` flow, auto-start after grant, no mic without a recorder. Add Compose UI tests if the module can
  run them (Android host and/or iOS simulator); otherwise test through the state holder only and say so.

## 2. New module `:sdk-voice` (`com.lascade.chatwoot:sdk-voice`)

KMP module mirroring `:sdk` targets (android library, iosArm64, iosSimulatorArm64), `explicitApi()`, `api(project(":sdk"))`.
Public entry: `@Composable public fun rememberVoiceNoteRecorder(): VoiceNoteRecorder` (commonMain expect, platform
actuals), so hosts need no platform code. It contributes **no** manifest permission; hosts declare `RECORD_AUDIO` /
`NSMicrophoneUsageDescription` (ADR 0006).

- Android: `MediaRecorder` (API 31+ context constructor), MPEG_4/AAC, 64 kbps, 44.1 kHz, mono, unique file in
  `cacheDir`, deleted after read/cancel/dispose. Permission through `rememberLauncherForActivityResult`;
  `Askable` vs `Blocked` via `shouldShowRequestPermissionRationale` after a denial (an app that was never asked is
  `Askable`; persist "asked once" in the module's own SharedPreferences key if needed to tell the two apart).
  Interruptions: `AudioManager.registerAudioRecordingCallback` with `isClientSilenced` on API 29+ (minSdk is 26; below 29
  rely on lifecycle discard), plus `MediaRecorder` error listener. Settings: `ACTION_APPLICATION_DETAILS_SETTINGS`.
- iOS: `AVAudioRecorder`, AAC, 44.1 kHz, mono, unique temp file deleted after read/cancel/dispose. Permission: the
  host deploys to iOS 16.4; use `AVAudioApplication` on 17+ and `AVAudioSession` record permission below, behind an
  availability check. Save the session's previous category, mode and options before recording and restore them
  (then deactivate with notify-others) after stop/cancel, so playback right after recording uses the speaker and the
  host's own audio setup survives. Interruptions: `AVAudioSessionInterruptionNotification` (began) → emit.
  Settings: `UIApplicationOpenSettingsURLString`.
- Tests: whatever logic is platform-free (file naming/cleanup helpers, access mapping) in commonTest/host tests.

## 3. Build and release plumbing

- One version source for both modules (e.g. `chatwoot.version=0.4.0` in `gradle.properties`); bump to **0.4.0**.
  Update `publish.yml` so version detection reads that source and the publish step runs
  `:sdk:publishToMavenCentral :sdk-voice:publishToMavenCentral`. The XCFramework/`Package.swift` steps stay `:sdk`-only.
- `sdk-voice` gets its own `pom {}` (name/description) consistent with `:sdk`.

## 4. Demos and docs

- Android sample: depend on `:sdk-voice`, declare `RECORD_AUDIO`, pass `rememberVoiceNoteRecorder()`.
- iOS demo: an app cannot embed two Kotlin frameworks sharing types. If straightforward, give `:sdk-voice` a demo
  framework that `export(project(":sdk"))` and switch the demo's embed task and Swift imports to it, with
  `NSMicrophoneUsageDescription` in its Info.plist. The published `ChatwootSDK` XCFramework stays `:sdk`-only.
  If this blocks, leave the iOS demo on `:sdk` and report why.
- README: install `sdk-voice`, the two host permission declarations, `ChatPage(voiceNoteRecorder = rememberVoiceNoteRecorder())`,
  new `StyleConfig` strings, and a 0.4.0 upgrade note (additive). `CLAUDE.md`: update the "No microphone API anywhere"
  invariant to "none in core `:sdk`; recording only in `:sdk-voice` (ADR 0006)", list the new module and ADR.

## Gates

Focused while iterating: `./gradlew :sdk:testAndroidHostTest` and the new module's tests. Final, once:
`./gradlew build` (includes `check`, `verifyNoRecordingApis`, iOS simulator tests) and the iOS demo `xcodebuild … build`
from `CLAUDE.md`. Also confirm `nm`/`strings` on the release `ChatwootSDK.xcframework` binary find no `AVAudioRecorder`
/ `requestRecordPermission`. Commit in small logical commits. End with `STATUS:` and the gate results.
