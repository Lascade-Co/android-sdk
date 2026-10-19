import SwiftUI
import ChatwootSDK

@main
struct iOSApp: App {
    init() {
        // Or set ChatwootBaseUrl / ChatwootWebsiteToken (/ ChatwootVoiceNotesEnabled) in Info.plist instead.
        Chatwoot.shared.configure(
            baseUrl: "https://app.chatwoot.com",
            websiteToken: "YOUR_WEBSITE_TOKEN",
            voiceNotesEnabled: true // requires NSMicrophoneUsageDescription in Info.plist
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
