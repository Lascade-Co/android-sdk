# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Chatwoot Android SDK, published to Maven Central under the package/group `com.chatwoot.android`. The public API surface is a single Jetpack Compose entry point:

```kotlin
ChatPage(show, onFinish, styleConfig = DefaultStyle)
```

The SDK wraps the Chatwoot website-widget HTTP API so Android apps get a native chat page instead of embedding the web widget.

The repo is currently being scaffolded. Its Gradle/publishing structure deliberately mirrors the sibling [`dependables`](../../Other/dependables) repo — when in doubt about build, publishing, or module conventions, look there first.

## Chatwoot Widget API (integration contract)

From https://github.com/chatwoot/android-sdk/wiki/Steps-to-build-the-integration — the SDK talks to a Chatwoot instance as a "website inbox" client:

1. **Bootstrap**: `GET <host>/widgets.json?website_token=${website_token}` — returns widget config (id, name, account id, color), an auth token, and a contact object (id, name, `pubsub_token`).
2. **Persist the token**: store the returned conversation token locally (DataStore/DB); append it as `cw_conversation=${token}` on all subsequent calls so the contact/conversation survives app restarts.
3. **Messages**: `GET /api/v1/widget/messages?website_token=${website_token}&cw_conversation=${token}` to fetch, `POST` to the same path to send. Send payload: `{ message: { content, timestamp, referer_url } }` (`referer_url` empty for now).
4. **Agents**: `GET /api/v1/widget/inbox_members?website_token=${website_token}`.

The wiki covers REST only. The `pubsub_token` exists for real-time updates (Rails ActionCable at `/cable`) but the wiki gives no instructions for it — verify against the Chatwoot web widget source before implementing websocket support.

## Module Layout (planned, mirroring dependables)

- Root `build.gradle.kts` — shared config only, no code: sets `group = "com.chatwoot.android"` for subprojects and centralises vanniktech maven-publish wiring (`publishToMavenCentral(automaticRelease = true)`, `signAllPublications()` only when `ORG_GRADLE_PROJECT_signingInMemoryKey` is set, config-cache opt-out for publish tasks per gradle/gradle#22779).
- SDK library module — `com.android.library` + `com.vanniktech.maven.publish`; owns its own `version = "x.y.z"` and `pom { }` block (root never sets versions). Source under `src/main/kotlin/com/chatwoot/android/`.
- `sample-app/` — minimal Android app exercising `ChatPage` end-to-end against a real Chatwoot inbox.
- Repositories (`google()`, `mavenCentral()`) live in `settings.gradle.kts` under `dependencyResolutionManagement` with `repositoriesMode = FAIL_ON_PROJECT_REPOS` — never redeclare them in subprojects.
- Dependency versions go in `gradle/libs.versions.toml` (version catalog), not inline.

## Common Commands

```bash
./gradlew build                              # build everything
./gradlew :<module>:build                    # build one module
./gradlew :<module>:test                     # unit tests for one module
./gradlew :<module>:test --tests "com.chatwoot.android.<TestClass>"   # single test class
./gradlew :<module>:publishToMavenLocal      # local publish, no signing needed
./gradlew :sample-app:installDebug           # install the demo on a connected device
```

## Publishing

- Maven Central via Sonatype Central Portal (vanniktech maven-publish plugin). `automaticRelease = true` means a successful publish auto-promotes — no manual release click.
- Credentials come from env vars: `ORG_GRADLE_PROJECT_mavenCentralUsername`, `ORG_GRADLE_PROJECT_mavenCentralPassword`, `ORG_GRADLE_PROJECT_signingInMemoryKey`, `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` (CI: GitHub secrets `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`, `SIGNING_KEY_PASSWORD`, `SIGNING_KEY_ID`).
- CI publishes only when a module's `version = "..."` line changes in a push to the default branch (Maven Central rejects republishing the same GAV); `workflow_dispatch` accepts an explicit module list. See `dependables/.github/workflows/publish.yml` for the reference workflow.
- For the Android library publication use `AndroidSingleVariantLibrary(variant = "release", sourcesJar = true, publishJavadocJar = false)` — AGP 9's bundled Dokka can't read Kotlin 2.3 metadata, and Maven Central accepts sources-only Kotlin publishes.

## Key Conventions

- JVM target 21: `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }` with matching `compileOptions`.
- AGP 9.x, Gradle 9.4+, config cache and build cache enabled in `gradle.properties`.
- The SDK module ships `consumer-rules.pro`; keep the public Compose API stable — `show`/`onFinish`/`styleConfig` is the whole contract, everything else stays `internal`.
- `styleConfig` defaults to a `DefaultStyle`; all theming flows through that one config object rather than scattered parameters.
