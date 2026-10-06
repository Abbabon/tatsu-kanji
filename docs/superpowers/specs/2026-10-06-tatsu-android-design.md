# Tatsu: native Android app

Date: 2026-10-06. Status: approved design, awaiting implementation plan.

## Goal

Ship Tatsu on Google Play for Android phones, tablets and foldables. It does everything iOS 0.1 does, in Material 3 rather than a copy of the iOS look, plus four Android-native hooks that each cost little.

## Decisions

| Topic | Decision |
|---|---|
| Code | Separate native app in `android/` in this repo. Kotlin, Jetpack Compose, Material 3. No code is shared with Swift; data and test cases are shared. |
| Devices | Phones, tablets and foldables. One adaptive layout. ChromeOS works but gets no extra polish. |
| Minimum OS | minSdk 26 (Android 8.0). targetSdk and compileSdk are the latest stable SDK at implementation time (36 or newer, as Play requires). |
| Distribution | Google Play, free, AAB with Play App Signing. The upload keystore lives outside the repo. |
| Package | `com.abbabon.kanjioffline`. It can never change after the first upload. |
| Version | versionName `0.1`, versionCode `1`. |
| Name | Store name "Tatsu – Offline Kanji", launcher name "Tatsu". |
| Network | None. No `INTERNET` permission. |
| Privacy | Data safety form: no data collected, no data shared. Recents stay on the device. |
| Backup | `android:allowBackup="false"`. Recents never leave the device and are not in Google backups or device transfers. |
| Data | `Tatsu/kanji.json` (KANJIDIC2, 10,348 entries) is copied into the APK assets by Gradle at build time. `build_data.py` stays the only generator. |
| Persistence | Jetpack DataStore (Preferences) storing the recent list as one ordered string, newest first, capped at 100. No Room. |
| Language | English. All UI strings in `strings.xml`, including the grade labels and section labels that iOS hardcodes. |

## Scope

### Parity with iOS 0.1

- One search field: English, hiragana, katakana, romaji, kanji text. Same ranking, same typo tolerance, same limit of 60. Search runs on every keystroke off the main thread; a newer query cancels the older one.
- Result row: kanji, meanings (up to 2 lines), readings (1 line).
- Empty query shows "Recent" with a Clear action. The order is frozen while browsing and re-sorted when the query clears. With no recents it shows the hint text.
- "No matches." state with a handwriting hint.
- Detail: large kanji (selectable), Copy, meanings, ON / KUN reading tiles (kana over kanji + okurigana, romaji below), NAMES, meta line.
- Copy from detail and from the row's long-press menu. Recent rows' menu also has "Remove from history".
- Recent is recorded on explicit picks only: tap, Enter, Copy. Arrow-key movement does not record.
- About sheet: KANJIDIC2 credit and CC BY-SA 4.0 link, KANJIDIC Project link, MIT note, GitHub link.
- Hardware keyboard on tablets: Up/Down step the selection, Enter picks, typing anywhere goes into the search field, Ctrl+F focuses search.

### Android additions

1. **Text-selection menu.** `ACTION_PROCESS_TEXT` intent filter, label "Tatsu". Selecting text in any app and choosing Tatsu opens it with that text searched. Read-only (we never return text).
2. **Edge-to-edge and predictive back.** `enableEdgeToEdge()`, and predictive back opts in through the manifest. On phones, back from detail returns to the list with the system animation.
3. **Themed icon.** Adaptive icon plus a monochrome layer for Android 13+ themed icons, drawn from `logo.svg`. The 断 outline is extracted from Noto Serif JP (SIL OFL 1.1), downloaded from an official source, not from Hiragino; the About sheet credits the font.
4. **Gboard hint.** The "No matches." hint says how to add Japanese handwriting in Gboard ("No matches. To write kanji by hand, add Gboard's Japanese Handwriting layout: Gboard settings → Languages → Japanese → Handwriting."; path verified 2026-10-06 against Gboard Help, support.google.com/gboard/answer/9108773), in place of the iOS Chinese Handwriting keyboard hint.

