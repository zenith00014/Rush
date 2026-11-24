# Rush app architecture

## Runtime structure

Rush is an Android application with a Kotlin host, a local HTML/CSS/JavaScript interface and a native Media3 playback service.

- app/src/main/java/com/rush/app/MainActivity.kt owns the WebView, system bars, inline native video view, immersive mode and Android Picture-in-Picture.
- app/src/main/java/com/rush/app/RushWebBridge.kt exposes a narrow @JavascriptInterface bridge for discovery, preferences, profile, permissions, playback, downloads and library operations.
- app/src/main/java/com/rush/app/RushPlaybackService.kt owns the Media3 session and background playback lifecycle.
- app/src/main/java/com/rush/app/profile/RushProfileStore.kt stores the local-only profile.
- app/src/main/java/com/rush/app/permissions/RushPermissionManager.kt reports permissions the app actually uses.
- app/src/main/assets/rush.html is the WebView shell.
- app/src/main/assets/styles/ contains the base style system and four-page experience styles.
- app/src/main/assets/scripts/ contains the app core, four-page screens, profile/settings UI and bootstrap.
- scripts/validate_rush_html.py checks static UI contracts, JavaScript/native method references, local asset ordering and Play configuration.

## Four-page navigation

1. Explore: All, Videos, Music, News and Movies filters. All combines vertical shelves and horizontal media rails. Video cards use 16:9 artwork, music cards use square artwork, movie discovery uses poster-shaped artwork and news uses compact image-left cards.
2. Search: focused search input, local search history, interest/topic shortcuts and broad provider search.
3. Subscribed: followed-source avatars and a combined following feed, with source-specific filtering.
4. Library: local profile, saved items, history, watchlist, liked items, playlists, queue, downloads and settings.

The watch page and music player are playback destinations, not extra primary bottom-navigation items. The watch page embeds the native video surface at the WebView's measured position; the same native view can be moved to the in-app mini-player or Android PiP. Music uses the MediaSession for Android notification/lock-screen controls.

## Loading and feed resilience

- Native Home feed responses are cached per profile/mode for three minutes.
- The WebView shows the cached response immediately where available, then accepts refreshed results.
- Home feed source lookups use bounded coroutine concurrency.
- Explore and Subscribed request background refresh when the app returns to the foreground and at a three-minute visible-page interval.
- First-load skeletons are only used when no cached content exists.
- Media3 buffering is surfaced in the native video surface and in the watch/player status UI.
- Reduced-motion settings disable decorative CSS movement.

## Engineering rules

- Keep Android platform work in Kotlin; keep view-only rendering and interaction in the WebView modules.
- Add @JavascriptInterface methods only for capabilities that have a real implementation on the native side.
- Keep WebView modules local. Do not load scripts, styles, fonts or UI frameworks from a CDN.
- Use WebViewAssetLoader; do not return to file:// access or loadDataWithBaseURL for a multi-file UI.
- Add permissions only for features that require them, request runtime permissions contextually and provide a route to Android app settings.
- Preserve cache-first feed behaviour and test stale/out-of-order responses.
- Keep each CSS/JS responsibility in its own file. Do not add new overrides to the HTML shell.
- Keep controls at accessible touch sizes, respect system insets and keyboard Back behaviour, and test small screens in portrait and landscape.
- Keep motion purposeful and respect prefers-reduced-motion.
- Treat third-party media rights and provider terms as release requirements, not as a runtime implementation detail.
