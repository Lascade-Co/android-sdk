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
            url: "https://github.com/Lascade-Co/android-sdk/releases/download/sdk-v0.1.0/ChatwootSDK.xcframework.zip",
            checksum: "75bcff2a15bfeb3e27e92f4362acaca484172ecd55b500a36d0c6ca79800fc16"
        )
    ]
)
