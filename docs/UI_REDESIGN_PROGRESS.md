# Rush UI redesign progress

**Branch:** `redesign/productive-mobile-ui`  
**Reference:** `Youtube Ui.zip` (screen-composition reference), `RushSS.zip` (current app screenshots), and `Youtube Design System (Community).pdf` (visual context only; it contains a cover/collage, not detailed component rules).  
**Last updated:** 2026-11-27

## Completed in the current redesign passes
- Corrected the active Home renderer so the creator rail is visible before a single compact horizontal filter row.
- Moved video-card Save, Queue, Download and Share into the existing More action sheet, removing permanently visible action rows.
- Corrected the bottom navigation to four equal columns for its four actual tabs.
- Preserved the originating tab when opening a video from Home, Search, Subscriptions or Library; the watch Back control returns to that source and the originating tab remains selected.
- Rebuilt the watch action area with accessible SVG icons and a compact overflow menu; removed duplicate Resume controls and emoji controls.
- Made watch view/date metadata and description conditional on actual source data; pass avatar/view/date metadata from video cards when available.
- Tightened watch title, creator row, action row, autoplay settings and Up next spacing while retaining the native Media3 player host.
- Added a minimal mobile top bar: Rush mark and wordmark plus Notifications.
- Added a Settings/Profile control in the top bar that is visible only on Library.
- Kept Cast and all other extra actions out of the top bar, per the latest direction.
- Routed the Library Settings/Profile action to the existing local profile screen.
- Removed duplicate profile/music actions from the Library page heading.
- Removed the Home page title/kicker and redundant creator-strip heading/discover button.
- Simplified Home to a creator rail followed by one horizontal category/filter row; moved Topics into that row.
- Changed Home's secondary content layout to a single vertical feed column to match the supplied YouTube reference more closely.
- Replaced always-visible Save/Queue/Download button rows with a compact accessible More actions disclosure menu.
- Tightened mini-player geometry and replaced its text arrow with the shared SVG icon.
- Retained the existing native Media3 player and requestAnimationFrame-coalesced geometry update on nested WebView scroll/resize.
- Added validator assertions for the header, Library profile routing, and Home layout contracts.

- Fixed duplicate empty/error messaging on the Subscriptions feed: the empty-state message is now shown once instead of also being repeated in the live status line.

## Remaining work, in priority order

### Critical
- Compare the native video player against the reference on a physical Android device: remove top whitespace, fix crop/aspect ratio, anchor controls inside the video, verify resume/fullscreen/rotation/scroll.
- Verify mini-player thumbnail, dimensions, position above bottom navigation, tap targets, and feed scroll clearance on-device.
- Verify bottom-navigation selection on-device for watch/player and Library subviews; four-column layout and watch-origin selection are corrected in code.

### High
- Verify the More actions menu opens/closes correctly and remains inside the viewport on narrow screens.
- Refine Home card hierarchy, title/metadata density, thumbnail radius/aspect ratio, and overflow actions against the reference screenshots.
- Search's duplicate native clear affordance is suppressed in favour of the explicit Clear button; verify keyboard/submit/clear states on-device.
- Fix Subscriptions empty/loading/error states and remove duplicate error messaging.
- Investigate channel avatar source data. Persisted `FeedSource` currently stores kind/id/name/productive only; do not invent avatar URLs or fake logos. Extend metadata only if a trustworthy thumbnail is available from actual channel search/feed results.
- Remove any remaining duplicate or competing page-level actions that conflict with the minimal top-bar rule.

### Medium
- Verify Library shelves (History, Watch Later, Liked videos, Playlists) and View all transitions against the reference; check queue/download subviews separately.
- Comments preview remains out of scope until a real comments source is available; the current native bridge exposes no comments data, so no sample comments are fabricated.
- Standardize action icons, card spacing, text clamping, and touch targets across Search, Subscriptions, Library, and Watch.

## Known limits and verification
- Notifications currently show an explicit unavailable-state message; there is no notifications backend.
- No Cast control is included in the top bar.
- Channel avatar images are not available in the persisted followed-source model; initials remain the honest fallback until real avatar metadata is available.
- CI build/lint/test status must be checked for the latest commit; a running workflow is not a pass.
- Physical-device verification is still required for player geometry, mini-player behavior, narrow-screen overflow menus, search keyboard/clear states, and actual image loading.
- No backend/authentication or fake sample data was added as part of this UI work.

## Verification checklist
- [ ] `python scripts/validate_rush_html.py`
- [ ] Android lint
- [ ] Unit tests
- [ ] Debug APK build
- [ ] Small-screen portrait device: Home, Search, Subscriptions, Library
- [ ] Video: open, play, scroll, mini-player, fullscreen, rotate, return
- [ ] Offline download: complete, cancel/resume, play offline
- [ ] Compare screenshots at the same viewport dimensions as supplied references
