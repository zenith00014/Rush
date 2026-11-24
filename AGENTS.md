# Rush contributor guide

Rush's product and interaction source of truth is the current four-page product brief and the wireframe prototype. Do not restore the older Home/Videos/Music/News/Library navigation.

## Product structure

- Keep exactly four persistent destinations: Explore, Search, Subscribed and Library.
- Keep video watch and music deck as media experiences, not extra bottom-navigation pages.
- Preserve Explore's five filters and content-appropriate media ratios.
- Preserve vertical shelf navigation with horizontal shelf scrolling.
- Search opens focused; Android Back first dismisses the keyboard, then returns to Explore.
- Subscribed uses followed-source avatars and a combined feed.
- Library owns profile, saved media, history, watchlist, liked items, playlists, queue, downloads and settings.

## Code ownership

- Kotlin owns Android platform integration, Media3, foreground playback, notifications, PiP, permissions and local profile storage.
- RushWebBridge is the only supported JavaScript-to-native boundary. New bridge methods must have a native implementation and be referenced by the validator.
- HTML shell stays in app/src/main/assets/rush.html.
- Styles belong in app/src/main/assets/styles/.
- JavaScript modules belong in app/src/main/assets/scripts/.
- Keep app core, screens, profile/settings and bootstrap responsibilities separate.
- Do not add large inline CSS or JS blocks to rush.html.
- Serve local assets through WebViewAssetLoader. Do not reintroduce file:// access or loadDataWithBaseURL for the multi-file app.

## UI and interaction

- Use the established dark, tactile retro-electronics design system with illustrated/vector hardware controls and clear, vibrant accents.
- Use content-driven layout variation: video 16:9, square music art, portrait movie cards and compact landscape news cards.
- Keep glass effects limited to a small number of floating surfaces such as the bottom navigation and modal sheet.
- Avoid generic gradient panels, gratuitous glow, duplicated card patterns and decorative motion with no interaction meaning.
- Respect safe areas, keyboard resizing, touch target sizing and Android Back behaviour.
- Respect prefers-reduced-motion. Motion must not delay first content or interfere with playback controls.
- Loading UI should preserve cached content; use skeletons for true first-load waits and show retry actions for recoverable playback errors.

## Performance and data

- Preserve cache-first Explore rendering and stale-while-revalidate behaviour.
- Keep network fan-out bounded and avoid serial requests that block first paint.
- Keep news image enrichment asynchronous and cache publisher thumbnails.
- Never clear visible feed content merely because a refresh started.
- Keep local profile, follows, interests and library state local unless the product explicitly introduces an account/server feature.
- Add only permissions required by a real feature. Request runtime permissions when the user invokes that feature.

## Quality and release

Run before opening a PR:
1. python3 scripts/validate_rush_html.py
2. Android lint
3. Unit tests
4. Debug APK and release AAB builds

The validator checks modular asset paths, four-page UI contracts, JavaScript/native bridge calls, profile/permission integration and Play configuration.

Review third-party licences and content rights before changing the media pipeline. A successful build is not proof that a provider permits extraction, storage, download or background playback. See docs/PLAY_STORE_READINESS.md and docs/PRIVACY_POLICY_DRAFT.md.

## Runtime performance

- Use Kotlin and AndroidX Media3 for Android/platform and media responsibilities; use the bundled Android WebView (Chromium/V8) for local DOM/CSS UI.
- Do not add a second UI runtime or framework unless a profiling result shows it solves a real bottleneck on supported devices.
- Keep first-party JS/CSS local, loaded through WebViewAssetLoader; do not introduce CDN fonts or launch-time frameworks.
- Keep feed cache-first; never blank visible content during refresh. Skip DOM updates if the visible item signature has not changed.
- Load media art lazily with reserved dimensions. Do not make image enrichment a first-paint dependency.
- Preserve native adaptive track selection and use measured Media3 buffer policies. Do not force highest bitrate or claim zero-buffer playback.
- Keep video decoding on the native Media3 surface. Keep loading, retry and error states tied to actual Media3 state.
- Keep UI motion short and transform/opacity-based. Avoid infinite shimmer; respect reduced motion.
- Hardware acceleration is enabled. Handle WebView renderer process loss by recreating the WebView and restoring the current route.
- Benchmark time-to-first-frame, rebuffer time/count, dropped frames, startup, memory and scroll jank on real low/mid-range Android hardware before claiming smoothness.
- Follow Anti-Slop settings: Energy 2/5, Rhythm 3/5, Motion 1/5. Keep the established visual direction; motion and visual weight should support content and controls, not overwhelm them.

## Accessibility and onboarding

- Prefer Android host conventions over copying Apple controls literally. Keep the HIG principles that transfer: clear hierarchy, honest status, visible recovery, readable text, labels, focus and user control.
- Use at least 48dp touch targets for primary and frequent controls where the layout permits. Keep actual hit areas larger than decorative icons and provide clear focus styling.
- Do not encode meaning by color alone. Pair selected, error, buffering and notification states with text, shape or an icon.
- Respect Android font scaling and system reduced-motion preferences. Avoid clipping labels at larger text sizes; reflow the screen instead.
- Keep the first-run profile short and local. The user can continue as Guest; notification permission and other optional preferences must not be prerequisites for opening Explore.
- Request notification permission only after the user invokes notification-dependent functionality or explicitly enables it in settings. Provide a clear route to Android settings when permission was denied or disabled.
- For every modal/sheet, implement Back/Escape, Close, focus restoration and a non-blocking status path. Don't use alerts for ordinary loading or feed errors.
- Show cached/useful content before network enrichment. Distinguish first-load progress, background refresh, buffering, empty results, provider failure and retry states.
- Do not report native accessibility, real device font scaling or motion support as verified from static source checks alone; test TalkBack and Android font/display settings on real devices.
