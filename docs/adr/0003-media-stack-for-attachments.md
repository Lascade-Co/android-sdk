# 0003 — Media stack for attachments: Coil + native players + FileKit

Date: 2026-06-15 · Status: accepted; voice-note recording superseded by ADR 0005

## Context

Phase 1 of attachments needs to render images inline, play video on tap, play audio as
voice notes, and let the contact pick a file — across Android and iOS from `commonMain`.
There is no single mature Kotlin Multiplatform library covering image loading, video/audio
playback, and file picking, so each capability is chosen separately. Phase 2 added voice-note
*recording* on the same principle. ADR 0005 has since removed recording.

## Decision

- **Images:** Coil 3 (`coil-compose` + `coil-network-ktor3`), loading over the SDK's existing
  Ktor stack. A `SingletonImageLoader` factory registers the Ktor fetcher so iOS works too
  (it has no JVM ServiceLoader auto-registration).
- **Video & audio playback:** hand-rolled `expect`/`actual` players — Media3/ExoPlayer
  (`media3-exoplayer` + `media3-ui`) on Android, `AVPlayer`/`AVPlayerViewController` on iOS.
  No third-party multiplayer dependency; keeps `commonMain` thin (ADR 0002).
- **Voice-note recording (Phase 2): removed by ADR 0005.** Gating it behind `voiceNotesEnabled`
  did not keep `AVAudioRecorder` / `requestRecordPermission` out of the linked iOS binary, and App
  Store Connect rejects such binaries without `NSMicrophoneUsageDescription` (ITMS-90683).
  The SDK now records nothing. Audio *playback*, above, is unaffected.
- **Picker:** FileKit (`filekit-dialogs-compose`) — one `commonMain` call returns the picked
  file, using PHPicker/UIDocumentPicker on iOS and the Photo Picker/OpenDocument on Android,
  all permission-free. The alternative (rolling our own `expect`/`actual` PHPicker delegate
  glue) was rejected as the fiddliest, lowest-value platform code.

## Consequences

- A future reader sees AVPlayer/Media3 code in the platform source rather than one shared
  player abstraction — that's deliberate; native players give the most control with no media
  lock-in, at the cost of two implementations to maintain.
- Three new dependencies enter the consumer's app (Coil, FileKit on both platforms; Media3 on
  Android). Each is independently swappable.
- Picked files are read fully into memory before upload (Phase 1); large media is bounded by
  Chatwoot's server limit (default 40 MB). Streaming uploads are a later optimization.