### Not in scope

The iOS roadmap items (camera lookup, stroke order, words, radicals, built-in handwriting), widgets, App Shortcuts, settings, translations, and a ChromeOS-specific layout.

## UI mapping

| iOS | Android |
|---|---|
| `NavigationSplitView` | `ListDetailPaneScaffold` from `material3-adaptive`. One pane with a push transition on compact widths; two panes on medium and expanded widths (tablets, unfolded foldables, landscape). |
| `.searchable` | A full-width pill-shaped `TextField` (rounded 28dp, no underline) pinned at the top of the list pane. Results show in the list below it, not in an expanding search overlay. The leading icon is search; the trailing icon clears the field when it has text. `SearchBarDefaults.InputField` is not used because it has no `keyboardOptions`; a plain `TextField` lets us turn autocorrect and capitalisation off and set the IME action to Search (Enter picks). The field is focused at launch with the keyboard up. |
| List row | `ListItem`: leading kanji at 40sp, headline the meanings, supporting text the readings. Long-press opens a `DropdownMenu`. The selected row is highlighted in two-pane mode. |
| "Recent" header + Clear | A section header row with a `TextButton("Clear")`. |
| Toolbar info button | `IconButton` with the info icon in the list pane's top bar, which opens the About screen as a `ModalBottomSheet`. |
| Detail view | Top app bar with back (single-pane only) and a Copy action. Kanji at 96sp in a `SelectionContainer`. Reading tiles are tonal `Surface` cards in a `FlowRow`. Section labels use `titleSmall`. |
| Copy confirmation | `ClipboardManager`. On Android 13+ the system shows its own clipboard preview, so the app shows a "Copied" snackbar only below API 33. |
| Tint (system blue) | Material You dynamic colour on Android 12+. Below that, a static light and dark scheme seeded from the logo's main colour. Follows system dark mode. |

Text sizes use `sp`, so the kanji scale with the user's font size (iOS fixes them in points). Every text that shows kanji or kana sets `LocaleList("ja")`, so Han characters use Japanese glyphs, not Chinese ones. The Compose layout supplies accessibility semantics; rows and reading tiles merge their children so TalkBack reads each as one item.

## Architecture

One Gradle module, `app`. No dependency-injection framework, no repository layer, no use-case classes. Files mirror the iOS ones:

| File | Mirrors | Role |
|---|---|---|
| `Kanji.kt` | `Kanji.swift` | `@Serializable` data class with the short JSON keys, plus loading from assets. |
| `Search.kt` | `Search.swift` | `toHira`, `romaji`, `Searcher`. Pure Kotlin, no Android imports. |
| `Format.kt` | `Format.swift` | `readingParts`, `gradeLabel`, `metaLine`, `stepSelection`, `isTypable`. Pure Kotlin. Label strings are passed in so the logic stays testable on the JVM. |
| `Recents.kt` | `Recent.swift` | DataStore wrapper: `flow`, `touch`, `remove`, `clear`. |
| `TatsuViewModel.kt` | state in `ContentView`/`TatsuApp` | Owns the loaded data, query, results, selection and recents. Keeps the query in `SavedStateHandle` so it survives process death. |
| `MainActivity.kt` | `TatsuApp.swift` | Edge-to-edge, theme, reading `EXTRA_PROCESS_TEXT` (on create and on new intent). |
| `ListPane.kt` | `ContentView.swift` | Search field, results and recents list, empty states, keyboard handling. |
| `DetailPane.kt` | `KanjiDetail.swift` | Detail view. |
| `AboutSheet.kt` | `AboutView.swift` | About bottom sheet. |
| `Theme.kt` | — | Dynamic colour with static fallback. |

