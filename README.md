# Rush for Android

Rush is an Android media and discovery app built with Kotlin, AndroidX Media3 and a local HTML/CSS/JavaScript interface hosted in an Android WebView. Native Android components handle media playback, downloads, notifications, background playback, immersive video and Picture-in-Picture.

## Current product scope

Rush has four primary destinations:

1. Explore: All, Videos, Music, News and Movies filters. All is a vertical sequence of content-led shelves with horizontal scrolling. Video artwork is 16:9, music artwork is square, movie discovery uses poster-shaped cards, and news uses compact image-left headline cards.
2. Search: focused cross-provider search with local recent-search history, followed creators and topic shortcuts.
3. Subscribed: followed channel, publisher and musician avatars with a combined following feed and source-specific views.
4. Library: local profile, saved media, watch/listening history, watchlist, liked items, playlists, queue, downloads and settings.

The watch page and retro CD-style music player are media experiences opened from Explore, Search or Subscribed; they are not additional bottom-navigation destinations. Video playback uses a native Media3 surface positioned inside the watch page, then repositions that same surface as an in-app mini-player or Android Picture-in-Picture. Music uses the Android media session for notification and lock-screen controls.

The Movies filter currently searches long-form YouTube results and displays them in poster-shaped tiles. It is not a licensed film catalogue or a Netflix-equivalent service.

The first-run profile is stored locally on the device. Notification access is optional and requested contextually. The current app does not ask for camera, microphone, contacts, location or shared-storage permission.

## Recommendation behavior

For You draws from all followed YouTube channels; following is enough to supply candidates. Productive and informative material is ranked higher, rather than requiring a separate channel eligibility flag. Selected topics help rank videos across different subject areas, so productive recommendations are not limited to one discipline. Low-focus topics such as memes, pranks and viral clips remain available in topic selection and unrestricted search, but receive a ranking penalty in recommendation feeds.

Following collects content from sources the user follows. Music suggestions use followed musicians and recent listening history. News suggestions use selected topics and followed publishers, with contextual/reporting signals prioritized over low-information viral terms. Search remains broader than recommendation feeds and is not filtered by the selected topics or productive-channel pool.

## Discovery and search engine

Rush uses one shared on-device intelligence layer plus dedicated video, news, music, channel and playlist scorers. Shared signals combine selected interests, follows, local history, freshness, metadata completeness and novelty with a section specialist. The two layers are combined for recommendations. Video and news feeds prioritise useful, informative material; music remains taste-led.

Search is deliberately broader than recommendation feeds. It does not filter by selected topics, followed-source status or productivity classification. Search ranking uses BM25-style lexical relevance, title/source/summary field weights, phrase and prefix matching, typo tolerance and a small spam-quality adjustment. Low-focus and entertainment results remain searchable. See `docs/ALGORITHM_ARCHITECTURE.md` for the scoring model and current limitations.

## Project layout

- `app/src/main/java/com/rush/app/`: Android application, WebView bridge, local library, playback, downloads, search and preference integration
- `app/src/test/java/com/rush/app/`: regression tests for local library, playlists, queue and feed preferences
- `scripts/validate_rush_html.py`: checks modular asset loading, four-page UI contracts, JavaScript/native bridge references and Android release configuration
- `app/src/main/java/com/github/libretube/`: LibreTube-derived adaptive playback implementation
- `app/src/main/assets/rush.html`: local WebView shell\n- `app/src/main/assets/styles/`: base and four-page design styles\n- `app/src/main/assets/scripts/`: app core, four-page screens, profile/settings and startup modules
- `app/src/main/proto/`: protocol schemas used by the adaptive playback implementation
- `app/src/main/res/`: Android manifest resources, app label and themes
- `.github/workflows/`: CI build and APK artifact workflow
- `LICENSES/` and `THIRD_PARTY_NOTICES.md`: third-party license texts and attribution

The modular WebView assets are the current application UI and are not safe to delete as an unused prototype. Likewise, the LibreTube-derived player and protobuf files are runtime dependencies, not disposable legacy files.

## Build locally

Requirements: JDK 17, Android SDK Platform 36, Android Build Tools 36.0.0 and Gradle 8.11.1 (Android Gradle Plugin 8.9.2).

```bash
python3 scripts/validate_rush_html.py
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.\n\nRelease bundle output: `app/build/outputs/bundle/release/app-release.aab`. The AAB is unsigned unless the four RUSH_UPLOAD_* environment variables are configured. See `docs/PLAY_STORE_READINESS.md`.

## Build on GitHub

The **Build Rush Android APK** workflow runs on pushes to the supported branches and can also be started manually from GitHub Actions. It uploads a `rush-debug-apk` artifact and a `rush-release-aab` artifact. The release bundle is signed only when the upload-key secrets are configured.

A successful build verifies compilation and packaging only. It does not verify live content extraction, stream playback, news-source coverage, recommendation relevance, offline download integrity or device-specific behaviour; those require testing on physical Android devices. A successful AAB build is not evidence of Google Play content-policy approval.

## Compatibility and licensing

YouTube extraction uses unofficial endpoints and NewPipeExtractor, so upstream service changes may affect compatibility. News discovery currently reads public Google News RSS search feeds. Use offline downloads only for content and methods you are permitted to keep offline. The current unofficial YouTube extraction and download path is a Play submission blocker until its use is expressly authorised or replaced with permitted sources. See `docs/PLAY_STORE_READINESS.md`.

The playback implementation adapts LibreTube/NewPipeExtractor code under GPL-3.0-or-later. MP3 conversion uses a community-maintained FFmpegKit-compatible audio package under LGPL-3.0. Read `THIRD_PARTY_NOTICES.md` and the included license texts before distributing builds.
