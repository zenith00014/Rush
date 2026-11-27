# Rush Android Agent-Skills Audit

**Audit date:** 2026-11-27  
**Target branch:** `redesign/productive-mobile-ui`  
**Project profile:** Kotlin 2.0.21, AGP 8.9.2, Gradle 8.11.1, Android SDK 36, Media3 1.9.2. The primary mobile UI is an HTML/CSS/JavaScript WebView backed by Kotlin/Media3. The source tree also contains Compose player and legacy screen code.

This audit applies the relevant engineering rules from each supplied repository. Third-party skill files themselves were not copied into the app; the resulting implementation changes and applicability decisions are recorded here.

## 1. Google Android Skills — `android/skills`

Source: https://github.com/android/skills

**Applied**
- Media3 playlist preloading: configure a five-second preload for the next playlist item; recommendations are queued in a bounded batch so the player has a next item to preload.
- Shorter startup/rebuffer thresholds: two seconds for initial playback and after rebuffer, while retaining a longer rolling buffer for unstable connections.
- Real player buffering feedback: enable Media3 `PlayerView` buffering UI and publish Media3 buffering state to the WebView.
- Feed and search skeleton placeholders use an animated shimmer with a reduced-motion fallback.
- Existing edge-to-edge insets and `adjustResize` behavior were retained; search explicitly focuses its input and requests the keyboard.
- Static ANR review: feed scoring and diversification now run on `Dispatchers.Default`; network search remains on `Dispatchers.IO`.

**Not completed on source inspection alone**
- No Perfetto trace, Android Studio Profiler capture, or physical-device network measurement was available in this workflow. Buffering improvements therefore need device verification under Wi-Fi and cellular conditions.

Relevant official references:
- https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer
- https://developer.android.com/reference/androidx/media3/ui/PlayerView
- https://developer.android.com/reference/androidx/media3/exoplayer/DefaultLoadControl

## 2. JetBrains Kotlin Agent Skills — `Kotlin/kotlin-agent-skills`

Source: https://github.com/Kotlin/kotlin-agent-skills

**Applied**
- Preserved structured concurrency and rethrows `CancellationException` from coroutine-backed bridge and Compose operations.
- Avoided moving the project to a new build system during a targeted Android app update.

**Not applicable**
- The Kotlin Toolchain skill explicitly targets Kotlin Toolchain/Amper projects; Rush is an existing Gradle Android project.
- Kotlin/Native build-performance guidance is for iOS/KMP native targets; Rush is not a Kotlin/Native build.
- The AGP 9 migration skill is for KMP/AGP 9 compatibility. Rush currently uses AGP 8.9.2 and is not being migrated to AGP 9 in this task.

## 3. Awesome Kotlin Android Agent Skills — `almasumdev/awesome-kotlin-android-agent-skills`

Source: https://github.com/almasumdev/awesome-kotlin-android-agent-skills

**Applied**
- Corrected coroutine cancellation handling in feed search, playback, search, downloads, and Compose routines.
- Kept I/O on `Dispatchers.IO`; moved CPU-heavy candidate scoring/ranking to `Dispatchers.Default`.
- Added stale-while-revalidate feed behavior: cached recommendations render immediately while the refresh runs; cached results are retained if refresh returns no items.
- Added unit tests for freeform topic normalization, topic preference/snooze behavior, like/dislike ranking, and show-more topic ranking.
- Enabled Gradle parallelism, build caching, and Kotlin incremental compilation.

**Adapted**
- The project already has native SharedPreferences, a local media library, Media3's stream cache, and a WebView presentation layer. A broad Room/Retrofit/Hilt/feature-module migration would be a separate architectural project, so those recommendations were not mechanically imposed here.

## 4. Awesome Android Agent Skills — `new-silvermoon/awesome-android-agent-skills`

Source: https://github.com/new-silvermoon/awesome-android-agent-skills