**Data flow:**
1. On first composition the ViewModel decodes `kanji.json` on `Dispatchers.Default` and builds the `Searcher`. The UI shows an empty list until that is done. If the asset fails to decode, the app crashes, like `fatalError` on iOS: the asset ships inside the APK, so a failure is a build bug.
2. The query is a `StateFlow`, and results come from `mapLatest { searcher.search(it) }` on `Dispatchers.Default`.
3. Recents come from a DataStore `Flow`.

**Dependencies:** Compose BOM, `material3`, `material3-adaptive` (plus its navigation artifact), `activity-compose`, `lifecycle-viewmodel-compose`, `datastore-preferences` and `kotlinx-serialization-json`. Tests also use JUnit 4 and `compose-ui-test`. Nothing else. Versions go in a Gradle version catalog.

**Build:**
- A Gradle task copies `../Tatsu/kanji.json` into the generated assets, so the data is never committed twice.
- Release builds use R8 minify and resource shrinking. kotlinx-serialization ships its own keep rules.

## Testing

- **JVM unit tests** (`./gradlew test`, no emulator):
  - every case from `SearchTests.swift`, `FormatTests.swift` and the DataTests suite, copied one for one;
  - the tests read the real `kanji.json`.
- **Recents tests** against a DataStore in a temporary folder: order, de-duplication, cap of 100, remove, clear, removing an absent kanji.
- **One instrumented Compose test** (`connectedAndroidTest` on an emulator):
  - tap a row, the detail opens, the kanji is in Recent;
  - a `PROCESS_TEXT` intent with "水" searches for it.
- **Manual checklist** for things the automated tests don't cover:
  - two-pane layout on a tablet emulator and foldable posture;
  - predictive back;
  - themed icon;
  - hardware-keyboard behaviour;
  - TalkBack pass;
  - dark mode;
  - on a device, Japanese handwriting in Gboard recognises kanji and the hint's menu labels match.

## Toolchain and repo

- This Mac has the Android SDK but no JDK and no Android Studio. Setup installs JDK 17 (Homebrew Temurin) and the Gradle wrapper. Android Studio is optional but recommended for the emulator UI.
- `CLAUDE.md` gets a "Running Android tests" section:
  - `./gradlew test` for unit tests;
  - `connectedAndroidTest` against an emulator only, never a physical device without asking.
- `README.md` gains an Android section.
- `.gitignore` gains `android/.gradle`, `android/build`, `android/app/build`, `local.properties`, and `*.jks`/`*.keystore`.

## Play Store release

- `docs/playstore.md`:
  - listing text adapted from `docs/appstore.md` (short description of 80 characters or fewer, full description);
  - category Education;
  - content rating questionnaire answers;
  - data safety answers ("no data collected");
  - privacy policy URL `https://github.com/Abbabon/tatsu-kanji/blob/main/PRIVACY.md`.
- `PRIVACY.md` adds Android wording: how to clear recents, and that uninstalling deletes them.
- Screenshots:
  - phone and 10" tablet, light mode only (about 6 files);
  - captured from emulators with `adb exec-out screencap`;
  - stored in `docs/playstore/screenshots/`;
  - plus a 1024×500 feature graphic and a 512×512 icon.
- Upload to internal testing first. Personal accounts created after 13 November 2023 must run a closed test (at least 12 testers, 14 days) before production; whether that applies is not known yet. If Play Console shows the closed-testing requirement banner, run that test; otherwise go straight to production.
- Upload keystore: `~/Keys/tatsu/upload.jks`, password in `~/.gradle/gradle.properties`. Both need a backup.
- Upload is done by hand by the user, as with App Store Connect.

## Execution and git

- One subagent per plan task, with a review between tasks.
- After each reviewed task, push the branch: `git push -u origin android`.
- After the last task, merge `android` into `main` with a merge commit (`--no-ff`, like the `swiftui` merge) and push `main`.
- Tags are per platform from now on: `android-X.Y` and `iOS-X.Y` (exact case, no `v`). `android-0.1` is created and pushed only after the user confirms the Play upload.
- Commits use the repo-local Abbabon identity, with no trailers.
