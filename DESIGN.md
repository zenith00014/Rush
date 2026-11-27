# Rush Design System

This file defines the visual rules for Rush's Android WebView interface. It is an adaptation for a personal, video-first discovery app; it is not a pixel copy of another product.

## Source basis

Adapted from the supplied `VoltAgent/awesome-design-md` collection:

- `design-md/spotify/DESIGN.md`: dark media-first surfaces, compact content hierarchy, pill-shaped actions and circular player controls.
- `design-md/apple/DESIGN.md`: restrained chrome, clear typographic hierarchy, circular utility controls and a practical 4/8/12/17/24/32px spacing rhythm.
- `design-md/linear.app/DESIGN.md`: quiet dark surfaces, hairline dividers, disciplined accent usage and visible focus states.
- `design-md/raycast/DESIGN.md`: layered dark surfaces, compact controls, low-noise utility UI and consistent card geometry.

These are reference systems, not Rush's brand identity. Do not import their logos, proprietary fonts, exact accent palettes or marketing-page layouts. Rush retains its own coral accent and media-first visual language.

## Design principles

1. **Content first.** Thumbnails, titles, creators and playback are more important than decorative UI.
2. **Dark, layered surfaces.** Use subtle differences between the canvas, cards and interactive controls instead of gradients or heavy shadows.
3. **One functional accent.** Rush coral is reserved for selected states, playback progress, focus and the primary action. Do not add competing accent colours.
4. **Dense but readable.** Video feeds should remain compact without making titles, metadata or touch targets cramped.
5. **Consistent iconography.** Use the shared inline SVG helper in `rush.html`; do not use Unicode symbols, emoji or platform-dependent glyphs as functional icons.
6. **Honest loading states.** Use skeletons for content loading and a persistent, clearly labeled buffering state for playback.
7. **Accessible controls.** Icon-only buttons need an accessible name, SVGs are decorative, focus must be visible and reduced-motion preferences must be respected.

## Tokens

| Token | Value | Use |
|---|---:|---|
| Canvas | `#0f0f0f` | Main mobile background |
| Surface | `#181818` | Cards, drawer and grouped content |
| Raised surface | `#242424` | Chips, fields and icon-button backgrounds |
| Hairline | `#363636` | Quiet card edges and dividers |
| Primary text | `#f2f2f2` | Headlines and key labels |
| Secondary text | `#aaaaaa` | Metadata and supporting copy |
| Rush accent | `#ff315d` | Selected state, focus, progress and primary action |

### Spacing

Use a 4px base scale: 4, 8, 12, 16, 20, 24 and 32px. Use 16–24px horizontal page insets on phones. Keep media content full-width within those insets.

### Shape

- Filter chips and primary actions: full pill.
- Icon buttons and playback controls: circle.
- Standard content cards and sheets: 12–18px radius.
- Dividers: 1px hairline; avoid decorative borders around every nested element.

### Typography

Use the Android/WebView system sans-serif stack. Do not fetch fonts over the network. Use a compact hierarchy: 30px page titles, 20px section titles, 15–16px video titles, 12–14px supporting text and 10–11px utility labels. Titles should remain readable before using uppercase tracking.

## Icon system

- Render icons with the shared `ic(name)` SVG function and a 24×24 viewBox.
- Use consistent outline geometry with approximately 1.8–2px strokes and rounded joins/caps.
- Reserve solid fills for play/pause glyphs where the silhouette benefits from it.
- Standard icon size: 20–22px; secondary row icon: 17–19px.
- Icon-only touch targets: at least 44×44px; primary mobile controls should reach 48px where layout permits.
- Keep the button's visible label in `aria-label`; SVGs must remain `aria-hidden` and non-focusable.
- Provide visible keyboard/accessibility focus using the Rush accent.

## Motion and feedback

- Keep navigation and sheets brief and functional.
- Use skeleton shimmer for feed, search and library content while loading.
- Use a visible spinner/status for active video buffering; do not replace the video with an empty black area.
- Disable repeated submit actions while local onboarding data is saving; restore the action and show an error when saving fails.
- Respect `prefers-reduced-motion` for shimmer, spinners and transitions.

## Component rules

- **Header:** 44px minimum icon hit areas, a compact wordmark, search, subscriptions and local-profile shortcut.
- **Drawer:** 48px minimum rows, aligned vector icons, explicit close action and correct `aria-hidden` state.
- **Feed card:** 16:9 thumbnail, concise title, creator metadata and a visually quiet action row.
- **Filter chips:** pill geometry with an unmistakable selected state.
- **Profile/onboarding:** local-only copy, clear selected interests, a disabled/saving state during persistence and an actionable error.
- **Downloads:** preserve progress, resolved quality and explicit cancel/resume actions.

## Do not

- Do not mix SVGs with Unicode icon glyphs for the same functional control.
- Do not use emoji as navigation or action icons.
- Do not make icon-only controls smaller than their touch target.
- Do not add decorative gradients, glass effects or unrelated accent colours.
- Do not silently mark onboarding complete when local profile or interest persistence fails.
- Do not add cloud accounts, tracking or remote profile storage for this personal-use app.
