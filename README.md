<p align="center"><img src="logo.png" width="160" alt="Tatsu"></p>
<h1 align="center">Tatsu</h1>
<p align="center"><b>断</b> · <i>tatsu</i> · to cut off, to sever<br>Offline kanji lookup for iPhone, iPad and Mac.</p>

---

Type English, hiragana, katakana, romaji, or paste a sentence of kanji. Get the character, every reading with its kana and romaji, meanings, stroke count, school grade, JLPT level, and frequency rank. No account, no network.

## Install

From the App Store (iPhone, iPad and Mac). To build it yourself, open `Tatsu.xcodeproj` in Xcode 26 or later and press Run.

## Search

| You type | You get |
|---|---|
| `water` | 水 first, then anything meaning water |
| `watr`, `mountian` | same results, one typo or swapped letters is forgiven |
| `みず` / `ミズ` | kana readings, katakana is normalised |
| `taberu` | romaji readings, plus near misses like 立てる *tateru* |
| `日本語` | each kanji in the text, in order |

Ranking: exact match, then prefix, then whole word, then substring, then one edit away, then scattered letters. Ties go to the more frequent kanji.

Tap a kanji to see its details. Copy it from the detail view, or long-press (right-click on Mac) a result. Kanji you open or copy appear under Recent when the search box is empty. Recent lookups stay on the device.

Can't type a kanji on iPhone? Add the Chinese – Handwriting keyboard and draw it.

## Project layout

| Path | Role |
|---|---|
| `Tatsu/Search.swift` | Kana to romaji, normalisation, scoring. |
| `Tatsu/Kanji.swift` | Data model and loader. |
| `Tatsu/kanji.json` | Generated from KANJIDIC2, committed so the app builds offline. |
| `Tatsu/ContentView.swift`, `KanjiDetail.swift`, `AboutView.swift` | SwiftUI interface, shared by all platforms. |
| `Tatsu/Recent.swift` | Recent lookups (SwiftData). |
| `TatsuTests/` | Search, formatting and recent lookup tests. |
| `build_data.py` | Regenerates `kanji.json` from the latest KANJIDIC2. |

## Test

```sh
xcodebuild -project Tatsu.xcodeproj -scheme Tatsu -destination 'platform=macOS' CODE_SIGNING_ALLOWED=NO test
```

## Update the dictionary

```sh
python3 build_data.py
```

Then run the tests.

## Data and license

Kanji data is [KANJIDIC2](https://www.edrdg.org/wiki/index.php/KANJIDIC_Project) by the Electronic Dictionary Research and Development Group, used under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). App code is MIT.
