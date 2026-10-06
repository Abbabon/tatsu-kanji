# Google Play Console metadata: Tatsu 0.1

Paste-ready values for the 0.1 release. Counts are `len()` of the text in each block.

## App

- App name (21 / 30): `Tatsu – Offline Kanji`
- Default language: English (United States)
- App or game: App. Free or paid: Free.
- Package name: `com.abbabon.kanjioffline` (cannot change after the first upload)
- Category: Education. Tags: pick "Education" and "Dictionaries" if offered.
- Contact email: the account email. Website: `https://github.com/Abbabon/tatsu-kanji`
- Privacy policy URL: `https://github.com/Abbabon/tatsu-kanji/blob/main/PRIVACY.md`

### Short description (80 / 80)

    Offline kanji dictionary. Search by English, kana or romaji. No account, no ads.

### Full description (1591 / 4000)

    Tatsu is a kanji dictionary that works entirely offline. Type a word, get the kanji. There is no account, no network access and no tracking.

    Search in English, hiragana, katakana or romaji, or paste a sentence of kanji to see each character in order. Small typos are forgiven, and results are ranked by how often the kanji is used, so common characters come first.

    Open a kanji to see its on and kun readings with kana and romaji, its meanings, name readings, stroke count, school grade, JLPT level and frequency rank. Copy a kanji with one tap. Kanji you open or copy are listed under Recent, stored only on your device. You can remove items or clear the list at any time.

    Tatsu covers about 10,300 kanji from KANJIDIC2.

    • Search by English, hiragana, katakana, romaji or pasted kanji
    • Typo tolerance
    • Results ranked by frequency
    • On and kun readings with kana and romaji
    • Meanings and name readings
    • Stroke count, school grade, JLPT level, frequency rank
    • Copy any kanji
    • Recent lookups, kept on the device
    • Select Japanese text in any app and choose Tatsu from the menu to look it up
    • Phones, tablets and foldables, with light, dark and dynamic colour themes
    • Tablets: use a hardware keyboard to type anywhere, move through results with the arrow keys and press Ctrl+F to search
    • Can't type a kanji? Add Japanese handwriting to Gboard and draw it
    • No account, no ads, no analytics, no network

    Tatsu is free. Kanji data is KANJIDIC2 by the Electronic Dictionary Research and Development Group, used under CC BY-SA 4.0. The app code is MIT licensed and available on GitHub.

## App content declarations

- **Privacy policy:** URL above.
- **Ads:** No, the app contains no ads.
- **App access:** All functionality is available without special access (no login).
- **Content rating (IARC questionnaire):** category "Reference, News, or Educational". Answer "No" to every question (violence, sexual content, language, controlled substances, gambling, user-generated content, location sharing, digital purchases). Expected rating: Everyone / PEGI 3 / USK 0.
- **Target audience:** 13 and over only. Do not select any under-13 age group (that would pull the app into the Families policy for no benefit).
- **News app:** No. **COVID-19 contact tracing or status:** No. **Government app:** No. **Financial features:** none. **Health features:** none.
- **Data safety:** "Does your app collect or share any of the required user data types?" No. Because the answer is No, the encryption and deletion questions do not apply. This matches the manifest: no `INTERNET` permission, no SDKs.
- **Content rights:** the app contains third-party content (KANJIDIC2, CC BY-SA 4.0); credit is in the About screen.

## Graphics (files in this repo)

- App icon: `docs/playstore/icon-512.png` (512×512 PNG).
- Feature graphic: `docs/playstore/feature-graphic.png` (1024×500 PNG, no transparency).
- Screenshots: `docs/playstore/screenshots/`: `phone-*` and `tablet10-*`, light mode only (3 scenes each, 6 files). Phone shots are 1080×2160 (Play rejects an aspect ratio beyond 2:1).

## Release notes (en-US)

    First release of Tatsu for Android: offline kanji lookup with readings, meanings and recent lookups, for phones, tablets and foldables.

## Testing before production

Upload the first AAB to internal testing. If Play Console then shows the closed-testing requirement banner (it applies to personal accounts created after 13 November 2023; whether it applies here is not known yet), run a closed test with at least 12 testers opted in for 14 continuous days before applying for production access. If there is no banner, go straight to production.