**Applied**
- Coroutine cancellation is rethrown rather than converted into ordinary error UI.
- Interactive mobile controls were raised to 48dp minimum hit areas where practical.
- Search inputs, selected filters, action states, empty states, retry actions, and loading announcements have explicit labels/states.
- Existing Android unit tests were extended for the new feed-preference and ranking behavior.

## 5. Android Skills Catalog — `javiercamarenatriguero/android-skills`

Source: https://github.com/javiercamarenatriguero/android-skills

**Applied**
- Used scoped coroutine handling and explicit cancellation propagation.
- Added behavior-focused tests around feed personalization and preferences.
- Retained idiomatic Kotlin naming and null-safe handling in new logic.

**Not applied wholesale**
- The supplied MVI/Compose screen foundation is not a fit for the existing WebView-first presentation and would introduce a second, competing UI architecture.

## 6. Compose Kotlin Agent Skills — `haidrrrry/compose-kotlin-agent-skills`

Source: https://github.com/haidrrrry/compose-kotlin-agent-skills

**Applied where Compose code exists**
- Kept network work off the main thread and made cancellation explicit.
- Added a Compose `CircularProgressIndicator` over the legacy Compose video player while buffering; the embedded `PlayerView` also displays its own buffer indicator.

**Not applicable to the main feed**
- The current redesigned feed is HTML/CSS/JavaScript in a WebView, not a Compose `LazyColumn`. Paging 3 and Compose recomposition-specific migration were not added to the WebView feed.

## 7. Modern Jetpack Compose Skills — `anhvt52/jetpack-compose-skills`

Source: https://github.com/anhvt52/jetpack-compose-skills

**Applied where relevant**
- Reviewed Kotlin null-safety and coroutine patterns.
- Preserved state and loading/error behavior in existing Compose player code.
- Added visible buffering progress to the Compose player path.

**Not applicable**
- Compose-only lazy-list keys, Material 3 navigation, and recomposition metrics do not govern the WebView-based mobile navigation/feed. The WebView UI instead uses accessible labels, ARIA states, semantic loading announcements, and responsive CSS.

## 8. Performance Compose Skills — `santimattius/performance-compose-skills`

Source: https://github.com/santimattius/performance-compose-skills

**Applied where relevant**
- Kept feed scoring off the main/UI thread.
- Added buffer state feedback without continuously rebuilding the feed.
- Kept the Media3 `PlayerView` as the native video surface rather than drawing video frames through Compose.

**Not measured**
- Compose recomposition tracing and Macrobenchmark require a running device/emulator and a profileable/release build. Those measurements were not available here.

## Second implementation pass — local profile, downloads and quality

This follow-up keeps all eight supplied source collections and Ponytail rules active.

**Local profile and onboarding**
- First launch offers an optional display name and interest selection, then opens Home.
- The Profile screen edits the display name, interests, snoozed topics and default video-quality cap.
- Profile setup state and display name are saved in the existing app-private SharedPreferences store. No email, password, identity provider, cloud database or sync service was added.
- The top profile shortcut opens the local Profile screen; feed-interest controls remain separate.

**Video downloads and quality**
- Mobile video cards, the watch screen, the action sheet and saved-video rows expose a Download action.
- Quality options now include Auto, 360p, 720p, 1080p, 1440p, 4K Ultra HD (2160p cap) and 8K (4320p cap).
- New video downloads resolve a direct progressive stream rather than storing an HLS/DASH manifest as though it were a playable offline file.
- The resolver chooses the best available direct format at or below the selected cap. If no format is below the cap, it falls back to the lowest available direct format. The Downloads shelf reports the resolved quality.
- The Downloads shelf shows state and progress, disables Play until a file is complete, and exposes Cancel or Resume for unfinished work.
- The selected quality cap is applied when Media3 starts. Saving a quality preference does not start an otherwise idle playback service.

**High-quality download limitation**
- The current offline path downloads a directly playable progressive stream. A 4K/8K cap does not guarantee that the source provides a directly downloadable 4K/8K file.
- Separately downloading a video-only stream plus audio and muxing them into one offline file is not implemented. The UI reports the actual resolved quality and explains that the source may provide a lower maximum.

