<p align="center"><img src="logo.png" width="160" alt="Tatsu"></p>
<h1 align="center">Tatsu</h1>
<p align="center"><b>断</b> · <i>tatsu</i> · to cut off, to sever<br>Offline kanji lookup for macOS.</p>

---

Type English, hiragana, katakana, romaji, or paste a sentence of kanji. Get the character, every reading as furigana with romaji, meanings, stroke count, school grade, JLPT level, and frequency rank. No account, no network, no Electron.

## Install

Needs the Xcode command line tools (`xcode-select --install`) for `swiftc`.

```sh
git clone https://github.com/Abbabon/tatsu-kanji
cd tatsu-kanji
./make_app.sh install     # builds Tatsu.app and copies it to /Applications
```

Without `install` the app is left next to the script. You can also skip the app and `open index.html` in any browser.

## Search

| You type | You get |
|---|---|
| `water` | 水 first, then anything meaning water |
| `watr`, `mountian` | same results, one typo or swapped letters is forgiven |
| `みず` / `ミズ` | kana readings, katakana is normalised |
| `taberu` | romaji readings, plus near misses like 立てる *tateru* |
| `日本語` | each kanji in the text, in order |

Ranking: exact match, then prefix, then whole word, then substring, then one edit away, then scattered letters. Ties go to the more frequent kanji.

## Keys

- Type anywhere, the search box takes it.
- `Esc` clears.
- Click a kanji to copy it.
- Dark mode follows the system.

## Project layout

| File | Role |
|---|---|
| `index.html` | The whole UI. Furigana uses native `<ruby>`. |
| `search.js` | Kana to romaji, normalisation, scoring. Shared with the test. |
| `data.js` | Generated from KANJIDIC2. 10,384 kanji, committed so the app works offline out of the box. |
| `build_data.py` | Regenerates `data.js` from the latest KANJIDIC2. |
| `main.swift` | 30-line WKWebView window. |
| `make_app.sh` | Builds the `.app`, renders the icon from `logo.svg`. |
| `test.js` | `node test.js` checks romaji and ranking. |

## Update the dictionary

```sh
python3 build_data.py
node test.js
./make_app.sh install
```

## Data and license

Kanji data is [KANJIDIC2](https://www.edrdg.org/wiki/index.php/KANJIDIC_Project) by the Electronic Dictionary Research and Development Group, used under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). App code is MIT.
