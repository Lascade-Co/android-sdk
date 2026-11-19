# 0006 — Voice-note recording lives in the opt-in `sdk-voice` module

Date: 2026-11-19 · Status: accepted · Partly supersedes ADR 0005 (its "no recording anywhere" scope)

## Context

ADR 0005 removed recording because a runtime flag cannot keep `AVAudioRecorder` /
`requestRecordPermission` out of a linked iOS binary, and App Store Connect rejects such a binary
without `NSMicrophoneUsageDescription` (ITMS-90683). Its consequences named the way back: a separate
opt-in artifact that hosts without voice notes never link. Travel Animator now wants contacts to
record voice notes.

## Decision

- Recording ships as a second published artifact, `com.lascade.chatwoot:sdk-voice`, versioned with
  `:sdk` (first release 0.4.0). It holds only the platform recorder, the microphone permission
  request and the open-app-settings action. It depends on `:sdk`.
- Core `:sdk` stays microphone-free and `verifyNoRecordingApis` keeps guarding it unchanged. Core owns
  a public `VoiceNoteRecorder` interface, the mic button, the recording row and the recording state
  logic (length cap, cancel, discard on interruption, denial flow). `ChatPage` gains an optional
  `voiceNoteRecorder` parameter; with none supplied, the input bar is exactly the 0.3.0 one.
- Interaction: tap the mic (shown when the text field is empty) to start; the input row becomes a
  red dot, elapsed time, cancel and send. A voice note is AAC in an `.m4a` file, sent as an ordinary
  audio **attachment** through multipart `POST /messages`. Recording stops and sends automatically at
  2 minutes. Backgrounding, an audio interruption (such as a call) or closing the chat discards the
  partial clip. There is no pre-send preview.
- Permission: the first mic tap asks the system. After a denial the mic stays enabled; tapping it
  shows an inline "microphone access is off" note with an Open Settings action.
- All new user-visible text comes through `StyleConfig` with English defaults, like `inputHint`.
- A host that adds `sdk-voice` declares `RECORD_AUDIO` (Android) and `NSMicrophoneUsageDescription`
  (iOS) itself; `sdk-voice` contributes no manifest permission.

## Rejected; do not retry

- **Recording back in core `:sdk` behind a flag or parameter.** ADR 0005's reasons still hold:
  the App Store check reads linked symbols, and every host would need a microphone usage string.
- **Hold-to-record with slide-to-cancel.** Harder to make accessible and to get gestures right in
  Compose on both platforms; tap-to-toggle covers the need.

## Consequences

- Swift Package Manager consumers get no `sdk-voice` XCFramework yet; adding one later is additive.
  Kotlin/Native apps cannot embed two Kotlin frameworks that share types, so an iOS host links
  `sdk-voice` through its own single Kotlin framework (as Travel Animator's shared framework does).
- Hosts that do not add `sdk-voice` see no change, need no permission and pass ITMS-90683.