**Additional fixes in this pass**
- Removed redundant consecutive CancellationException handlers while preserving cancellation propagation.
- Removed the duplicate mobile video-search error callback that overwrote the callback responsible for clearing pending/loading state.
- Added a visible player buffering spinner with a reduced-motion fallback; native Media3 buffering indicators remain enabled.
- Added unit tests for selecting the best direct format at or below a quality cap and falling back when a requested Ultra HD height is unavailable.

**How the eight source collections informed this pass**
1. Google Android Skills: retained native Media3 playback, applies the saved track-height cap at player creation, keeps visible buffering feedback and extends automated validation.
2. JetBrains Kotlin Agent Skills: preserves the existing Gradle Android project; Kotlin Toolchain and KMP/AGP migration instructions remain out of scope.
3. almasumdev Kotlin/Android skills: keeps personal profile data local, preserves cancellation, performs stream resolution/download work off the UI thread and adds focused tests.
4. new-silvermoon Android skills: adds labeled onboarding/profile controls, explicit selected states, download progress, and cancel/resume affordances.
5. javiercamarenatriguero Android skills: adds behavior-focused resolver tests and avoids imposing a Compose/MVI migration on the WebView-first UI.
6. haidrrrry Compose/Kotlin skills: keeps the legacy Compose player path intact and uses native player surfaces rather than rebuilding the feed as Compose.
7. anhvt52 modern Compose skills: preserves existing Compose player conventions without introducing a parallel Compose navigation architecture.
8. santimattius performance Compose skills: keeps buffering feedback separate from feed rebuilding and retains the native PlayerView video surface; device profiling remains outstanding.

## Behavior changes covered by this update

- For You feed candidates are now searched from selected topics, not only followed channels.
- Topic entry accepts user phrases, maps known aliases locally, and preserves unmatched phrases as custom topics.
- Topics support **Show more**, **Don’t show**, removal, and a one-day snooze with automatic expiry.
- Like/dislike feedback affects recommendation scoring; liked videos appear in the Library's Liked shelf.
- The video screen exposes autoplay, next, speed, quality, captions, and sleep-timer controls.
- Search has recent-search history and loading skeletons; the feed and music search also show loading states.
- The mobile interface removes Explore and News routes, the top header, and the creator strip.

**Topic Assist note:** The phrase-to-topic helper is deterministic and runs locally using aliases and the topic vocabulary. It is not a remote generative-AI model.

## Verification status

The repository workflow validates the WebView/bridge contract, runs Android lint, runs unit tests, assembles the debug APK, and uploads the APK artifact. Final verification is considered complete only when the workflow for the latest code commit passes. Device-only PiP, soft-keyboard, TalkBack, and real-network buffering checks remain separate physical-device acceptance tests.


## Follow-up — first-run profile completion and visual system

**First-run issue found and fixed**
- The onboarding picker included default interests such as `Culture` and `Language`, while `saveTopics` used a stricter catalog-only resolver. Those existing defaults were rejected during profile setup, so the native bridge never set `rush_profile_setup_complete` and the route guard sent the user back to onboarding.
- Reused the existing local `resolveTopic` function for onboarding interest persistence. Known aliases are normalized and unknown user-selected phrases remain local custom interests.
- Added tests that save the full default interest list and a mixed selection including `Culture`, `Language`, a custom phrase and the machine-learning alias.
- Onboarding now shows a saving state, disables repeat submission and restores the form if persistence fails. It navigates to Home only after the native callback confirms `setupComplete=true`; a failed or incomplete save remains on the form with an error message.
- Missing native profile storage now fails closed instead of pretending setup is complete.

**Uploaded design reference audit**
- Read the uploaded `awesome-design-md-main.zip` and inspected its Apple, Linear, Raycast and Spotify `DESIGN.md` references.
- Adapted the media-first dark surface ladder and pill/circle controls from Spotify; restrained typography and 4/8/12/17/24/32px spacing rhythm from Apple; low-noise dark surfaces, hairline dividers and single-accent discipline from Linear; and compact dark utility controls from Raycast.
- Rush keeps its own coral accent, existing system-font stack and feed layout. Brand colors, fonts, logos, marketing-page layouts and decorative effects from those references were not copied.
- Added root `DESIGN.md` with Rush tokens, touch-target rules, icon rules, motion/accessibility rules and component guidance.

