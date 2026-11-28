# Rush for Android

Rush is a modified Android client based on the Flow Android Client source tree. It uses Kotlin, Jetpack Compose, Material 3, Media3/ExoPlayer, Room, DataStore and upstream playback and discovery integrations.

## Rush changes

- Rebranded package and application ID: com.rush.app.
- Replaced the previous Rush WebView shell with Flow's native Android application architecture.
- Retained video playback, subscriptions, search, music, library, watch history, playlists, downloads and settings.
- Disabled Shorts navigation, home shelves, channel tabs and Shorts-specific settings in the Rush UI.
- Added RushRankingMixer, a staged recommendation combiner for relevance, discovery, source affinity, quality, freshness and feedback guardrails.

## Attribution and license

Rush is a modified derivative of Flow Android Client: https://github.com/Flow-Tube/Flow. The upstream source is licensed under GNU GPL version 3. This public repository keeps the GPL-3.0 license and makes the corresponding modified source available.

The recommendation engine originates from FlowNeuro. Rush credits **Flow Android Client** and links to the original repository as required by the upstream source notice. Original source headers and copyright notices are retained.

See LICENSE and THIRD_PARTY_NOTICES.md before redistributing builds.

## Build

Requirements: JDK 21, Android SDK and the included Gradle wrapper.

./gradlew :app:assembleFossDebug
./gradlew :app:testFossDebugUnitTest
