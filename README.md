# Rush for Android

Rush is an Android media and discovery app built with Kotlin, AndroidX Media3 and a local HTML/CSS/JavaScript interface hosted in an Android WebView. Native Android components handle media playback, downloads, notifications, background playback, immersive video and Picture-in-Picture.

## Current product scope

- YouTube video search and playback with native playback controls, captions where available and immersive full-screen viewing
- YouTube Music search and playback, history, lyrics and taste-based suggestions derived from followed musicians and listening history
- Source follows for YouTube channels, news publishers and musicians
- A two-mode Home feed: For You and Following
- User-selected topics across learning, culture, entertainment and low-focus/viral categories
- For You recommendations limited to YouTube channels the user explicitly includes in the productive-channel pool
- News discovery and publisher-aware suggestions using topic relevance, reporting/context signals and followed publishers
- Broad search across videos, music, news, YouTube channels and public video playlists; playlist tracks can be browsed and queued locally, and recommendation scoring is not applied as a search restriction
- Public video playlists, local playlists, watch/listening history and offline downloads
- Media3 background playback, notifications, app-private downloads and Picture-in-Picture

Movies and TV are temporarily hidden from the app while a permitted, reliable movie source is evaluated. They are not part of the current build.

## Recommendation behavior

For You draws from all followed YouTube channels; following is enough to supply candidates. Productive and informative material is ranked higher, rather than requiring a separate channel eligibility flag. Selected topics help rank videos across different subject areas, so productive recommendations are not limited to one discipline. Low-focus topics such as memes, pranks and viral clips remain available in topic selection and unrestricted search, but receive a ranking penalty in recommendation feeds.

Following collects content from sources the user follows. Music suggestions use followed musicians and recent listening history. News suggestions use selected topics and followed publishers, with contextual/reporting signals prioritized over low-information viral terms. Search remains broader than recommendation feeds and is not filtered by the selected topics or productive-channel pool.

## Discovery and search engine

Rush uses one shared on-device intelligence layer plus dedicated video, news, music, channel and playlist scorers. Shared signals combine selected interests, follows, local history, freshness, metadata completeness and novelty with a section specialist. The two layers are combined for recommendations. Video and news feeds prioritise useful, informative material; music remains taste-led.

Search is deliberately broader than recommendation feeds. It does not filter by selected topics, followed-source status or productivity classification. Search ranking uses BM25-style lexical relevance, title/source/summary field weights, phrase and prefix matching, typo tolerance and a small spam-quality adjustment. Low-focus and entertainment results remain searchable. See `docs/ALGORITHM_ARCHITECTURE.md` for the scoring model and current limitations.

## Project layout

- `app/src/main/java/com/rush/app/`: Android application, WebView bridge, local library, playback, downloads, search and preference integration
- `app/src/test/java/com/rush/app/`: regression tests for local library, playlists, queue and feed preferences
- `scripts/validate_rush_html.py`: checks the WebView shell, HTML IDs, embedded JavaScript syntax and native bridge method references
- `app/src/main/java/com/github/libretube/`: LibreTube-derived adaptive playback implementation
- `app/src/main/assets/`: local WebView application and proof-of-origin helper assets
- `app/src/main/proto/`: protocol schemas used by the adaptive playback implementation
- `app/src/main/res/`: Android manifest resources, app label and themes
- `.github/workflows/`: CI build and APK artifact workflow
- `LICENSES/` and `THIRD_PARTY_NOTICES.md`: third-party license texts and attribution

The WebView asset is part of the current application UI and is not safe to delete as an unused prototype. Likewise, the LibreTube-derived player and protobuf files are runtime dependencies, not disposable legacy files.

## Build locally

Requirements: JDK 17, Android SDK Platform 36, Android Build Tools 36.0.0 and Gradle 8.11.1 (Android Gradle Plugin 8.9.2).

```bash
python3 scripts/validate_rush_html.py
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Build on GitHub

The **Build Rush Android APK** workflow runs on pushes to the supported branches and can also be started manually from GitHub Actions. Download the `rush-debug-apk` artifact from a successful run.

A successful build verifies compilation and packaging only. It does not verify live YouTube extraction, stream playback, news-source coverage, recommendation relevance, offline download integrity or device-specific behavior; those require testing on physical Android devices.

## Compatibility and licensing

YouTube extraction uses unofficial endpoints and NewPipeExtractor, so upstream service changes may affect compatibility. News discovery currently reads public Google News RSS search feeds. Use offline downloads only for content and methods you are permitted to keep offline.

The playback implementation adapts LibreTube/NewPipeExtractor code under GPL-3.0-or-later. MP3 conversion uses a community-maintained FFmpegKit-compatible audio package under LGPL-3.0. Read `THIRD_PARTY_NOTICES.md` and the included license texts before distributing builds.
