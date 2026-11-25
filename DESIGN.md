# Rush design direction

Reading this as: a mobile-first video discovery and viewing app for Rush users, in the Juxtopposed YouTube Redesign visual language, with Energy 2 / Rhythm 2 / Motion 1.

## Source and boundary
- Primary reference: Juxtopposed, YouTube Redesign, Figma Community file 1450380484645543336.
- The reference governs the shell, header, sidebar hierarchy, topic strip, video thumbnails, feed shelves, metadata, Shorts presentation, and interaction density.
- Rush keeps its own name and identity. Do not use YouTube's logo, wordmark, avatar, or imply affiliation.
- Only implement surfaces present in the reference: Home, Explore, Shorts, TV Mode, History, Watch Later, Liked Videos, Playlists, Collections, Subscriptions, Search, video watch, and the visible header actions.
- News, music-player, movie browsing, and unrelated Rush-only screens are out of scope for this rebuild.

## Visual system
- Canvas: #111111. Elevated surface: #1f1f1f. Secondary surface: #2a2a2a. Divider: #393939.
- Primary text: #ffffff. Secondary text: #aaaaaa. Accent: #ff0033, used for selected navigation and active controls only.
- Typography: Roboto for interface text, Roboto Condensed for section headings where available. Use system fallbacks; no remote font dependency.
- Radius: 8px for topic tags and controls, 16px for large feed shelves, 20px for menus/dialog surfaces, 16px for video thumbnails.
- Video artwork: 16:9 thumbnails; Shorts: 9:16. All image regions reserve their ratio before loading.
- Icons use the reference's restrained outlined style and are paired with accessible labels.

## Layout and interaction
- Header: menu and Rush wordmark on the left, centered search, compact create/notifications/settings/profile actions on the right.
- Navigation: collapsible left sidebar with the same hierarchy as the reference. On phones it becomes a dismissible drawer; there is no duplicate bottom navigation.
- Home: horizontal topic tags, a horizontally browsable "In Case You Missed" shelf, a responsive video grid, and a distinct Shorts shelf.
- Search: one query field, video and channel results, loading/empty/error feedback.
- TV Mode reuses real feed data with a viewing-distance layout rather than inventing a separate content type.
- Native video playback, subscriptions, history, watch-later, liked state, and playlists use Rush's existing native bridge and local data.
- No fabricated creators, view counts, profile photos, notifications, or video content. Initial-based avatars are honest fallbacks.

## Craft decisions
- The black canvas and white typography preserve the source design's high contrast and let thumbnails carry the visual variety.
- Red is reserved for selected navigation and active controls because that is the source design's strongest state signal.
- The sidebar is retained on wide screens and becomes a drawer on narrow screens to preserve the reference hierarchy without compressing the feed.
- A single topic row filters the feed; it is not a third navigation system.
- Motion is limited to short drawer, popover, and pressed-state transitions. Reduced-motion preferences disable non-essential transitions.
