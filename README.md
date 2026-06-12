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
