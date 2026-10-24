# 0005 — No voice-note recording; the SDK links no microphone API

Date: 2026-10-24 · Status: accepted · Supersedes the voice-note recording part of ADR 0003

## Context

ADR 0003 added voice-note recording (`MediaRecorder` / `AVAudioRecorder`) and 0.2.0 made it
opt-in through `Chatwoot.configure(..., voiceNotesEnabled)`. Opt-in only gated the code path at
runtime. The iOS static framework still linked `AVAudioRecorder` and
`AVAudioSession.requestRecordPermission`. App Store Connect scans the app binary for those
references and rejects any upload without `NSMicrophoneUsageDescription` (ITMS-90683), whether or
not the code runs. Travel Animator's iOS uploads with 0.2.0 were rejected for this reason. No
host uses voice notes.

## Decision

- Remove recording entirely: the recorder, the mic-permission controller, the mic button and
  recording UI in the input bar, `StyleConfig.micIcon`, and the `voiceNotesEnabled` parameter
  (with its `com.chatwoot.android.VOICE_NOTES_ENABLED` / `ChatwootVoiceNotesEnabled` fallback
  keys). This is a breaking API change, shipped as 0.3.0.
- The SDK neither requests nor merges `RECORD_AUDIO` on Android, and links no microphone API
  on iOS. Hosts need no microphone usage string or permission for the SDK.
- **Playback stays.** Audio attachments in the thread, such as an agent's voice note or an
  uploaded audio file, still play through the ADR 0003 players (Media3 / `AVPlayer`). Playback
  needs no permission. Contacts can still send audio files through the attachment picker.
- `:sdk:verifyNoRecordingApis` runs as part of `check` and fails if a recording API or
  `RECORD_AUDIO` reappears under `sdk/src/`.

## Rejected; do not retry

- **Keeping recording behind a runtime flag.** The App Store check reads linked symbols, not
  runtime behaviour, so a flag cannot prevent ITMS-90683.
- **Making every host declare `NSMicrophoneUsageDescription`.** That asks every host for a
  permission string for a feature nobody uses.

## Consequences

- Hosts must drop the `voiceNotesEnabled` argument and any `micIcon` style override when they
  upgrade. Stale manifest or Info.plist keys are ignored.
- Bringing recording back would need a separate opt-in artifact, such as its own module or
  framework, so hosts that do not use it never link it.
