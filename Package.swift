// swift-tools-version:5.9
// Swift Package Manager distribution for pure-Swift apps. The binary XCFramework is built
// and attached to GitHub Releases by .github/workflows/publish.yml, which also rewrites the
// url + checksum below on every release. Until the first release exists, integrate via the
// local Gradle build instead (see iosApp/).
import PackageDescription

let package = Package(
    name: "ChatwootSDK",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(name: "ChatwootSDK", targets: ["ChatwootSDK"])
    ],
    targets: [
        .binaryTarget(
            name: "ChatwootSDK",
            url: "https://github.com/Lascade-Co/android-sdk/releases/download/sdk-v0.2.0/ChatwootSDK.xcframework.zip",
            checksum: "5695027f62f4b71e7724a9a339e5ee932005aea2cdd965f6c49fa16e0370ef6d"
        )
    ]
)