**UI icon and navigation changes**
- Replaced the mobile header, search/history, feed utilities, topics, overflow, drawer and action-sheet text glyphs with one inline SVG icon map. Icons share a 24px viewBox, rounded outline geometry, decorative/hidden SVG semantics and consistent sizing.
- Increased header icon/profile hit areas to 44px and retained 48px targets where the mobile layout already allows them.
- Wired menu open/close/scrim, drawer routes, library shortcuts and the subscriptions shortcut.
- The mobile top bar was previously hidden by a broad CSS rule, which made its controls unreachable. The final mobile style now explicitly keeps the redesigned top bar visible.
- Added visible focus states and explicit labels for icon-only controls.

**Regression checks added**
- The WebView validation script now checks onboarding default/custom topic support, submit/error recovery, SVG icon usage, drawer control wiring and presence of the design guide.
- It verifies that the mobile top bar and its menu/search/profile controls remain visible.
- CI validation and unit tests for this final commit are still required before calling the update complete. Real-device keyboard, TalkBack, visual layout and onboarding persistence checks remain outstanding.


**Static-versus-dynamic SVG handling**
- The top bar and drawer are literal HTML, so their icons are now inline SVG elements directly in the markup. Dynamic cards, rows and action sheets use the shared `ic(name)` SVG helper from the app script.
- Validation checks the static HTML and dynamic helper separately so JavaScript expressions cannot accidentally be emitted as visible raw text in static markup.


## Build regression follow-up — strict versus onboarding topic validation

The failed CI run on commit `27ef0cf35ef2892013eda9a4cc186bbd8fdea8bf` passed WebView validation and Android Lint, but failed one unit test: `LocalLibraryTest.topicSelectionIsValidatedAndLegacyProductivityFlagDoesNotGateForYou`. That test requires normal topic editing to reject unknown values.

The earlier first-run fix had made the shared `saveTopics` method accept arbitrary user phrases. That made profile onboarding work in principle but weakened the regular topic-editor contract.

**Correction**
- Restored strict catalog validation for `FeedPreferences.saveTopics`; unknown values are rejected and aliases must resolve to a catalog/default topic.
- Added a separate `saveOnboardingTopics` method. It accepts built-in catalog/default topics and custom topics already stored locally, but rejects arbitrary unsaved phrases.
- Updated `RushWebBridge.completeLocalProfile` to call the onboarding-specific method before it marks setup complete.
- Added tests for default interests, Culture/Language defaults, a previously saved custom interest, alias normalization, regular-editor rejection, and onboarding rejection of unsaved custom phrases.
- Updated WebView validation so future CI checks both API paths and their intended contracts.

The CI run after these corrections is the verification gate. No APK is considered ready until Android lint, all unit tests, APK output verification and artifact upload complete successfully.

## 2026-11 mobile reference and playback repair

- Removed the mobile top header and its unused drawer markup; the four bottom destinations remain available on every screen, including video watch.
- Rebuilt topic personalisation as a selectable chip catalogue that mirrors the Home topic rail. Existing custom topics remain selectable; the native bridge validates the saved set against the built-in catalogue and locally stored topics.
- Home now includes an explicit All topic reset and uses serialized feed topic tags before text matching.
- YouTube search/feed results preserve channel avatar URLs from the video renderer and merge them into extractor results.
- Native inline video positioning now uses the WebView device-pixel ratio and repositions after WebView/root layout changes.
- Download creation and worker recovery force-refresh cached stream metadata after URL expiry. Adaptive SABR audio is no longer mislabeled as a video download.
- Remaining validation is the Android CI build and device-level playback/download checks; YouTube availability and source restrictions remain external dependencies.

