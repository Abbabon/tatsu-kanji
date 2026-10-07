# iOS platform features: plan

Status: draft, to be grilled. Not approved for implementation.

## Why

Tatsu 0.1 covers search, recents, copy, keyboard control and dark mode. Almost all of its
contact with the rest of iOS is missing: no widgets, no Siri/Shortcuts, no Spotlight, no deep
links, no share sheet, no speech. The Android app has since shipped (see below); this plan covers what iOS users expect.

## Current state (2026-10-06)

- One multiplatform app target (iOS 17 / macOS 14), plus `TatsuTests` and `TatsuUITests`, all under `apple/`.
  No extensions, no entitlements, no URL types.
- Recents: SwiftData `Recent`, last 100, device-only by design (`apple/Tatsu/Recent.swift`).
- Glyphs use fixed sizes (`apple/Tatsu/ContentView.swift:140` 40pt, `apple/Tatsu/KanjiDetail.swift:12` 96pt).
- No accessibility modifiers. One iOS app icon with no dark/tinted variants.
- The 0.1 spec's roadmap: 1.1 action extension (search moves to a shared Swift package),
  1.2 camera Live Text, then downloadable packs. Non-goals kept: syncing, translations.
- The repo is split into `apple/` (this app), `android/` and `data/` (`kanji.json`, 10,348
  kanji, and `build_data.py`), shared by both apps.
- The Android app (`android/`) shipped 0.1 with a text-selection "Tatsu" menu (Android's
  analogue of the 1.1 action extension), a themed icon, hardware-keyboard support and
  tablet/foldable layouts. It has no favorites, read-aloud, widgets or deep links.

## Order of work

| # | Piece | Path | Depends on |
|---|---|---|---|
| 1 | Tier 1 polish | Bounded | — |
| 2 | Deep links, App Intents, Spotlight | Architectural | — |
| 3 | Kanji-of-the-day widget | Architectural | 2, shared package |
| 4 | Dark/tinted app icons | Art only | user supplies artwork |

Each piece gets its own design approval before code. 4 can land any time.

## 1. Tier 1 polish

- **Favorites.** A SwiftData `Favorite` model next to `Recent`. Star button in the detail
  toolbar. A Favorites section above Recent when the search field is empty. Device-only.
- **Read aloud.** `AVSpeechSynthesizer` with a `ja-JP` voice. Tapping a reading speaks it;
  a speaker button speaks the kanji. Works offline with the built-in voice.
- **Accessibility.** `@ScaledMetric` for the two glyph sizes. VoiceOver labels on result rows
  and reading stacks (e.g. "水, on reading sui, kun reading mizu").
- **Swipe to delete** on Recent and Favorites rows.
- **Share and drag.** `ShareLink` in the detail toolbar and `.draggable` on the glyph, both
  sharing plain text (the kanji, maybe with its first meaning).

Tests: unit tests for the Favorite store and the accessibility label builder.
Speech and share are checked by hand.

## 2. Deep links, App Intents, Spotlight

- **URL scheme** `tatsu://kanji/<char>` handled with `onOpenURL`, selecting the kanji in the
  split view. This is the one route widgets, Spotlight and intents all use.
- **App Intents.** `LookUpKanjiIntent(query:)` opens the app on the best match.
  `AppShortcutsProvider` exposes it to Siri, Shortcuts, Spotlight and the Action button.
  An `AppEntity` for kanji so Shortcuts can pass results around.
- **Spotlight.** Index every kanji with CoreSpotlight (glyph, meanings, readings) on first
  launch and when the data version changes. Tapping a result opens the deep link.
- **Control Center control** (iOS 18): reuses the intent. Optional, deferred if it needs a
  widget extension before piece 3 exists.

## 3. Kanji-of-the-day widget

- New WidgetKit extension: small/medium home screen and lock screen widgets.
- Choice of kanji is a deterministic function of the date (same kanji all day, on every
  device), drawn from a filtered pool (e.g. JLPT N5–N3 or grade 1–6).
- Needs the kanji data and lookup code in the extension: move search and data into a
  shared Swift package under `apple/` that reads `data/kanji.json`, as the 1.1 action-extension plan already intends.
- Tap opens `tatsu://kanji/<char>`.

## 4. Icons

Dark and tinted 1024px variants added to `AppIcon.appiconset`. Needs artwork from the user.

## Out of scope

iCloud sync, Apple Watch, visionOS, StoreKit review prompt, TipKit, translations.

## Open questions

1. Android has neither favorites nor read-aloud. Do they ship on iOS first, with parity
   meaning we add them to Android later?
2. Speech: which reading forms are spoken (kana only, or names too)? Fallback when the
   Japanese voice isn't installed?
3. Widget pool and rotation: which kanji, and does it skip ones you've already seen?
4. Does the shared package move happen as part of piece 3, or as its own step first
   (so the 1.1 action extension and the widget share it)?
5. Spotlight indexing cost: index all 10,348 kanji, or only graded/JLPT ones?
6. Mac: which of these ship on macOS too (widgets and intents do; speech and share do)?
