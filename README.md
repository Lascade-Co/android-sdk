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
implementation("com.chatwoot.android:sdk:0.1.0")
```

**iOS (Swift Package Manager):** add this repo as a package dependency — the
`ChatwootSDK` binary target ships as an XCFramework attached to GitHub releases.

## Setup

Create a *website inbox* in your Chatwoot dashboard, then configure the SDK once at startup:

```kotlin
Chatwoot.configure(
    baseUrl = "https://app.chatwoot.com",   // or your self-hosted URL
    websiteToken = "<website inbox token>",
    voiceNotesEnabled = false,               // see "Voice notes" below
)
```

Or declare it statically instead:

- **Android** — manifest `<meta-data>`: `com.chatwoot.android.BASE_URL` and
  `com.chatwoot.android.WEBSITE_TOKEN`, plus optional boolean
  `com.chatwoot.android.VOICE_NOTES_ENABLED`
- **iOS** — Info.plist keys: `ChatwootBaseUrl` and `ChatwootWebsiteToken`, plus optional
  boolean `ChatwootVoiceNotesEnabled`

Then show the chat. On Android/Compose, call `ChatPage` (above). From Swift:

```swift
ChatPageViewControllerKt.ChatPageViewController(
    onFinish: { /* dismiss */ },
    styleConfig: StyleConfigKt.DefaultStyle
)
```

Everything else — anonymous contact creation, conversation persistence across launches,
history, live agent replies, typing indicators, reconnection — is handled inside.

## Voice notes (opt-in)

With `voiceNotesEnabled = false` the input bar shows no mic button and the SDK never touches
the microphone, so apps that don't declare a mic permission are safe. The static
manifest/Info.plist keys default to false when absent. Sending pre-recorded audio files through
the attachment picker works either way.

To enable it, pass `voiceNotesEnabled = true` to `configure` (or set the static key to true) and
declare the permission:

- **Android** — add `<uses-permission android:name="android.permission.RECORD_AUDIO" />` to
  your app manifest. The SDK requests it at runtime on the first mic tap.
- **iOS** — add `NSMicrophoneUsageDescription` (with a user-facing reason) to your app's
  `Info.plist`. iOS aborts at permission-request time if the key is missing, and the SDK
  cannot supply it on your behalf.

If the user denies the permission, the mic button is disabled.

Picking image/video/file attachments needs no runtime permission on either platform.

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

## Layout

```
├── sdk/          # the KMP library (commonMain + androidMain/iosMain)
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
