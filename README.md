# Chatwoot SDK

A Kotlin Multiplatform chat SDK for [Chatwoot](https://www.chatwoot.com) — one Compose
chat page that runs natively on **Android and iOS**, backed by the Chatwoot website-widget
API with live messages over websocket.

```kotlin
ChatPage(
    show = showChat,
    onFinish = { showChat = false },
    styleConfig = DefaultStyle, // optional theming
)
```

## Install

**Android / KMP (Maven Central):**

```kotlin
implementation("com.lascade.chatwoot:sdk:0.4.0")
```

**iOS (Swift Package Manager):** add this repo as a package dependency — the
`ChatwootSDK` binary target ships as an XCFramework attached to GitHub releases.
The Swift package provides the core chat SDK without voice-note capture.

**Voice notes in a Kotlin Multiplatform or Android app (opt in):** use the voice module
instead of a direct dependency on `sdk`. It exposes the core SDK transitively:

```kotlin
implementation("com.lascade.chatwoot:sdk-voice:0.4.0")
```

Then supply its platform recorder from Compose:

```kotlin
ChatPage(
    show = showChat,
    onFinish = { showChat = false },
    voiceNoteRecorder = rememberVoiceNoteRecorder(),
)
```

If `voiceNoteRecorder` is omitted, the input bar has no microphone control.

## Setup

Create a *website inbox* in your Chatwoot dashboard, then configure the SDK once at startup:

```kotlin
Chatwoot.configure(
    baseUrl = "https://app.chatwoot.com",   // or your self-hosted URL
    websiteToken = "<website inbox token>",
)
```

Or declare it statically instead:

- **Android** — manifest `<meta-data>`: `com.chatwoot.android.BASE_URL` and
  `com.chatwoot.android.WEBSITE_TOKEN`
- **iOS** — Info.plist keys: `ChatwootBaseUrl` and `ChatwootWebsiteToken`

Then show the chat. On Android/Compose, call `ChatPage` (above). From Swift:

```swift
ChatPageViewControllerKt.ChatPageViewController(
    onFinish: { /* dismiss */ },
    styleConfig: StyleConfigKt.DefaultStyle
)
```

Everything else — anonymous contact creation, conversation persistence across launches,
history, live agent replies, typing indicators, reconnection — is handled inside.

## Permissions

Core `sdk` needs no microphone permission and links no recording API. Audio attachments
in the thread, such as an agent's voice note, still play, and contacts can send audio
files through the attachment picker.

If you add `sdk-voice`, declare the permission in the **host app**. The module does not
contribute an Android permission or an iOS usage string:

```xml
<!-- Android app's AndroidManifest.xml -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

```xml
<!-- iOS app's Info.plist -->
<key>NSMicrophoneUsageDescription</key>
<string>Record voice notes to send in chat.</string>
```

The iOS demo builds one `ChatwootSDKVoice` framework that exports the core SDK. Swift
Package Manager consumers only receive the core `ChatwootSDK` XCFramework for now.

Picking image/video/file attachments needs no runtime permission on either platform.

### Upgrading to 0.4.0

Voice notes return as an additive, opt-in module. Existing `ChatPage` calls keep the
0.3.0 input bar. To enable the microphone button, add `sdk-voice`, declare the host
permission above, and pass `rememberVoiceNoteRecorder()`. Voice notes are sent as
ordinary audio attachments and are capped at two minutes.

### Upgrading from 0.2.x

0.3.0 removes voice-note recording. Drop the `voiceNotesEnabled` argument from `configure`
and any `micIcon` override from your `StyleConfig`. The `VOICE_NOTES_ENABLED` /
`ChatwootVoiceNotesEnabled` keys are now ignored and can be deleted.

## Identifying the visitor

By default the contact is anonymous. If your app knows who the user is, identify them so agents
see a named contact and conversations follow the user across reinstalls/devices. Call it any
time (typically after your own login), before or while `ChatPage` is shown:

```kotlin
Chatwoot.setUser(
    identifier = "your-user-id",          // stable id; recognises the same person later
    name = "Ada Lovelace",
    email = "ada@example.com",
    phoneNumber = "+15551234567",
    customAttributes = mapOf("plan" to "pro"),
    identifierHash = serverComputedHash,  // only if the inbox enforces identity validation
)

Chatwoot.setCustomAttributes(mapOf("plan" to "enterprise"))  // merge more attributes later
Chatwoot.reset()                                             // on logout — forgets the session
```

**Identity validation (optional):** if you enable it on the inbox, `identifierHash` is required.
It must be computed **on your backend** as `HMAC-SHA256(hmacToken, identifier)` — the HMAC token
is a secret and must never ship inside the app. Passing a different `identifier` than the active
session starts a fresh contact + conversation on the next `ChatPage` open.

## Theming

All visual customisation flows through one object:

```kotlin
ChatPage(show, onFinish, styleConfig = DefaultStyle.copy(
    primaryColor = Color(0xFF7C3AED),
    title = "Support",
))
```

For voice-note controls, `StyleConfig` also provides `micIcon`,
`voiceNoteRecordLabel`, `voiceNoteCancelLabel`, `voiceNoteSendLabel`,
`micAccessOffMessage`, and `openSettingsLabel`. The labels have English defaults;
replace them for your app's language and accessibility text.

## Layout

```
├── sdk/          # the KMP library (commonMain + androidMain/iosMain)
├── sdk-voice/    # opt-in Android/iOS voice-note recorder
├── sample-app/   # Android demo — put chatwoot.baseUrl / chatwoot.websiteToken in local.properties
├── iosApp/       # iOS demo (Xcode project, builds the framework via Gradle)
├── docs/adr/     # architecture decision records
└── CONTEXT.md    # domain glossary + verified protocol contract
```

## Build

```bash
./gradlew build                                      # everything incl. tests (needs macOS for iOS targets)
./gradlew :sample-app:installDebug                   # Android demo on a connected device
./gradlew :sdk:assembleChatwootSDKReleaseXCFramework # ChatwootSDK.xcframework for Swift apps
open iosApp/iosApp.xcodeproj                         # iOS demo
```

## License

MIT — see [LICENSE](LICENSE).
