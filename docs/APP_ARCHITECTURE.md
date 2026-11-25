# Rush app architecture

## Runtime structure

Rush is an Android application with a Kotlin host, a local HTML/CSS/JavaScript interface and a native Media3 playback service.

- `MainActivity.kt` owns the WebView, system bars, native video surface, immersive mode and Android Picture-in-Picture.
- `RushWebBridge.kt` exposes the native discovery, preferences, profile, permissions, playback and library actions used by the UI.
- `RushPlaybackService.kt` owns the Media3 session and background playback lifecycle.
- `app/src/main/assets/rush.html` is the small WebView shell.
- `app/src/main/assets/styles/figma-redesign.css` contains the reference-led responsive visual system.
- `app/src/main/assets/scripts/figma-experience.js` contains the active screens, navigation, feed and interaction logic.
- `scripts/validate_rush_html.py` checks the active asset graph, JavaScript/native bridge contract, design scope and Android configuration.

## Design boundary

The primary visual reference is Juxtopposed's YouTube Redesign Figma Community file, ID `1450380484645543336`. The reference governs the app shell, header, collapsible sidebar, topic strip, video cards, feed shelves, Shorts presentation, search, watch page, library hierarchy and interaction density.

Rush keeps its own name and identity. It must not use YouTube's logo or wordmark, or imply affiliation.

Only implement these reference surfaces:

- Home and Explore video feeds
- Topic chips and topic personalisation
- Shorts and TV Mode
- Search for videos and channels
- Subscriptions and channel-specific uploads
- Video watch page and native playback controls
- History, Watch Later, Liked Videos, Playlists and Collections
- The visible header actions and the settings required to support those surfaces

News, music-player, movie browsing, and unrelated Rush-only screens are outside this rebuild. Do not add them back as secondary tabs, hidden routes, feed shelves or extra navigation.

## Navigation and layout

- Use the reference's collapsible left sidebar on wide screens and a dismissible drawer on phones.
- Do not add a duplicate bottom navigation bar.
- Keep one horizontal topic row on Home/Explore. Topic chips filter the existing personalised feed; the settings action opens topic personalisation. Topics are not a second navigation system.
- Home uses a horizontally browsable "In Case You Missed" shelf, a responsive video grid and a separate Shorts shelf.
- Search presents video and channel results with loading, empty and error states.
- TV Mode reuses real feed data with a viewing-distance layout; it does not invent a separate content type.
- The watch page embeds the native video surface at its measured WebView position. The same native view can move to the mini-player or Android PiP.

## Data and playback

- Kotlin owns network-facing feed discovery, ranking, local preferences and library persistence.
- The native discovery engine ranks video candidates using selected topics, followed channels and user feedback; the WebView renders the returned results.
- Topic filtering is local against topic tags returned with the personalised feed, avoiding a second competing search request.
- Video playback, adaptive stream selection, buffering, audio focus and notification controls remain native Media3 responsibilities.
- Subscriptions, history, Watch Later, liked state, playlists and collections use the native bridge and local app storage.
- The app must not fabricate creators, view counts, profile photos, notifications or video content. Initial-based avatars are honest fallbacks.

## Engineering rules

- Keep Android platform work in Kotlin and view rendering/interaction in the WebView modules.
- Add JavaScript bridge methods only for capabilities implemented on the native side.
- Keep all first-party scripts, styles and fonts local. Do not load UI frameworks or fonts from a CDN.
- Serve packaged assets through AndroidX WebViewAssetLoader; do not return to `file://` access.
- Request only permissions required by active features, contextually, with a route to Android app settings.
- Preserve cache-first feed behaviour, bounded network concurrency and stale/out-of-order response handling.
- Reserve image aspect ratios, lazy-load offscreen artwork and avoid replacing unchanged DOM.
- Keep controls accessible, respect system insets, font scaling, reduced motion and keyboard Back behaviour.
- Treat provider terms and media rights as release requirements, not as a runtime implementation detail.
