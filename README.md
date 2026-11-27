# Rush — YouTube Video Client for Android

Rush is an independent Android client focused on YouTube videos. The interface is rebuilt around a familiar video-discovery and watch experience, with a responsive desktop-style sidebar and mobile bottom navigation. Native AndroidX Media3 handles playback, background media controls and Picture-in-Picture.

## Product scope

- YouTube video search and playback with adaptive streams, captions where available and native full-screen controls
- A video-only Home feed with **For you** and **Following** modes
- Search and follow YouTube channels; follows are stored locally on this device
- Browse and queue public YouTube video playlists
- Watch Later, local playlists, watch history and an up-next queue
- Offline video downloads, Android media notifications and Picture-in-Picture
- No Shorts tab, Shorts feed, vertical swipe viewer or dedicated Shorts navigation
- No Rush-placed display, banner, interstitial or video-ad UI; this project does not include an advertising SDK

## Shorts filtering

YouTube's public extractor responses do not consistently expose a reliable Shorts/non-Shorts field. Rush therefore filters out videos whose title contains a Shorts marker and videos whose reported duration is three minutes or less. This is intentionally conservative and can also hide ordinary short videos. A Shorts item with no duration and no title marker may still pass through if YouTube changes the extractor response.

## What is not identical to the official YouTube app

Rush is not an official Google/YouTube app and does not currently authenticate with a Google account. It cannot perform account-backed actions such as likes/dislikes, comments, live chat, creator uploads, YouTube Premium features, or cloud-synchronised subscriptions, history and playlists. Channel follows, Watch Later, playlists and history in this build are local to the device.

“No ads” describes the Rush interface and its lack of an advertising SDK or Rush-inserted ad placements. Rush does not promise to remove a creator's in-video sponsorship, a promotion embedded in the uploaded video, or every advertising behaviour that may be delivered by YouTube's changing playback infrastructure.

This is a substantial YouTube-focused client rebuild, not a claim of one-to-one parity with Google's private application, services, or account APIs.

## Project layout

- `app/src/main/java/com/rush/app/`: Android activity, WebView bridge, YouTube extraction, playback, local library and downloads
- `app/src/main/java/com/github/libretube/`: adaptive streaming implementation adapted from LibreTube/NewPipeExtractor
- `app/src/main/assets/rush.html`: responsive Rush video interface
- `app/src/test/java/com/rush/app/`: unit and regression tests, including Shorts filtering
- `scripts/validate_rush_html.py`: validates the WebView, native bridge, Shorts exclusion and JavaScript syntax
- `app/src/main/proto/`: protobuf schemas used by the adaptive streaming implementation
- `LICENSES/` and `THIRD_PARTY_NOTICES.md`: third-party licence texts and attribution

The adaptive streaming implementation and protocol sources are runtime dependencies, not unused prototype code.

## Build locally

Requirements: JDK 17, Android SDK Platform 36, Android Build Tools 36.0.0 and Gradle 8.11.1 (Android Gradle Plugin 8.9.2).

```bash
python3 scripts/validate_rush_html.py
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions

The **Build Rush Android APK** workflow validates the WebView and native bridge, runs Android lint and unit tests, builds the debug APK, and uploads it as the `rush-debug-apk` artifact.

A successful build confirms compilation and packaging. It does not by itself verify live YouTube extraction, playback compatibility, download integrity or device-specific PiP behaviour; those need testing on physical Android devices.

## Compatibility and licensing

YouTube extraction uses unofficial extractor endpoints and NewPipeExtractor. YouTube can change those endpoints at any time, which may interrupt search or playback until the extractor is updated. Only download content and use offline playback when you have permission to do so.

The adaptive playback implementation is derived from LibreTube/NewPipeExtractor under GPL-3.0-or-later. Read `THIRD_PARTY_NOTICES.md` and the included licence texts before distributing builds.
