# Tatsu Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Tatsu on Google Play for Android phones, tablets and foldables: everything iOS 0.1 does, in Material 3, plus four Android-native hooks (text-selection menu, edge-to-edge with predictive back, themed icon, Gboard handwriting hint).

**Architecture:** One Gradle module `app` in `android/`. Kotlin, Jetpack Compose, Material 3. Pure logic (`Search.kt`, `Format.kt`) has no Android imports and is tested on the JVM against the real `Tatsu/kanji.json`. One `AndroidViewModel` owns data, query, results, selection and recents; recents persist in Jetpack DataStore. The UI is a `ListDetailPaneScaffold` (one pane on phones, two on tablets and unfolded foldables). No DI framework, no Room, no extra modules.

**Tech Stack:** Kotlin 2.4.20, AGP 9.4.1 (built-in Kotlin), Gradle 9.8.0, JDK 17, Compose BOM 2026.09.00, material3-adaptive 1.3.0, DataStore 1.2.1, kotlinx-serialization 1.11.0, JUnit 4, compose-ui-test.

**Spec:** `docs/superpowers/specs/2026-10-06-tatsu-android-design.md`. The iOS sources in `Tatsu/*.swift` and tests in `TatsuTests/*.swift` are the behavioural reference; this plan ports them. Read the spec before starting.

**Execution:** one subagent per task, with a review between tasks. After each reviewed task, push the branch (see "Execution and git" below).

## Global Constraints

- Separate native app in `android/` in this repo. Kotlin, Jetpack Compose, Material 3. No code shared with Swift; data and test cases are shared.
- Phones, tablets and foldables, one adaptive layout. ChromeOS gets no extra polish.
- minSdk 26. targetSdk and compileSdk are the latest stable SDK at implementation time (36 or newer, as Play requires). This plan pins 36 (see Pinned versions).
- Package `com.abbabon.kanjioffline` (never changes after the first upload). versionName `0.1`, versionCode `1`.
- Store name "Tatsu – Offline Kanji", launcher name "Tatsu".
- No network: no `INTERNET` permission.
- Data safety: no data collected, none shared. Recents stay on the device.
- `Tatsu/kanji.json` is copied into the APK assets by Gradle at build time and never committed a second time. `build_data.py` stays the only generator.
- Persistence: Jetpack DataStore (Preferences), recent list as one ordered string, newest first, capped at 100. No Room.
- English only. All UI strings in `strings.xml`, including the grade and section labels that iOS hardcodes.
- Search: same ranking, same typo tolerance, limit 60, runs on every keystroke off the main thread, a newer query cancels the older one.
- Recent is recorded on explicit picks only: tap, Enter, Copy. Arrow-key movement does not record.
- Dependencies are only: Compose BOM, `material3`, `material3-adaptive` (plus its navigation artifact), `activity-compose`, `lifecycle-viewmodel-compose`, `material-icons-core` (BOM-managed, for the search/info/back/copy icons), `datastore-preferences`, `kotlinx-serialization-json`; tests add JUnit 4 and `compose-ui-test` (plus `androidx.test.ext:junit` as the instrumented runner). Versions live in a Gradle version catalog.
- All text showing kanji or kana sets `LocaleList("ja")`. Text sizes are in `sp`.
- Release builds use R8 minify and resource shrinking.
- `Search.kt` and `Format.kt` import nothing from `android.*` / `androidx.*`.
- Commits: git identity must be `Abbabon <1280330+Abbabon@users.noreply.github.com>` (check `git config user.name` and `git config user.email` before the first commit). Never use `--author` or `-c user.*`. **Never** add `Co-Authored-By` or any other attribution trailer.
- Never drive the Mac UI (no osascript/System Events clicks or keystrokes). Emulators run with `-no-window`, driven only through `adb`. Instrumented tests run on an emulator only, never on a physical device without asking the user.
- Work happens on branch `android` in the worktree `/Users/amit/repos/kanji-offline-android` (already created; the spec and plan are already committed there).

## Pinned versions

Checked on 2026-10-06 (Google Maven `dl.google.com/android/maven2/.../maven-metadata.xml`, Maven Central metadata, `services.gradle.org/versions/current`, and the AGP 9.4 release notes and Play target-API policy pages). Latest stable (non-alpha/beta/rc) at that date:

| Item | Version | Note |
|---|---|---|
| Android Gradle plugin | 9.4.1 | 9.5.0 is alpha only. Needs Gradle 9.6.0+, JDK 17, supports up to API 37. Has built-in Kotlin, so no `org.jetbrains.kotlin.android` plugin. |
| Gradle | 9.8.0 | current release |
| Kotlin (+ Compose compiler + serialization plugins) | 2.4.20 | |
| Compose BOM | 2026.09.00 | manages `material3`, icons, ui-test |
| material3-adaptive (`adaptive-layout`, `adaptive-navigation`) | 1.3.0 | 1.4.0 is alpha |
| datastore-preferences | 1.2.1 | |
| activity-compose | 1.13.0 | |
| lifecycle-viewmodel-compose | 2.11.0 | |
| kotlinx-serialization-json | 1.11.0 | |
| JUnit | 4.13.2 | |
| androidx.test.ext:junit | 1.3.0 | |
| compileSdk / targetSdk | 36 | Play requires API 36+ for new apps and updates from 2026-08-31. API 37 is also available; 36 is enough and keeps the SDK download small. |
| build-tools | 36.0.0 | already installed |
| JDK | Temurin 17 | `brew install --cask temurin@17` |

If a version fails to resolve or two artifacts conflict, run `./gradlew :app:dependencies --configuration debugRuntimeClasspath`, fix the catalog, and note the change in the commit message. Do not add dependencies to work around a problem.

## Review Focus

Inputs the spec implies but no obvious test covers. Each line gets a test in the task named in brackets.

1. **Pasted kanji outside the BMP** (`𠮟`, U+20B9F, one of 8 such entries in the data) must be listed as one whole kanji, not split into surrogate halves. [Task 4]
2. **Very long query** (5,000 characters of Latin letters, as a text-selection hand-off could deliver) returns without crashing or hanging. [Task 4]
3. **Non-English device locale** (for example Arabic) must still print ASCII digits in the meta line, "4 strokes", not locale digits. [Task 5]
4. **Recents round-trip a non-BMP kanji** and survive being stored as one string. [Task 6]
5. **Rotation after a text-selection hand-off** must not reset the query the user has since typed back to the original selected text. [Task 12]

## File Map

| File (under `android/` unless noted) | Responsibility | Task |
|---|---|---|
| `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml` | Build config, version catalog | 2 |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Gradle wrapper | 2 |
| `app/build.gradle.kts` | Module config, kanji.json copy task, dependencies, release config | 2, 14 |
| `app/proguard-rules.pro` | R8 rules (empty, comment only) | 2 |
| `app/src/main/AndroidManifest.xml` | Manifest | 2, 11, 13 |
| `app/src/main/res/values/strings.xml` | Strings | 2, 7 |
| `app/src/main/res/values/themes.xml`, `values-night/themes.xml` | Window theme | 2 |
| `app/src/main/res/drawable/ic_launcher_*.xml`, `mipmap-anydpi-v26/ic_launcher.xml` | Adaptive + themed icon | 13 |
| `app/src/main/java/com/abbabon/kanjioffline/Kanji.kt` | Model, JSON parse, asset load | 3 |
| `.../Search.kt` | `toHira`, `romaji`, `Searcher` (pure) | 4 |
| `.../Format.kt` | `readingParts`, `Labels`, `gradeLabel`, `metaLine`, `stepSelection`, `isTypable`, `mergeOrder` (pure) | 5 |
| `.../Recents.kt` | DataStore wrapper | 6 |
| `.../Theme.kt` | Dynamic colour with static fallback, `ja()` text style | 7 |
| `.../TatsuViewModel.kt` | State holder (incl. the hand-off event) | 7 |
| `.../DetailPane.kt` | Detail view | 8 |
| `.../AboutSheet.kt` | About bottom sheet | 8 |
| `.../ListPane.kt` | Search field, rows, recents, empty states | 9 |
| `.../TatsuScreen.kt` | Adaptive scaffold, copy, key handling, text-selection hand-off and back (small addition to the spec's file list: the glue between the panes) | 9, 10, 11 |
| `.../MainActivity.kt` | Edge-to-edge, theme, `EXTRA_PROCESS_TEXT` | 9, 11 |
| `app/src/test/java/com/abbabon/kanjioffline/{TestData,DataTests,SearchTests,FormatTests,RecentsTest}.kt` | JVM tests | 3-6 |
| `app/src/androidTest/java/com/abbabon/kanjioffline/AppTest.kt` | Instrumented Compose test | 12 |
| `CLAUDE.md`, `README.md`, `PRIVACY.md` (repo root) | Docs | 15 |
| `.gitignore` (repo root) | Ignores | 2 |
| `docs/playstore.md`, `docs/playstore/screenshots/*`, `docs/playstore/feature-graphic.png`, `docs/playstore/icon-512.png` | Store listing | 15, 16 |

## Conventions used below

Run every shell block from the repo root `/Users/amit/repos/kanji-offline-android` (the `android` worktree) unless it says otherwise. `ENV` below means this block, run once per shell (shell state does not persist between tool calls, so repeat it at the top of each command):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH
export SCRATCH=/private/tmp/claude-502/tatsu-android-scratch; mkdir -p "$SCRATCH"
```

- **TEST** = `cd android && ./gradlew test` (JVM unit tests, no emulator).
- **BUILD** = `cd android && ./gradlew assembleDebug`.
- If the executor can read images, "look at the screenshot" means: `adb exec-out screencap -p > "$SCRATCH/x.png"` then read the PNG. `SCRATCH` is set by `ENV` to a fixed scratch directory outside the repo; do not re-create it with `mktemp -d` (that gives a new directory per call and loses files such as `$FONT`).
- The compile-checked Kotlin below is written against the pinned versions but could not be compiled when this plan was written. If a Compose or adaptive API name or signature is rejected, fix it against the compiler error (and the library's source/docs) while keeping the behaviour described. Do not change behaviour to dodge a compile error.

## Execution and git

- One subagent per task, with a review between tasks.
- The per-task `git commit` blocks below commit only. After each task has been reviewed, push the branch: `git push -u origin android` (the first push sets the upstream).
- Commits are authored by the repo-local identity Abbabon; no trailers.
- After Task 16 (and the final verification), merge `android` into `main` with a merge commit, no fast-forward, like the earlier `swiftui` merge, and push `main`: `git checkout main && git merge --no-ff android && git push origin main`. Run it from the main checkout `/Users/amit/repos/kanji-offline` (git does not allow `main` to be checked out in two worktrees at once; the `android` worktree keeps `android`).
- Tags are per platform from now on: `android-X.Y` and `iOS-X.Y` (exact case, no `v`). `android-0.1` is created and pushed only after the user confirms the Play upload (last step of the handover). Do not tag before that.

---

### Task 1: Toolchain from zero

No repo changes. Deliverable: a JDK, SDK command-line tools, the API 36 platform, and three emulators that boot headless.

**Interfaces:**
- Consumes: nothing.
- Produces: `ENV` works (`java -version` says 17); `sdkmanager` and `avdmanager` on PATH; AVDs `tatsu_phone`, `tatsu_tablet10`, `tatsu_fold`; `adb devices` lists an emulator when one is running.

What to verify: this Mac has the Android SDK (platforms: only `android-35`; build-tools 35.0.1 and 36.0.0; emulator; arm64 system images for API 36) but no JDK, no command-line tools (so no `sdkmanager`/`avdmanager`), and no AVDs.

- [x] **Step 1: Branch and docs commit** — already done: worktree `/Users/amit/repos/kanji-offline-android` on branch `android`, spec and plan committed. Just confirm `git branch --show-current` says `android` and the identity is Abbabon.

- [ ] **Step 2: Install JDK 17**

```bash
brew install --cask temurin@17
/usr/libexec/java_home -v 17
```

Expected: a path like `/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`. The cask runs a macOS installer that may ask for the user's password. If it prompts and cannot continue, stop and ask the user to run `brew install --cask temurin@17` in their own terminal. Fallback with no password: `brew install openjdk@17`, then `export JAVA_HOME=$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home` in `ENV`.

- [ ] **Step 3: Install the SDK command-line tools, then the platform**

```bash
brew install --cask android-commandlinetools
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
SDKM=$(brew --prefix)/share/android-commandlinetools/cmdline-tools/latest/bin/sdkmanager
yes | $SDKM --sdk_root=$HOME/Library/Android/sdk --licenses
$SDKM --sdk_root=$HOME/Library/Android/sdk "cmdline-tools;latest" "platform-tools" "platforms;android-36" "build-tools;36.0.0" "emulator"
```

Accepting the licences is a legal acknowledgement made on the user's behalf; tell the user in the report that it was done. Expected: `$HOME/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager` now exists and `platforms/android-36` exists. From here on use the SDK copy (it is on PATH via `ENV`).

- [ ] **Step 4: Create the AVDs**

```bash
# ENV
sdkmanager --list_installed | grep system-images        # expect android-36 google_apis_playstore arm64-v8a
avdmanager list device -c | grep -E "^(pixel_8|pixel_tablet|pixel_fold)"
IMG="system-images;android-36;google_apis_playstore;arm64-v8a"
avdmanager create avd -n tatsu_phone    -k "$IMG" -d pixel_8
avdmanager create avd -n tatsu_tablet10 -k "$IMG" -d pixel_tablet
avdmanager create avd -n tatsu_fold     -k "$IMG" -d pixel_fold
```

If `pixel_8` is not listed use `pixel_7`. Answer `no` to any custom hardware profile prompt.

- [ ] **Step 5: Boot the phone emulator headless and confirm it**

```bash
# ENV
nohup emulator -avd tatsu_phone -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect > "$SCRATCH/emu.log" 2>&1 &
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 3; done
adb devices
adb shell getprop ro.build.version.sdk
```

Expected: `emulator-5554  device` and `36`. Leave it running for later tasks. `-no-window` keeps it off the user's screen (the user can drop that flag to watch). Kill with `adb emu kill` when finished.

- [ ] **Step 6: Report**

No commit in this step. Report the JDK path, installed SDK packages, the AVD names, and that licences were accepted.

---

### Task 2: Gradle skeleton, version catalog, data asset, placeholder activity

**Files:**
- Create: `android/gradle/wrapper/*`, `android/gradlew`, `android/gradlew.bat`
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/libs.versions.toml`, `android/local.properties` (ignored)
- Create: `android/app/build.gradle.kts`, `android/app/proguard-rules.pro`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/res/values/strings.xml`, `values/themes.xml`, `values-night/themes.xml`
- Create: `android/app/src/main/java/com/abbabon/kanjioffline/MainActivity.kt` (placeholder)
- Modify: `.gitignore`

**Interfaces:**
- Consumes: the toolchain from Task 1.
- Produces: `./gradlew assembleDebug` and `./gradlew test` work from `android/`; the APK contains `assets/kanji.json`; the app installs and shows placeholder text on the emulator; the catalog aliases used by later tasks (`libs.compose.bom`, `libs.compose.material3`, `libs.compose.material.icons.core`, `libs.adaptive.layout`, `libs.adaptive.navigation`, `libs.activity.compose`, `libs.lifecycle.viewmodel.compose`, `libs.datastore.preferences`, `libs.kotlinx.serialization.json`, `libs.junit`, `libs.androidx.test.ext.junit`, `libs.compose.ui.test.junit4`, `libs.compose.ui.test.manifest`).

- [ ] **Step 1: Generate the Gradle wrapper without installing Gradle**

```bash
# ENV
curl -fsSL -o "$SCRATCH/gradle.zip" https://services.gradle.org/distributions/gradle-9.8.0-bin.zip
SUM=$(curl -fsSL https://services.gradle.org/distributions/gradle-9.8.0-bin.zip.sha256)
echo "$SUM  $SCRATCH/gradle.zip" | shasum -a 256 -c -
unzip -q "$SCRATCH/gradle.zip" -d "$SCRATCH"
mkdir -p android && cd android
"$SCRATCH/gradle-9.8.0/bin/gradle" wrapper --gradle-version 9.8.0 --distribution-type bin --gradle-distribution-sha256-sum "$SUM"
./gradlew --version
```

Expected: `gradle.zip: OK`, then `Gradle 9.8.0` and a JVM line with version 17.

- [ ] **Step 2: Write the build files**

`android/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Tatsu"
include(":app")
```

`android/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

`android/gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.caching=true
android.useAndroidX=true
kotlin.code.style=official
```

`android/local.properties` (never committed):

```properties
sdk.dir=/Users/amit/Library/Android/sdk
```

`android/gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.4.1"
kotlin = "2.4.20"
composeBom = "2026.09.00"
adaptive = "1.3.0"
datastore = "1.2.1"
activityCompose = "1.13.0"
lifecycle = "2.11.0"
serialization = "1.11.0"
junit = "4.13.2"
androidxTestExt = "1.3.0"

[libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons-core = { module = "androidx.compose.material:material-icons-core" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
adaptive-layout = { module = "androidx.compose.material3.adaptive:adaptive-layout", version.ref = "adaptive" }
adaptive-navigation = { module = "androidx.compose.material3.adaptive:adaptive-navigation", version.ref = "adaptive" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
junit = { module = "junit:junit", version.ref = "junit" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExt" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

`android/app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// kanji.json lives once, in Tatsu/. Copy it into a generated assets folder at build time.
val kanjiAssetsDir = layout.buildDirectory.dir("generated/kanjiAssets")
val copyKanjiData = tasks.register<Copy>("copyKanjiData") {
    from(rootProject.file("../Tatsu/kanji.json"))
    into(kanjiAssetsDir)
}

android {
    namespace = "com.abbabon.kanjioffline"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.abbabon.kanjioffline"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    sourceSets.getByName("main").assets.srcDir(files(kanjiAssetsDir).builtBy(copyKanjiData))

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

// JVM tests read the real data file from the repo
tasks.withType<Test>().configureEach {
    systemProperty("kanji.json", rootProject.file("../Tatsu/kanji.json").absolutePath)
    inputs.file(rootProject.file("../Tatsu/kanji.json"))
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.adaptive.layout)
    implementation(libs.adaptive.navigation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
```

If AGP 9 rejects `compileSdk = 36` or `sourceSets.getByName("main")`, use the form its error message suggests (AGP 9's new DSL offers `compileSdk { version = release(36) }`). The behaviour must stay: compile and target 36, kanji.json in the APK assets.

`android/app/proguard-rules.pro`:

```
# kotlinx-serialization ships its own consumer rules; nothing to add yet.
```

- [ ] **Step 3: Manifest, strings, theme, placeholder activity**

`android/app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Tatsu">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`allowBackup="false"` keeps the "recents never leave the device" promise (no Google Drive backup of the recents file). There is deliberately no `<uses-permission>` at all.

`android/app/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Tatsu</string>
</resources>
```

`android/app/src/main/res/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Tatsu" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

`android/app/src/main/res/values-night/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Tatsu" parent="android:Theme.Material.NoActionBar" />
</resources>
```

`android/app/src/main/java/com/abbabon/kanjioffline/MainActivity.kt` (placeholder, replaced in Task 9):

```kotlin
package com.abbabon.kanjioffline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Tatsu") }
    }
}
```

- [ ] **Step 4: Ignore build output**

Append to the repo-root `.gitignore`:

```
android/.gradle/
android/.kotlin/
android/.idea/
android/build/
android/app/build/
local.properties
*.jks
*.keystore
```

- [ ] **Step 5: Build, check the data asset, run on the emulator**

```bash
# ENV (emulator from Task 1 must be running)
cd android && ./gradlew assembleDebug test
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep kanji.json
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.abbabon.kanjioffline/.MainActivity
adb exec-out screencap -p > "$SCRATCH/launch.png"
```

Expected: `BUILD SUCCESSFUL` (the `test` task reports "NO-SOURCE"), the `unzip -l` line shows `assets/kanji.json` of about 1.3 MB, install prints `Success`, and the screenshot shows the word "Tatsu" in the top-left under the status bar. First build downloads Gradle plugins and dependencies and can take several minutes. Also confirm there is no network permission: `$ANDROID_HOME/build-tools/36.0.0/aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk | grep -c INTERNET || true` must print `0` (the compiled manifest is binary XML, so do not use `strings`; `aapt2 dump permissions` should list `package: com.abbabon.kanjioffline` and no INTERNET line).

- [ ] **Step 6: Commit**

```bash
git add .gitignore android/gradle android/gradlew android/gradlew.bat android/settings.gradle.kts android/build.gradle.kts android/gradle.properties android/app
git status --short   # local.properties and build/ must not appear
git commit -m "Android: Gradle project, version catalog, kanji.json asset copy"
```

---

### Task 3: Kanji model and data tests

**Files:**
- Create: `android/app/src/main/java/com/abbabon/kanjioffline/Kanji.kt`
- Create: `android/app/src/test/java/com/abbabon/kanjioffline/TestData.kt`
- Create: `android/app/src/test/java/com/abbabon/kanjioffline/DataTests.kt`

**Interfaces:**
- Consumes: the `kanji.json` system property set in `app/build.gradle.kts`.
- Produces:
  - `@Serializable data class Kanji(val k: String, val m: List<String>, val on: List<String>, val kun: List<String>, val n: List<String>, val s: Int, val g: Int, val j: Int, val f: Int)`. Same fields and meaning as `Kanji.swift` (`k` the character, `m` meanings, `on` katakana, `kun` hiragana with `.` and `-`, `n` name readings, `s` strokes, `g` grade 0=none, `j` JLPT 0=none, `f` frequency rank 0=none).
  - `fun parseKanji(text: String): List<Kanji>` (pure).
  - `fun Context.loadKanji(): List<Kanji>` (reads asset `kanji.json`).
  - Test helper `object TestData { val all: List<Kanji>; val searcher: Searcher }` (the `searcher` property is added in Task 4).

- [ ] **Step 1: Write the failing test**

`android/app/src/test/java/com/abbabon/kanjioffline/TestData.kt`:

```kotlin
package com.abbabon.kanjioffline

import java.io.File

/** The real data file, shared by all JVM tests. */
object TestData {
    val all: List<Kanji> by lazy {
        parseKanji(File(System.getProperty("kanji.json")!!).readText())
    }
}
```

`android/app/src/test/java/com/abbabon/kanjioffline/DataTests.kt`:

```kotlin
package com.abbabon.kanjioffline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataTests {
    @Test
    fun bundledDataLoads() {
        val all = TestData.all
        // 10,348 in the current KANJIDIC2 build; the exact count changes when build_data.py is re-run
        assertTrue(all.size > 10_000)
        assertEquals("日", all[0].k)
        val water = all.first { it.k == "水" }
        assertTrue("water" in water.m)
        assertTrue("みず" in water.kun)
        assertEquals(4, water.s)
    }

    @Test
    fun kanjiIDsAreUnique() {
        val all = TestData.all
        assertEquals(all.size, all.map { it.k }.toSet().size)
    }

    // 8 entries are outside the BMP (one UTF-16 surrogate pair each); every k is exactly one code point
    @Test
    fun everyKanjiIsOneCodePoint() {
        assertTrue(TestData.all.all { it.k.codePointCount(0, it.k.length) == 1 })
        assertNotNull(TestData.all.firstOrNull { it.k == "𠮟" })
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: TEST (`cd android && ./gradlew test`)
Expected: FAIL to compile, `Unresolved reference 'parseKanji'` / `'Kanji'`.

- [ ] **Step 3: Implement**

`android/app/src/main/java/com/abbabon/kanjioffline/Kanji.kt`:

```kotlin
package com.abbabon.kanjioffline

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One KANJIDIC2 entry. Short keys match kanji.json (see build_data.py). */
@Serializable
data class Kanji(
    val k: String,            // the character
    val m: List<String>,      // English meanings
    val on: List<String>,     // on'yomi, katakana
    val kun: List<String>,    // kun'yomi, hiragana; "." marks okurigana, "-" marks a prefix/suffix
    val n: List<String>,      // nanori (name readings)
    val s: Int,               // stroke count
    val g: Int,               // school grade, 0 = none
    val j: Int,               // JLPT level (old 1-4 scale), 0 = none
    val f: Int,               // frequency rank, 0 = unranked
)

private val json = Json { ignoreUnknownKeys = true }

fun parseKanji(text: String): List<Kanji> = json.decodeFromString(text)

/** Reads the bundled asset. A failure here is a build bug, so let it crash (like fatalError on iOS). */
fun Context.loadKanji(): List<Kanji> =
    assets.open("kanji.json").use { parseKanji(it.readBytes().decodeToString()) }
```

- [ ] **Step 4: Run to verify it passes**

Run: TEST
Expected: PASS, 3 tests in `DataTests`. Open `android/app/build/reports/tests/testDebugUnitTest/index.html` only if something fails.

- [ ] **Step 5: Commit**

```bash
git add android/app/src
git commit -m "Android: Kanji model, JSON parsing and data tests"
```

---

### Task 4: Search (toHira, romaji, Searcher)

Port of `Tatsu/Search.swift` and the `RomajiTests`/`SearchTests` structs in `TatsuTests/SearchTests.swift`. Pure Kotlin, no Android imports.

**Files:**
- Create: `android/app/src/main/java/com/abbabon/kanjioffline/Search.kt`
- Create: `android/app/src/test/java/com/abbabon/kanjioffline/SearchTests.kt`
- Modify: `android/app/src/test/java/com/abbabon/kanjioffline/TestData.kt`

**Interfaces:**
- Consumes: `Kanji`, `TestData.all` (Task 3).
- Produces:
  - `fun toHira(s: String): String`
  - `fun romaji(kana: String): String`
  - `class Searcher(list: List<Kanji>)` with `fun kanji(character: String): Kanji?` and `fun search(query: String, limit: Int = 60): List<Kanji>`
  - `TestData.searcher: Searcher`

Behaviour that must match iOS exactly: score ladder 100 equal, 80 prefix, 70 whole word, 55 substring, 50 within one edit (query length 4+, per word), 30 subsequence (query length 3+); ties go to higher frequency (rank 0 sorts last as 9999), then data order; kanji in the query short-circuit to "list each known kanji once, in order"; kana queries match only kana readings, anything else matches meanings and romaji.

- [ ] **Step 1: Add the shared searcher to the test helper**

Append to `TestData` (inside the object):

```kotlin
    val searcher: Searcher by lazy { Searcher(all) }
```

- [ ] **Step 2: Write the failing tests**

`android/app/src/test/java/com/abbabon/kanjioffline/SearchTests.kt`:

```kotlin
package com.abbabon.kanjioffline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomajiTests {
    @Test
    fun romajiConversion() {
        assertEquals("kyou", romaji("きょう"))
        assertEquals("nichi", romaji("ニチ"))
        assertEquals("taberu", romaji("た.べる"))
        assertEquals("matcha", romaji("まっちゃ"))
        assertEquals("gakkou", romaji("がっこう"))
        assertEquals("juu", romaji("じゅう"))
    }

    @Test
    fun sokuonAtEndDoesNotCrash() {
        assertEquals("a", romaji("あっ"))
    }

    @Test
    fun katakanaToHiragana() {
        assertEquals("みず", toHira("ミズ"))
        assertEquals("water", toHira("water"))
    }
}

class SearchTests {
    private val searcher = TestData.searcher

    private fun first(q: String) = searcher.search(q).firstOrNull()?.k
    private fun top3(q: String) = searcher.search(q).take(3).map { it.k }
    private fun keys(q: String) = searcher.search(q).map { it.k }

    @Test
    fun rankingMatchesWebVersion() {
        assertEquals("水", first("water"))
        assertEquals("水", first("みず"))
        assertEquals("水", first("ミズ"))
        assertEquals("水", first("mizu"))
        assertEquals("日", first("sun"))
        assertEquals("日", first("nichi"))
        assertEquals("食", first("taberu"))
    }

    @Test
    fun kanjiTextListsEachKanji() {
        assertEquals(listOf("日", "本", "語"), keys("日本語"))
    }

    @Test
    fun typoTolerance() {
        assertTrue("水" in top3("watr"))
        assertTrue("山" in top3("mountian"))
    }

    @Test
    fun blankQueryIsEmpty() {
        assertTrue(searcher.search("   ").isEmpty())
        assertTrue(searcher.search("").isEmpty())
        assertTrue(searcher.search("　").isEmpty())
    }

    @Test
    fun uppercaseMatchesLowercase() {
        assertEquals("水", first("WATER"))
    }

    @Test
    fun repeatedKanjiListedOnce() {
        assertEquals(listOf("日", "本"), keys("日日本"))
    }

    @Test
    fun kanjiMixedWithLatinListsOnlyKanji() {
        assertEquals(listOf("日", "本"), keys("日本 water"))
    }

    @Test
    fun unknownCharactersReturnNothing() {
        assertTrue(searcher.search("🍣").isEmpty())
        assertTrue(searcher.search("〇〇").isEmpty())
    }

    @Test
    fun lookupByCharacter() {
        assertTrue(searcher.kanji("水")?.m?.contains("water") == true)
        assertNull(searcher.kanji("x"))
    }

    @Test
    fun limitIsRespected() {
        assertTrue(searcher.search("a", limit = 5).size <= 5)
        assertTrue(searcher.search("a").size <= 60)
    }

    // Review Focus 1: a kanji outside the BMP is one code point, listed whole and once
    @Test
    fun nonBmpKanjiIsListedWhole() {
        assertNotNull(searcher.kanji("𠮟"))
        assertEquals(listOf("𠮟", "日", "本"), keys("𠮟日𠮟本"))
    }

    // Review Focus 2: a huge pasted query neither crashes nor hangs (generous 5 s bound)
    @Test
    fun veryLongQueryReturns() {
        val start = System.nanoTime()
        searcher.search("a".repeat(5_000))
        val r = searcher.search("mizu".repeat(1_250))
        assertTrue(r.size <= 60)
        assertTrue(searcher.search("a".repeat(5_000)).size <= 60)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5_000)
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: TEST
Expected: FAIL to compile, `Unresolved reference 'romaji'` / `'Searcher'`.

- [ ] **Step 4: Implement**

`android/app/src/main/java/com/abbabon/kanjioffline/Search.kt`:

```kotlin
package com.abbabon.kanjioffline

import kotlin.math.abs

/** Katakana (ァ-ヶ) to hiragana; everything else unchanged. */
fun toHira(s: String): String = buildString(s.length) {
    for (c in s) append(if (c.code in 0x30A1..0x30F6) (c.code - 0x60).toChar() else c)
}

// kana -> wapuro romaji (what people actually type). Digraphs are looked up first so they win.
private val roma: Map<String, String> = run {
    val table = "きゃkya きゅkyu きょkyo しゃsha しゅshu しょsho ちゃcha ちゅchu ちょcho にゃnya にゅnyu にょnyo " +
        "ひゃhya ひゅhyu ひょhyo みゃmya みゅmyu みょmyo りゃrya りゅryu りょryo ぎゃgya ぎゅgyu ぎょgyo " +
        "じゃja じゅju じょjo ぢゃja ぢゅju ぢょjo びゃbya びゅbyu びょbyo ぴゃpya ぴゅpyu ぴょpyo " +
        "あa いi うu えe おo かka きki くku けke こko さsa しshi すsu せse そso たta ちchi つtsu てte とto " +
        "なna にni ぬnu ねne のno はha ひhi ふfu へhe ほho まma みmi むmu めme もmo やya ゆyu よyo " +
        "らra りri るru れre ろro わwa ゐwi ゑwe をwo んn がga ぎgi ぐgu げge ごgo ざza じji ずzu ぜze ぞzo " +
        "だda ぢji づzu でde どdo ばba びbi ぶbu べbe ぼbo ぱpa ぴpi ぷpu ぺpe ぽpo ぁa ぃi ぅu ぇe ぉo ゔvu"
    table.split(' ').filter { it.isNotEmpty() }.associate { token ->
        val kana = token.takeWhile { it !in 'a'..'z' }
        kana to token.drop(kana.length)
    }
}

fun romaji(kana: String): String {
    val k = toHira(kana).filter { it != '.' && it != '-' && it != 'ー' }
    val out = StringBuilder()
    var i = 0
    while (i < k.length) {
        if (k[i] == 'っ') {
            // small tsu doubles the next consonant ("tch" for ch)
            val rest = romaji(k.substring(i + 1))
            val double = rest.firstOrNull()?.let {
                if (it in "aeiou") "" else if (it == 'c') "t" else it.toString()
            } ?: ""
            return out.toString() + double + rest
        }
        val two = if (i + 1 < k.length) roma[k.substring(i, i + 2)] else null
        if (two != null) {
            out.append(two)
            i += 2
        } else {
            out.append(roma[k[i].toString()] ?: k[i].toString())
            i += 1
        }
    }
    return out.toString()
}

/** True if a and b differ by at most one insertion, deletion, substitution or adjacent swap. */
private fun within1(a: String, b: String): Boolean {
    if (abs(a.length - b.length) > 1) return false
    var i = 0
    var j = 0
    var used = false
    while (i < a.length && j < b.length) {
        if (a[i] == b[j]) { i++; j++; continue }
        if (used) return false
        used = true
        if (a.length == b.length && i + 1 < a.length && j + 1 < b.length && a[i] == b[j + 1] && a[i + 1] == b[j]) {
            i += 2; j += 2; continue
        }
        if (a.length > b.length) i++ else if (a.length < b.length) j++ else { i++; j++ }
    }
    return true
}

/** True if all of q's characters appear in s, in order. */
private fun subseq(q: String, s: String): Boolean {
    var want = 0
    for (c in s) if (want < q.length && c == q[want]) want++
    return want == q.length
}

private fun score(q: String, s: String): Int {
    if (s == q) return 100
    if (s.startsWith(q)) return 80
    if ((" $s").contains(" $q")) return 70
    if (s.contains(q)) return 55
    if (q.length >= 4 && s.split(' ').any { it.isNotEmpty() && within1(q, it) }) return 50
    if (q.length >= 3 && subseq(q, s)) return 30
    return 0
}

/** In-memory index over all kanji. Build once at launch. */
// ponytail: linear scan of ~10k entries per keystroke (tens of ms, run off the main thread); add a prefix index if a words pack makes it slow
class Searcher(list: List<Kanji>) {
    private class Entry(
        val kanji: Kanji,
        val kana: List<String>,   // hiragana, no "." or "-"
        val text: List<String>,   // lowercased meanings + romaji
    )

    private class Hit(val score: Int, val index: Int, val kanji: Kanji)

    private val entries: List<Entry> = list.map { e ->
        val kana = (e.on + e.kun).map { r -> toHira(r).filter { it != '.' && it != '-' } }
        Entry(e, kana, e.m.map { it.lowercase() } + kana.map(::romaji))
    }
    private val byKanji: Map<String, Kanji> = HashMap<String, Kanji>().also { map ->
        for (e in list) map.putIfAbsent(e.k, e)
    }

    fun kanji(character: String): Kanji? = byKanji[character]

    fun search(query: String, limit: Int = 60): List<Kanji> {
        val q = toHira(query.trim().lowercase())
        if (q.isEmpty()) return emptyList()

        // any known kanji in the input: list those, in order, once each (by code point, so non-BMP kanji stay whole)
        val seen = HashSet<String>()
        val listed = ArrayList<Kanji>()
        var i = 0
        while (i < q.length) {
            val cp = q.codePointAt(i)
            val s = String(Character.toChars(cp))
            i += s.length
            val e = byKanji[s]
            if (e != null && seen.add(s)) listed.add(e)
        }
        if (listed.isNotEmpty()) return listed

        val isKana = q.all { it.code in 0x3041..0x309F }
        val hits = ArrayList<Hit>()
        for ((index, e) in entries.withIndex()) {
            var best = 0
            for (field in if (isKana) e.kana else e.text) {
                best = maxOf(best, score(q, field))
                if (best == 100) break
            }
            if (best > 0) hits.add(Hit(best, index, e.kanji))
        }
        // higher score, then more frequent (unranked last), then data order (stable)
        fun rank(f: Int) = if (f == 0) 9999 else f
        return hits
            .sortedWith(compareByDescending<Hit> { it.score }.thenBy { rank(it.kanji.f) }.thenBy { it.index })
            .take(limit)
            .map { it.kanji }
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: TEST
Expected: PASS, all `DataTests`, `RomajiTests`, `SearchTests`. If a ranking assertion fails, compare the failing query against the Swift code line by line (the scores and the sort key are deliberately identical) before touching the test.

- [ ] **Step 6: Confirm the file is Android-free**

```bash
grep -nE "import (android|androidx)" android/app/src/main/java/com/abbabon/kanjioffline/Search.kt || echo "clean"
```

Expected: `clean`.

- [ ] **Step 7: Commit**

```bash
git add android/app/src
git commit -m "Android: port search (romaji, ranking, typo tolerance) with tests"
```

---

### Task 5: Format helpers

Port of `Tatsu/Format.swift` (without the Mac-only `keyRoute`, `KeyFocus`, `copyToPasteboard`) and `TatsuTests/FormatTests.swift`. Label strings are passed in so the logic stays JVM-testable.

**Files:**
- Create: `android/app/src/main/java/com/abbabon/kanjioffline/Format.kt`
- Create: `android/app/src/test/java/com/abbabon/kanjioffline/FormatTests.kt`

**Interfaces:**
- Consumes: `Kanji` (Task 3).
- Produces (all pure):
  - `data class ReadingParts(val prefix: String, val kana: String, val okurigana: String, val suffix: String)`
  - `fun readingParts(reading: String): ReadingParts`
  - `class Labels(val strokes: String, val grade: String, val joyo: String, val jinmei: String, val jlpt: String, val freq: String)`. `strokes`, `grade`, `jlpt`, `freq` are `String.format` templates with one integer argument (for example `"%d strokes"`; in `strings.xml` they are `%1$d`). `joyo` and `jinmei` are plain text.
  - `fun gradeLabel(g: Int, labels: Labels): String`
  - `fun metaLine(e: Kanji, labels: Labels): String`
  - `fun isTypable(characters: String): Boolean`
  - `fun stepSelection(current: String?, ids: List<String>, delta: Int): String?`
  - `fun mergeOrder(shown: List<String>, latest: List<String>): List<String>`: keeps the frozen Recent order while browsing: entries not yet shown go on top, entries no longer in `latest` drop out, the rest keep their place (iOS `syncRecents(resort: false)`).

- [ ] **Step 1: Write the failing tests**

`android/app/src/test/java/com/abbabon/kanjioffline/FormatTests.kt`:

```kotlin
package com.abbabon.kanjioffline

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTests {
    private val labels = Labels(
        strokes = "%d strokes",
        grade = "grade %d",
        joyo = "jōyō (secondary)",
        jinmei = "jinmeiyō",
        jlpt = "JLPT %d",
        freq = "#%d freq",
    )

    @Test
    fun readingWithOkurigana() {
        assertEquals(ReadingParts("", "た", "べる", ""), readingParts("た.べる"))
    }

    @Test
    fun readingWithAffixDashes() {
        assertEquals(ReadingParts("-", "び", "", ""), readingParts("-び"))
        assertEquals(ReadingParts("", "お", "", "-"), readingParts("お-"))
        assertEquals(ReadingParts("", "スイ", "", ""), readingParts("スイ"))
    }

    @Test
    fun gradeLabels() {
        assertEquals("grade 1", gradeLabel(1, labels))
        assertEquals("jōyō (secondary)", gradeLabel(8, labels))
        assertEquals("jinmeiyō", gradeLabel(9, labels))
    }

    // type-anywhere forwards printable keys (incl. space, punctuation, kana) but not control keys
    @Test
    fun typableKeys() {
        assertTrue(isTypable("a"))
        assertTrue(isTypable(" "))
        assertTrue(isTypable("-"))
        assertTrue(isTypable("み"))
        assertFalse(isTypable(""))
        assertFalse(isTypable("\r"))
        assertFalse(isTypable("\u007F"))
        assertFalse(isTypable("\u0000"))     // what a non-character key reports
        assertFalse(isTypable("\uF700"))     // private-use key codes (an escape, never a literal)
    }

    @Test
    fun metaLineSkipsMissingValues() {
        val water = Kanji("水", listOf("water"), listOf("スイ"), listOf("みず"), emptyList(), 4, 1, 4, 223)
        assertEquals("4 strokes · grade 1 · JLPT 4 · #223 freq", metaLine(water, labels))
        val rare = Kanji("鬱", listOf("gloom"), emptyList(), emptyList(), emptyList(), 29, 0, 0, 0)
        assertEquals("29 strokes", metaLine(rare, labels))
    }

    // Review Focus 3: ASCII digits whatever the device language
    @Test
    fun metaLineUsesAsciiDigitsInEveryLocale() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            val water = Kanji("水", listOf("water"), listOf("スイ"), listOf("みず"), emptyList(), 4, 1, 4, 223)
            assertEquals("4 strokes · grade 1 · JLPT 4 · #223 freq", metaLine(water, labels))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun steppingSelection() {
        val ids = listOf("a", "b", "c")
        assertEquals("a", stepSelection(null, ids, 1))
        assertEquals("a", stepSelection(null, ids, -1))
        assertEquals("b", stepSelection("a", ids, 1))
        assertEquals("c", stepSelection("c", ids, 1))   // clamps
        assertEquals("a", stepSelection("a", ids, -1))
        assertEquals("a", stepSelection("gone", ids, 1))
        assertNull(stepSelection("a", emptyList(), 1))
    }

    // frozen Recent order: new on top, vanished dropped, the rest keep their place
    @Test
    fun mergeOrderKeepsShownOrder() {
        assertEquals(listOf("c", "a"), mergeOrder(shown = listOf("a", "b"), latest = listOf("c", "a")))
        assertEquals(listOf("a", "b"), mergeOrder(shown = listOf("a", "b"), latest = listOf("b", "a")))
        assertEquals(listOf("x"), mergeOrder(shown = emptyList(), latest = listOf("x")))
        assertEquals(emptyList<String>(), mergeOrder(shown = listOf("a"), latest = emptyList()))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: TEST
Expected: FAIL to compile, `Unresolved reference 'readingParts'` etc.

- [ ] **Step 3: Implement**

`android/app/src/main/java/com/abbabon/kanjioffline/Format.kt`:

```kotlin
package com.abbabon.kanjioffline

import java.util.Locale

/**
 * "た.べる" -> kana た (shown over the kanji), okurigana べる after it.
 * "-び" / "お-" keep their dash outside, marking a suffix / prefix reading.
 */
data class ReadingParts(val prefix: String, val kana: String, val okurigana: String, val suffix: String)

fun readingParts(reading: String): ReadingParts {
    val pieces = reading.split('.', limit = 2)
    var stem = pieces[0]
    val okurigana = if (pieces.size > 1) pieces[1] else ""
    val prefix = if (stem.startsWith("-")) "-" else ""
    if (prefix.isNotEmpty()) stem = stem.removePrefix("-")
    val suffix = if (stem.endsWith("-")) "-" else ""
    if (suffix.isNotEmpty()) stem = stem.removeSuffix("-")
    return ReadingParts(prefix, stem, okurigana, suffix)
}

/** Label text from strings.xml, passed in so this file stays free of Android. */
class Labels(
    val strokes: String,  // "%d strokes"
    val grade: String,    // "grade %d"
    val joyo: String,     // "jōyō (secondary)"
    val jinmei: String,   // "jinmeiyō"
    val jlpt: String,     // "JLPT %d"
    val freq: String,     // "#%d freq"
)

// Locale.ROOT: always ASCII digits, whatever language the device is set to
private fun fmt(template: String, n: Int) = String.format(Locale.ROOT, template, n)

fun gradeLabel(g: Int, labels: Labels): String =
    if (g <= 6) fmt(labels.grade, g) else if (g == 8) labels.joyo else labels.jinmei

fun metaLine(e: Kanji, labels: Labels): String {
    val parts = mutableListOf(fmt(labels.strokes, e.s))
    if (e.g > 0) parts.add(gradeLabel(e.g, labels))
    if (e.j > 0) parts.add(fmt(labels.jlpt, e.j))
    if (e.f > 0) parts.add(fmt(labels.freq, e.f))
    return parts.joinToString(" · ")
}

/** A key press worth forwarding to the search field: printable text, not control keys or private-use key codes. */
fun isTypable(characters: String): Boolean =
    characters.isNotEmpty() && characters.all { !it.isISOControl() && it.code !in 0xF700..0xF8FF }

/** The id one step down (+1) or up (-1) from `current`, clamped to the ends. With nothing (or something no longer listed) selected, the first id. */
fun stepSelection(current: String?, ids: List<String>, delta: Int): String? {
    val i = current?.let { ids.indexOf(it) }?.takeIf { it >= 0 } ?: return ids.firstOrNull()
    return ids[(i + delta).coerceIn(0, ids.size - 1)]
}

/** New recents go on top, vanished ones drop out, everything else keeps its place. */
fun mergeOrder(shown: List<String>, latest: List<String>): List<String> =
    latest.filter { it !in shown } + shown.filter { it in latest }
```

- [ ] **Step 4: Run to verify it passes**

Run: TEST
Expected: PASS, including every earlier suite.

- [ ] **Step 5: Confirm the file is Android-free, then commit**

```bash
grep -nE "import (android|androidx)" android/app/src/main/java/com/abbabon/kanjioffline/Format.kt || echo "clean"
git add android/app/src
git commit -m "Android: port format helpers with tests"
```

---

### Task 6: Recents (DataStore)

Port of `Tatsu/Recent.swift` and `TatsuTests/RecentTests.swift`. The list is one string, kanji separated by `\n`, newest first, capped at 100.

**Files:**
- Create: `android/app/src/main/java/com/abbabon/kanjioffline/Recents.kt`
- Create: `android/app/src/test/java/com/abbabon/kanjioffline/RecentsTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `class Recents(store: DataStore<Preferences>)` with `val flow: Flow<List<String>>` (newest first), `suspend fun touch(kanji: String, keep: Int = 100)`, `suspend fun remove(kanji: String)`, `suspend fun clear()`
  - `val Context.recentsStore: DataStore<Preferences>` (file name `recents`)

- [ ] **Step 1: Write the failing tests**

`android/app/src/test/java/com/abbabon/kanjioffline/RecentsTest.kt`:

```kotlin
package com.abbabon.kanjioffline

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecentsTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var recents: Recents

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        recents = Recents(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "recents.preferences_pb") })
    }

    @After
    fun tearDown() = scope.cancel()

    private fun all(): List<String> = runBlocking { recents.flow.first() }

    @Test
    fun startsEmpty() {
        assertTrue(all().isEmpty())
    }

    @Test
    fun newestFirst() = runBlocking {
        recents.touch("水")
        recents.touch("日")
        assertEquals(listOf("日", "水"), all())
    }

    @Test
    fun touchingAgainMovesToTopWithoutDuplicate() = runBlocking {
        recents.touch("水")
        recents.touch("日")
        recents.touch("水")
        assertEquals(listOf("水", "日"), all())
    }

    @Test
    fun keepsOnlyNewestHundred() = runBlocking {
        // 101 distinct CJK characters starting at 一 (U+4E00)
        val chars = (0 until 101).map { (0x4E00 + it).toChar().toString() }
        for (c in chars) recents.touch(c)
        val kept = all()
        assertEquals(100, kept.size)
        assertEquals(chars[100], kept.first())
        assertFalse(chars[0] in kept)
    }

    @Test
    fun clearRemovesEverything() = runBlocking {
        recents.touch("水")
        recents.touch("日")
        recents.clear()
        assertTrue(all().isEmpty())
    }

    @Test
    fun removeDropsOnlyThatKanji() = runBlocking {
        recents.touch("水")
        recents.touch("日")
        recents.remove("日")
        assertEquals(listOf("水"), all())
    }

    @Test
    fun removingMissingKanjiIsNoOp() = runBlocking {
        recents.touch("水")
        recents.remove("火")
        assertEquals(listOf("水"), all())
    }

    // Review Focus 4: a kanji outside the BMP survives the one-string encoding intact
    @Test
    fun nonBmpKanjiRoundTrips() = runBlocking {
        recents.touch("水")
        recents.touch("𠮟")
        assertEquals(listOf("𠮟", "水"), all())
        recents.remove("𠮟")
        assertEquals(listOf("水"), all())
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: TEST
Expected: FAIL to compile, `Unresolved reference 'Recents'`.

- [ ] **Step 3: Implement**

`android/app/src/main/java/com/abbabon/kanjioffline/Recents.kt`:

```kotlin
package com.abbabon.kanjioffline

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.recentsStore: DataStore<Preferences> by preferencesDataStore(name = "recents")

/**
 * Kanji the user opened or copied. Local to the device. Stored as one string, newest first,
 * separated by newlines (a kanji is never a newline).
 */
class Recents(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("recent")

    private fun decode(s: String?): List<String> = if (s.isNullOrEmpty()) emptyList() else s.split('\n')

    val flow: Flow<List<String>> = store.data.map { decode(it[key]) }

    /** Insert or bump `kanji` to the top, then drop everything past the newest `keep`. */
    suspend fun touch(kanji: String, keep: Int = 100) {
        store.edit { prefs ->
            val next = (listOf(kanji) + decode(prefs[key]).filter { it != kanji }).take(keep)
            prefs[key] = next.joinToString("\n")
        }
    }

    suspend fun remove(kanji: String) {
        store.edit { prefs -> prefs[key] = decode(prefs[key]).filter { it != kanji }.joinToString("\n") }
    }

    suspend fun clear() {
        store.edit { it.remove(key) }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: TEST
Expected: PASS, all suites. If DataStore complains the file must end in `.preferences_pb`, keep the name as written; if it complains about the scope being cancelled, check `tearDown` runs only after the test body finished (`runBlocking` blocks, so it does).

- [ ] **Step 5: Commit**

```bash
git add android/app/src
git commit -m "Android: recents on DataStore with tests"
```

---

### Task 7: Strings, theme, ViewModel

No UI yet. Deliverable: everything the panes need compiles, strings are in `strings.xml`.

**Files:**
- Modify: `android/app/src/main/res/values/strings.xml`
- Create: `android/app/src/main/java/com/abbabon/kanjioffline/Theme.kt`
- Create: `android/app/src/main/java/com/abbabon/kanjioffline/TatsuViewModel.kt`

**Interfaces:**
- Consumes: `Kanji`, `loadKanji` (Task 3); `Searcher` (Task 4); `stepSelection`, `mergeOrder` (Task 5); `Recents`, `recentsStore` (Task 6).
- Produces:
  - Strings (names used by Tasks 8-12): `app_name`, `search_hint`, `clear_search`, `about`, `recent`, `clear`, `hint_empty`, `no_matches`, `handwriting_hint`, `copy`, `copied`, `remove_history`, `back`, `detail_empty_title`, `detail_empty_body`, `on_label`, `kun_label`, `names_label`, `meta_strokes`, `meta_grade`, `meta_joyo`, `meta_jinmei`, `meta_jlpt`, `meta_freq`, `about_tagline`, `about_blurb`, `about_data_title`, `about_data_credit`, `link_kanjidic`, `link_cc`, `about_app_title`, `about_mit`, `about_font_credit`, `link_source`.
  - `@Composable fun TatsuTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`
  - `fun TextStyle.ja(): TextStyle` (sets `LocaleList("ja")`)
  - `data class Found(val query: String, val kanji: List<Kanji>)`
  - `class TatsuViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app)` with:
    - state: `query: StateFlow<String>`, `ready: StateFlow<Boolean>`, `found: StateFlow<Found>`, `selected: StateFlow<String?>`, `recentOrder: StateFlow<List<String>>`, `handOffs: SharedFlow<Unit>` (one event per text-selection hand-off, no replay)
    - `fun applyHandOff(text: String)` (sets the query and emits one `handOffs` event)
    - `fun kanji(k: String): Kanji?`
    - `fun setQuery(q: String)`, `fun pick(k: String)`, `fun touch(k: String)`, `fun enter(): String?`, `fun step(delta: Int)`, `fun remove(k: String)`, `fun clearRecents()`, `fun visibleIds(): List<String>`

- [ ] **Step 1: Strings**

Replace `android/app/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Tatsu</string>

    <!-- list pane -->
    <string name="search_hint">English, kana, romaji or kanji</string>
    <string name="clear_search">Clear search</string>
    <string name="about">About</string>
    <string name="recent">Recent</string>
    <string name="clear">Clear</string>
    <string name="hint_empty">Type English, hiragana, katakana, romaji, or paste kanji.</string>
    <string name="no_matches">No matches.</string>
    <!-- shown under "No matches."; path verified 2026-10-06 against Gboard Help, support.google.com/gboard/answer/9108773 -->
    <string name="handwriting_hint">To write kanji by hand, add Gboard\'s Japanese Handwriting layout: Gboard settings → Languages → Japanese → Handwriting.</string>
    <string name="copy">Copy</string>
    <string name="copied">Copied</string>
    <string name="remove_history">Remove from history</string>

    <!-- detail pane -->
    <string name="back">Back</string>
    <string name="detail_empty_title">Search for a kanji</string>
    <string name="detail_empty_body">Type English, kana, romaji, or kanji</string>
    <string name="on_label">ON</string>
    <string name="kun_label">KUN</string>
    <string name="names_label">NAMES</string>
    <string name="meta_strokes">%1$d strokes</string>
    <string name="meta_grade">grade %1$d</string>
    <string name="meta_joyo">jōyō (secondary)</string>
    <string name="meta_jinmei">jinmeiyō</string>
    <string name="meta_jlpt">JLPT %1$d</string>
    <string name="meta_freq">#%1$d freq</string>

    <!-- about sheet -->
    <string name="about_tagline">Tatsu · 断 · to cut off, to sever</string>
    <string name="about_blurb">Offline kanji lookup. No account, no network.</string>
    <string name="about_data_title">Kanji data</string>
    <string name="about_data_credit">KANJIDIC2 by the Electronic Dictionary Research and Development Group, used under CC BY-SA 4.0.</string>
    <string name="link_kanjidic">KANJIDIC Project</string>
    <string name="link_cc">CC BY-SA 4.0 license</string>
    <string name="about_app_title">App</string>
    <string name="about_mit">App code is MIT licensed.</string>
    <string name="about_font_credit">The 断 in the app icon is drawn from Noto Serif JP, copyright The Noto Project Authors, licensed under the SIL Open Font License 1.1.</string>
    <string name="link_source">Source code</string>
</resources>
```

- [ ] **Step 2: Theme**

`android/app/src/main/java/com/abbabon/kanjioffline/Theme.kt`:

```kotlin
package com.abbabon.kanjioffline

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList

// Below Android 12 there is no dynamic colour: a static scheme seeded from the logo's red (#C0392B).
private val StaticLight = lightColorScheme(
    primary = Color(0xFFC0392B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD4),
    onPrimaryContainer = Color(0xFF410002),
    secondaryContainer = Color(0xFFFFDAD4),
    onSecondaryContainer = Color(0xFF410002),
)
private val StaticDark = darkColorScheme(
    primary = Color(0xFFFFB4A8),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondaryContainer = Color(0xFF5C3F3B),
    onSecondaryContainer = Color(0xFFFFDAD4),
)

@Composable
fun TatsuTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> StaticDark
        else -> StaticLight
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private val japanese = LocaleList("ja")

/** Han characters use Japanese glyphs, not Chinese ones. Apply to every text that shows kanji or kana. */
fun TextStyle.ja(): TextStyle = copy(localeList = japanese)
```

- [ ] **Step 3: ViewModel**

`android/app/src/main/java/com/abbabon/kanjioffline/TatsuViewModel.kt`:

```kotlin
package com.abbabon.kanjioffline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Results and the query they belong to. */
data class Found(val query: String, val kanji: List<Kanji>)

class TatsuViewModel(app: Application, private val handle: SavedStateHandle) : AndroidViewModel(app) {
    private val recents = Recents(app.recentsStore)
    private val searcher = MutableStateFlow<Searcher?>(null)

    /** Survives process death through SavedStateHandle. */
    val query: StateFlow<String> = handle.getStateFlow("query", "")
    val selected: StateFlow<String?> = handle.getStateFlow<String?>("selected", null)

    val ready: StateFlow<Boolean> =
        searcher.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Every new query cancels the search still running for the previous one. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val found: StateFlow<Found> = combine(query, searcher.filterNotNull()) { q, s -> q to s }   // no results (and no "No matches.") until the searcher is loaded
        .mapLatest { (q, s) -> Found(q, s.search(q)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Found("", emptyList()))

    // Recent order shown: frozen while browsing so a tap doesn't re-sort under the finger,
    // refreshed whenever the recents are shown again (query cleared).
    private var latestRecents: List<String> = emptyList()
    private var firstRecents = true
    private val _recentOrder = MutableStateFlow<List<String>>(emptyList())
    val recentOrder: StateFlow<List<String>> = _recentOrder.asStateFlow()

    init {
        // A failure to decode kanji.json is a build bug (the asset ships in the APK): crash, like fatalError on iOS.
        viewModelScope.launch(Dispatchers.Default) {
            searcher.value = Searcher(getApplication<Application>().loadKanji())
        }
        viewModelScope.launch {
            recents.flow.collect { latest ->
                latestRecents = latest
                _recentOrder.value = if (firstRecents) latest else mergeOrder(_recentOrder.value, latest)
                firstRecents = false
            }
        }
    }

    fun kanji(k: String): Kanji? = searcher.value?.kanji(k)

    /** One event per text-selection hand-off (Task 11). No replay, so a rotation or a new collector never sees an old one. */
    private val _handOffs = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val handOffs: SharedFlow<Unit> = _handOffs

    fun applyHandOff(text: String) {
        setQuery(text)
        _handOffs.tryEmit(Unit)
    }

    fun setQuery(q: String) {
        val becameBlank = q.isBlank() && query.value.isNotBlank()
        handle["query"] = q
        if (becameBlank) _recentOrder.value = latestRecents
    }

    /** Ids that Up/Down and Enter act on: the recents when the query is blank, else the results. */
    fun visibleIds(): List<String> =
        if (query.value.isBlank()) _recentOrder.value.filter { kanji(it) != null } else found.value.kanji.map { it.k }

    /** An explicit pick (tap or Enter): select and record. */
    fun pick(k: String) {
        handle["selected"] = k
        touch(k)
    }

    /** Record a lookup (also used by Copy). */
    fun touch(k: String) {
        viewModelScope.launch { recents.touch(k) }
    }

    /** Enter: record the current selection, or pick (and record) the first visible result. Returns what was picked. */
    fun enter(): String? {
        val k = selected.value ?: visibleIds().firstOrNull() ?: return null
        pick(k)
        return k
    }

    /** Arrow keys: move the selection only, never record. */
    fun step(delta: Int) {
        handle["selected"] = stepSelection(selected.value, visibleIds(), delta)
    }

    fun remove(k: String) {
        viewModelScope.launch { recents.remove(k) }
        if (selected.value == k) handle["selected"] = null
    }

    fun clearRecents() {
        viewModelScope.launch { recents.clear() }
        handle["selected"] = null
    }
}
```

- [ ] **Step 4: Build and run the tests**

Run: BUILD, then TEST
Expected: both succeed. Fix compile errors against the APIs (for example `getStateFlow<String?>` nullability) without changing the behaviour above.

- [ ] **Step 5: Commit**

```bash
git add android/app/src
git commit -m "Android: strings, theme and view model"
```

---

### Task 8: Detail pane and About sheet

**Files:**
- Create: `android/app/src/main/java/com/abbabon/kanjioffline/DetailPane.kt`
- Create: `android/app/src/main/java/com/abbabon/kanjioffline/AboutSheet.kt`

**Interfaces:**
- Consumes: `Kanji`, `Labels`, `metaLine`, `readingParts`, `romaji`, `TextStyle.ja()`, strings from Task 7.
- Produces:
  - `@Composable fun DetailPane(kanji: Kanji, labels: Labels, showBack: Boolean, onBack: () -> Unit, onCopy: () -> Unit)`: top app bar with back (only when `showBack`) and a Copy text action; large kanji (96sp, selectable, test tag `detail-kanji`), meanings, ON and KUN reading tiles, NAMES, meta line.
  - `@Composable fun EmptyDetail()`: "Search for a kanji" placeholder for the second pane.
  - `@Composable fun AboutSheet(onDismiss: () -> Unit)`: `ModalBottomSheet`.

This task has no visible result until Task 9 wires it in; verification here is compile only.

- [ ] **Step 1: Write `DetailPane.kt`**

```kotlin
package com.abbabon.kanjioffline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailPane(kanji: Kanji, labels: Labels, showBack: Boolean, onBack: () -> Unit, onCopy: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = { TextButton(onClick = onCopy) { Text(stringResource(R.string.copy)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SelectionContainer {
                Text(
                    kanji.k,
                    fontSize = 96.sp,
                    style = LocalTextStyle.current.ja(),
                    modifier = Modifier.testTag("detail-kanji"),
                )
            }
            Text(kanji.m.joinToString(", "), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            ReadingSection(stringResource(R.string.on_label), kanji.k, kanji.on)
            ReadingSection(stringResource(R.string.kun_label), kanji.k, kanji.kun)
            if (kanji.n.isNotEmpty()) {
                Section(stringResource(R.string.names_label)) {
                    Text(
                        kanji.n.joinToString("、"),
                        style = MaterialTheme.typography.bodyLarge.ja(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                metaLine(kanji, labels),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

/** Furigana stand-in: each reading is kana / kanji+okurigana / romaji stacked in a tonal tile. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingSection(label: String, kanji: String, readings: List<String>) {
    if (readings.isEmpty()) return
    Section(label) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in readings) {
                val p = readingParts(r)
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    // one TalkBack item per tile
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics(mergeDescendants = true) {}) {
                        Text(p.kana, style = MaterialTheme.typography.labelMedium.ja(), color = MaterialTheme.colorScheme.primary)
                        Text(p.prefix + kanji + p.okurigana + p.suffix, style = MaterialTheme.typography.titleMedium.ja())
                        Text(romaji(r), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyDetail() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.detail_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.detail_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
```

- [ ] **Step 2: Write `AboutSheet.kt`**

```kotlin
package com.abbabon.kanjioffline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.titleMedium.ja())
            Text(stringResource(R.string.about_blurb), color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(stringResource(R.string.about_data_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.about_data_credit))
            LinkText(stringResource(R.string.link_kanjidic), "https://www.edrdg.org/wiki/index.php/KANJIDIC_Project")
            LinkText(stringResource(R.string.link_cc), "https://creativecommons.org/licenses/by-sa/4.0/")

            Text(stringResource(R.string.about_app_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.about_mit))
            Text(stringResource(R.string.about_font_credit), style = LocalTextStyle.current.ja(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinkText(stringResource(R.string.link_source), "https://github.com/Abbabon/tatsu-kanji")
        }
    }
}

@Composable
private fun LinkText(label: String, url: String) {
    val style = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    Text(buildAnnotatedString { withLink(LinkAnnotation.Url(url, style)) { append(label) } })
}
```

- [ ] **Step 3: Build**

Run: BUILD
Expected: `BUILD SUCCESSFUL`. (The panes are unused until Task 9; unused-code warnings are fine.)

- [ ] **Step 4: Commit**

```bash
git add android/app/src
git commit -m "Android: detail pane and about sheet"
```

---

### Task 9: List pane, adaptive scaffold, MainActivity

The first runnable app. After this task the phone emulator shows search, results, recents, detail, About.

**Files:**
- Create: `android/app/src/main/java/com/abbabon/kanjioffline/ListPane.kt`
- Create: `android/app/src/main/java/com/abbabon/kanjioffline/TatsuScreen.kt`
- Modify: `android/app/src/main/java/com/abbabon/kanjioffline/MainActivity.kt`

**Interfaces:**
- Consumes: everything from Tasks 3-8.
- Produces:
  - `@Composable fun ListPane(query, ready, found, recents, selected, highlight, searchFocus, onSearchFocus, onQuery, onPick, onEnter, onCopy, onRemove, onClearRecents, onAbout)` (exact signature in the code below). Test tags: `search` (field), `row:<kanji>` (each row), `recent-header` (the "Recent" header row).
  - `@Composable fun TatsuScreen(vm: TatsuViewModel)`
  - `MainActivity` with `private val vm: TatsuViewModel by viewModels()`

- [ ] **Step 1: Write `ListPane.kt`**

```kotlin
package com.abbabon.kanjioffline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListPane(
    query: String,
    ready: Boolean,
    found: Found,
    recents: List<Kanji>,
    selected: String?,
    highlight: Boolean,                 // highlight the selected row (two-pane mode)
    searchFocus: FocusRequester,
    onSearchFocus: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
    onPick: (String) -> Unit,
    onEnter: () -> Unit,
    onCopy: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearRecents: () -> Unit,
    onAbout: () -> Unit,
) {
    val blank = query.isBlank()
    val rows = if (blank) recents else found.kanji
    val headerOffset = if (blank && recents.isNotEmpty()) 1 else 0
    val listState = rememberLazyListState()

    // keep the keyboard-selected row on screen
    LaunchedEffect(selected, rows) {
        val i = rows.indexOfFirst { it.k == selected }
        if (i >= 0) {
            val index = i + headerOffset
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.animateScrollToItem(index)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onAbout) {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.about))
                    }
                },
            )
        },
    ) { padding ->
        // imePadding: edge-to-edge disables adjustResize's effect, so lift the list above the keyboard explicitly
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            SearchField(query, onQuery, onEnter, searchFocus, onSearchFocus)
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (blank) {
                    if (recents.isEmpty()) {
                        item(key = "hint") {
                            Text(
                                stringResource(R.string.hint_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        item(key = "header") { RecentHeader(onClearRecents) }
                        items(recents, key = { it.k }) { e ->
                            KanjiRow(e, highlight && e.k == selected, inRecents = true, onPick, onCopy, onRemove)
                        }
                    }
                } else if (found.kanji.isNotEmpty()) {
                    items(found.kanji, key = { it.k }) { e ->
                        KanjiRow(e, highlight && e.k == selected, inRecents = false, onPick, onCopy, onRemove)
                    }
                } else if (ready && found.query == query) {
                    item(key = "none") { NoMatches() }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    onEnter: () -> Unit,
    focus: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
) {
    // A plain TextField styled as a search bar: SearchBarDefaults.InputField has no keyboardOptions,
    // and the spec needs autocorrect and capitalisation off with a Search IME action.
    TextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { onEnter() }),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        textStyle = LocalTextStyle.current.ja(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .focusRequester(focus)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .testTag("search"),
    )
}

@Composable
private fun RecentHeader(onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).testTag("recent-header"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.recent), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
    }
}

@Composable
private fun NoMatches() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.no_matches))
        Text(
            stringResource(R.string.handwriting_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KanjiRow(
    e: Kanji,
    highlighted: Boolean,
    inRecents: Boolean,
    onPick: (String) -> Unit,
    onCopy: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
        ListItem(
            modifier = Modifier
                .testTag("row:${e.k}")
                .combinedClickable(
                    onClick = { onPick(e.k) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
                // one TalkBack item per row
                .semantics(mergeDescendants = true) {},
            leadingContent = { Text(e.k, fontSize = 40.sp, style = LocalTextStyle.current.ja()) },
            headlineContent = { Text(e.m.joinToString(", "), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(
                    (e.on + e.kun).joinToString("、"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.ja(),
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            ),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.copy)) }, onClick = { menu = false; onCopy(e.k) })
            if (inRecents) {
                DropdownMenuItem(text = { Text(stringResource(R.string.remove_history)) }, onClick = { menu = false; onRemove(e.k) })
            }
        }
    }
}
```

- [ ] **Step 2: Write `TatsuScreen.kt`**

```kotlin
package com.abbabon.kanjioffline

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun TatsuScreen(vm: TatsuViewModel) {
    val query by vm.query.collectAsState()
    val found by vm.found.collectAsState()
    val ready by vm.ready.collectAsState()
    val selected by vm.selected.collectAsState()
    val recentOrder by vm.recentOrder.collectAsState()
    // `ready` is read here (outer scope) and keys both remembers: the searcher loads after the DataStore emits,
    // and `vm.kanji` is not observable, so without it Recent and a restored detail would stay empty until the next change.
    // Recents whose kanji vanished after a data update are skipped.
    val recents = remember(recentOrder, ready) { recentOrder.mapNotNull { vm.kanji(it) } }
    val selectedKanji = remember(selected, ready) { selected?.let { vm.kanji(it) } }

    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val searchFocus = remember { FocusRequester() }
    var showAbout by remember { mutableStateOf(false) }
    val twoPane = navigator.scaffoldDirective.maxHorizontalPartitions > 1

    val labels = Labels(
        strokes = stringResource(R.string.meta_strokes),
        grade = stringResource(R.string.meta_grade),
        joyo = stringResource(R.string.meta_joyo),
        jinmei = stringResource(R.string.meta_jinmei),
        jlpt = stringResource(R.string.meta_jlpt),
        freq = stringResource(R.string.meta_freq),
    )
    val copiedText = stringResource(R.string.copied)

    fun open(k: String) {
        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, k) }
    }

    fun pick(k: String) {
        vm.pick(k)
        open(k)
    }

    fun enter() {
        vm.enter()?.let { open(it) }
    }

    fun copy(k: String) {
        vm.touch(k)
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("kanji", k)))
            // Android 13+ shows its own clipboard preview; older versions get a snackbar
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbar.showSnackbar(copiedText)
        }
    }

    // launch: focus the search field (it is not composed if a restored detail hides the list)
    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            modifier = Modifier.padding(padding),
            listPane = {
                AnimatedPane {
                    ListPane(
                        query = query,
                        ready = ready,
                        found = found,
                        recents = recents,
                        selected = selected,
                        highlight = twoPane,
                        searchFocus = searchFocus,
                        onSearchFocus = {},
                        onQuery = vm::setQuery,
                        onPick = ::pick,
                        onEnter = ::enter,
                        onCopy = ::copy,
                        onRemove = vm::remove,
                        onClearRecents = {
                            vm.clearRecents()
                            runCatching { searchFocus.requestFocus() }
                        },
                        onAbout = { showAbout = true },
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    val e = selectedKanji
                    if (e != null) {
                        DetailPane(
                            kanji = e,
                            labels = labels,
                            showBack = !twoPane,
                            onBack = { scope.launch { navigator.navigateBack() } },
                            onCopy = { copy(e.k) },
                        )
                    } else {
                        EmptyDetail()
                    }
                }
            },
        )
    }

    if (showAbout) AboutSheet(onDismiss = { showAbout = false })
}
```

`onSearchFocus = {}` is a placeholder callback until Task 10 uses it; that is deliberate, not a TODO: the parameter exists because Task 10 needs it and the signature must not change.

- [ ] **Step 3: Replace `MainActivity.kt`**

```kotlin
package com.abbabon.kanjioffline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: TatsuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { TatsuTheme { TatsuScreen(vm) } }
    }
}
```

- [ ] **Step 4: Build, install, drive it with adb on the phone emulator**

```bash
# ENV; phone emulator booted (Task 1 step 5)
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear com.abbabon.kanjioffline
adb shell am start -n com.abbabon.kanjioffline/.MainActivity
sleep 3; adb exec-out screencap -p > "$SCRATCH/1-empty.png"
adb shell input text water
sleep 2; adb exec-out screencap -p > "$SCRATCH/2-water.png"
```

Look at the screenshots. Expected: (1) title "Tatsu" with the info icon, a rounded search field with the keyboard up, and the hint "Type English, hiragana, ..." (no recents yet). (2) rows with 水 first, the kanji large on the left, meanings on up to two lines and readings below. Then tap the first row. Find its coordinates from the screenshot (the phone is 1080x2400, so a row near the top of the list is around y=500) and run `adb shell input tap 540 500`; screenshot again. Expected: the detail page with a big 水, the Copy text button top right, a back arrow top left, meanings, ON and KUN tiles with kana over kanji over romaji, meta line "4 strokes · grade 1 · JLPT 4 · #... freq". Press back with `adb shell input keyevent KEYCODE_BACK`: list returns. Clear the field with the x: the "Recent" header with Clear and 水 below it appears. Tap the info icon: the About sheet opens. If text looks Chinese-style instead of Japanese, the `ja()` locale is not applied somewhere; fix it. With the keyboard up and the list scrolled to the end, the last row must be fully visible above the keyboard (`imePadding`); if the keyboard covers rows, fix the insets before moving on.

- [ ] **Step 5: Commit**

```bash
git add android/app/src
git commit -m "Android: list pane, adaptive list-detail screen, search and recents"
```

---

### Task 10: Hardware keyboard

Up/Down step the selection, Enter picks, typing anywhere goes into the search field, Ctrl+F focuses it. Arrow keys never record a recent.

**Files:**
- Modify: `android/app/src/main/java/com/abbabon/kanjioffline/TatsuScreen.kt`

**Interfaces:**
- Consumes: `vm.step`, `vm.enter`, `vm.setQuery`, `isTypable` (Task 5), the `onSearchFocus` callback already on `ListPane` (Task 9).
- Produces: key handling on the root `Scaffold` via `onPreviewKeyEvent`.

- [ ] **Step 1: Track whether the search field has focus**

In `TatsuScreen`, add next to `showAbout`:

```kotlin
    var searchFocused by remember { mutableStateOf(false) }
    var rootFocused by remember { mutableStateOf(false) }   // any node under the scaffold has focus
```

and change the `ListPane` argument `onSearchFocus = {}` to `onSearchFocus = { searchFocused = it }`.

- [ ] **Step 2: Add the key handler**

Add a helper at the bottom of the file:

```kotlin
/** Removes the last code point (so a non-BMP kanji is not left as half a surrogate pair). */
private fun String.dropLastCodePoint(): String =
    if (isEmpty()) this else substring(0, offsetByCodePoints(length, -1))
```

Add imports: `androidx.compose.ui.focus.onFocusChanged`, `androidx.compose.ui.input.key.Key`, `KeyEventType`, `isCtrlPressed`, `isMetaPressed`, `isAltPressed`, `key`, `onPreviewKeyEvent`, `type`, `utf16CodePoint`.

Change the root `Scaffold` modifier from `Modifier.fillMaxSize()` to:

```kotlin
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { rootFocused = it.hasFocus }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // a focused button (Copy, Clear, back, a row) must still get its own Enter and Space
                val otherFocused = rootFocused && !searchFocused
                when {
                    e.isCtrlPressed && e.key == Key.F -> {
                        runCatching { searchFocus.requestFocus() }
                        true
                    }
                    e.isCtrlPressed || e.isMetaPressed || e.isAltPressed -> false
                    e.key == Key.DirectionDown -> { vm.step(1); true }
                    e.key == Key.DirectionUp -> { vm.step(-1); true }
                    (e.key == Key.Enter || e.key == Key.NumPadEnter) && !otherFocused -> { enter(); true }
                    otherFocused && (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.Spacebar) -> false
                    e.key == Key.Backspace ->
                        if (!searchFocused && query.isNotEmpty()) {
                            vm.setQuery(query.dropLastCodePoint())
                            runCatching { searchFocus.requestFocus() }
                            true
                        } else false
                    !searchFocused && e.utf16CodePoint > 0 -> {
                        // type anywhere: forward printable keys to the search field
                        val s = String(Character.toChars(e.utf16CodePoint))
                        if (isTypable(s)) {
                            vm.setQuery(query + s)
                            runCatching { searchFocus.requestFocus() }
                            true
                        } else false
                    }
                    else -> false
                }
            },
```

Enter is consumed here when the search field or nothing has focus (so the field's own IME action does not also fire); when a button or row has focus, Enter and Space pass through to it; the soft keyboard's Search button still calls `onEnter` through `keyboardActions`.

- [ ] **Step 3: Verify on the 10-inch tablet emulator (two panes)**

```bash
# ENV
adb emu kill; sleep 3
nohup emulator -avd tatsu_tablet10 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect > "$SCRATCH/emu.log" 2>&1 &
adb wait-for-device; until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 3; done
cd android && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear com.abbabon.kanjioffline
adb shell am start -n com.abbabon.kanjioffline/.MainActivity; sleep 3
adb shell input text water; sleep 2
adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1
adb exec-out screencap -p > "$SCRATCH/tab-down.png"
adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1
adb exec-out screencap -p > "$SCRATCH/tab-down2.png"
adb shell input keyevent KEYCODE_ENTER; sleep 1
adb shell input keycombination 113 34       # Ctrl+F
```

Expected on the tablet screenshots: two panes side by side (list left, detail right). After the first Down the first row is highlighted and its kanji shows in the right pane; after the second Down the second row. Arrow movement alone must not add to Recent: `adb shell pm clear` first, step with Down twice, clear the query: the hint text shows and there is no Recent header. After Enter, that kanji appears in Recent. If `input keycombination` is unavailable, skip Ctrl+F and leave it to the manual checklist.

**Verify type-anywhere explicitly** (this is the risky part): tap a blank area of the detail pane so the search field loses focus, then `adb shell input text a` and screenshot; the search field must now contain `a` and have focus. `onPreviewKeyEvent` on the `Scaffold` only fires when a node under it has focus; if the key does nothing when no node is focused, move the whole handler (same logic, same `searchFocused`/`rootFocused` state) to `MainActivity.dispatchKeyEvent` (override, forward to the screen's handler, fall back to `super`), and keep the verification. Also check a focused button keeps its own Enter: `adb shell input keyevent KEYCODE_TAB` until Copy (or Clear) is focused, press `KEYCODE_ENTER`, and it must activate (Copy copies, no navigation change).

- [ ] **Step 4: Run the unit tests and commit**

```bash
# ENV
cd android && ./gradlew test
git add android/app/src
git commit -m "Android: hardware keyboard (arrows, Enter, type anywhere, Ctrl+F)"
```

---

### Task 11: Text-selection hand-off and predictive back

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/com/abbabon/kanjioffline/MainActivity.kt`
- Modify: `android/app/src/main/java/com/abbabon/kanjioffline/TatsuScreen.kt`

**Interfaces:**
- Consumes: `vm.applyHandOff`, `vm.handOffs` (Task 7).
- Produces: `ACTION_PROCESS_TEXT` entry labelled "Tatsu"; predictive-back opt-in; the query set from a selection on first launch and on a new intent only (never again after a rotation).

- [ ] **Step 1: Manifest**

In `AndroidManifest.xml` add `android:enableOnBackInvokedCallback="true"` to `<application>`, and add a second intent filter to the activity (after the MAIN/LAUNCHER one):

```xml
            <intent-filter>
                <action android:name="android.intent.action.PROCESS_TEXT" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
```

The menu entry takes its label from the activity, which is `@string/app_name` ("Tatsu"). The app only reads the selection and never returns text, so it does not look at `EXTRA_PROCESS_TEXT_READONLY`.

- [ ] **Step 2: Read the selection**

Replace `MainActivity.kt`:

```kotlin
package com.abbabon.kanjioffline

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: TatsuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // only on a fresh start: after a rotation the intent is delivered again and would overwrite what the user typed since
        if (savedInstanceState == null) applyProcessText(intent)
        setContent { TatsuTheme { TatsuScreen(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyProcessText(intent)
    }

    private fun applyProcessText(intent: Intent?) {
        if (intent?.action != Intent.ACTION_PROCESS_TEXT) return
        intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.let(vm::applyHandOff)
    }
}
```

- [ ] **Step 3: Return to the list when a hand-off arrives while a phone shows a detail**

In `TatsuScreen`, add after the `LaunchedEffect(Unit)` line (add import `androidx.compose.material3.adaptive.layout.PaneAdaptedValue`):

```kotlin
    // a text-selection hand-off while the phone shows a detail: go back to the list. Keyed on the VM's one-shot
    // event, never on `query`: a query effect would also run after rotation/restore and pop the detail every time.
    LaunchedEffect(Unit) {
        vm.handOffs.collect {
            if (navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden) navigator.navigateBack()
        }
    }
```

- [ ] **Step 4: Verify**

```bash
# ENV; phone emulator (tatsu_phone) booted
cd android && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
# 1. the hand-off, the way another app would send it
adb shell am start -n com.abbabon.kanjioffline/.MainActivity -a android.intent.action.PROCESS_TEXT -t text/plain --es android.intent.extra.PROCESS_TEXT 水
sleep 2; adb exec-out screencap -p > "$SCRATCH/pt.png"
# 2. the entry really exists in the manifest
adb shell cmd package query-activities --brief -a android.intent.action.PROCESS_TEXT -t text/plain | grep abbabon
```

Expected: the screenshot shows 水 in the search field and one result row 水; the second command prints `com.abbabon.kanjioffline/.MainActivity`. To see the real menu entry, open any app with selectable text on the emulator (for example Chrome, or Settings search), long-press a word, open the three-dot overflow of the selection toolbar and look for "Tatsu": this is on the manual checklist at the end. Rotation must not pop a phone detail: open a result, then `adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1; sleep 2; adb exec-out screencap -p > "$SCRATCH/rot.png"`; the detail must still be showing (then `user_rotation 0`). Predictive back: confirm the manifest flag took effect with `adb shell dumpsys package com.abbabon.kanjioffline | grep -i -E "enableOnBackInvokedCallback|ON_BACK_INVOKED"` (the grep may print nothing on some builds; the visual check is the manual checklist item).

- [ ] **Step 5: Commit**

```bash
git add android/app/src
git commit -m "Android: text-selection hand-off and predictive back opt-in"
```

---

### Task 12: Instrumented Compose test

Three tests that run on an emulator: tap a row, hand-off, rotation. Run them on the phone AVD (`tatsu_phone`); the first test also copes with a two-pane device.

**Files:**
- Create: `android/app/src/androidTest/java/com/abbabon/kanjioffline/AppTest.kt`

**Interfaces:**
- Consumes: test tags `search`, `row:<k>`, `recent-header`, `detail-kanji`; `Recents`, `recentsStore`; `MainActivity`.
- Produces: `./gradlew connectedDebugAndroidTest` passes on an emulator.

- [ ] **Step 1: Write the tests**

```kotlin
package com.abbabon.kanjioffline

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun cleanRecents() {
        runBlocking { Recents(context.recentsStore).clear() }
    }

    private fun processTextIntent(text: String) =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_PROCESS_TEXT
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        }

    @Test
    fun tappingARowOpensDetailAndRecordsRecent() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntilAtLeastOneExists(hasTestTag("search"), 10_000)
        compose.onNodeWithTag("search").performTextInput("water")
        compose.waitUntilAtLeastOneExists(hasTestTag("row:水"), 10_000)
        compose.onNodeWithTag("row:水").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("detail-kanji"), 5_000)
        compose.waitForIdle()

        // on a phone the list is hidden behind the detail: back returns to it (two-pane keeps both)
        if (compose.onAllNodesWithTag("search").fetchSemanticsNodes().isEmpty()) {
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntilAtLeastOneExists(hasTestTag("search"), 5_000)
        }
        compose.onNodeWithTag("search").performTextClearance()
        compose.waitUntilAtLeastOneExists(hasTestTag("recent-header"), 5_000)
        compose.onNodeWithTag("row:水").assertExists()
        scenario.close()
    }

    @Test
    fun processTextIntentSearchesForTheSelection() {
        val scenario = ActivityScenario.launch<MainActivity>(processTextIntent("水"))
        compose.waitUntilAtLeastOneExists(hasTestTag("row:水"), 10_000)
        compose.onNodeWithTag("search").assertTextContains("水")
        scenario.close()
    }

    // Review Focus 5: rotation re-delivers the launch intent; it must not overwrite what the user typed since
    @Test
    fun recreatingKeepsTheTypedQueryInsteadOfReapplyingTheSelection() {
        val scenario = ActivityScenario.launch<MainActivity>(processTextIntent("水"))
        compose.waitUntilAtLeastOneExists(hasTestTag("row:水"), 10_000)
        compose.onNodeWithTag("search").performTextReplacement("sun")
        compose.waitUntilAtLeastOneExists(hasTestTag("row:日"), 10_000)
        scenario.recreate()
        compose.waitUntilAtLeastOneExists(hasTestTag("search"), 10_000)
        compose.onNodeWithTag("search").assertTextContains("sun")
        scenario.close()
    }
}
```

If `createEmptyComposeRule` cannot see the activity's hierarchy, switch to `createAndroidComposeRule<MainActivity>()` for the first test only and keep the others on the empty rule; do not change what the tests assert.

- [ ] **Step 2: Run on the phone emulator and confirm they pass**

```bash
# ENV
adb emu kill; sleep 3
nohup emulator -avd tatsu_phone -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect > "$SCRATCH/emu.log" 2>&1 &
adb wait-for-device; until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 3; done
adb devices            # exactly one device, and it must be emulator-5554
cd android && ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
```

Expected: `BUILD SUCCESSFUL`, 3 tests passed (`app/build/reports/androidTests/connected/debug/index.html`). If `adb devices` lists a physical phone, unplug it or stop and ask the user: instrumented tests must not run on it.

- [ ] **Step 3: Prove the rotation test can fail**

Temporarily remove the `if (savedInstanceState == null)` guard in `MainActivity.onCreate` (always call `applyProcessText(intent)`), rerun the connected tests, and confirm `recreatingKeepsTheTypedQuery...` fails. Restore the guard and rerun until green. This is a deliberate check, so do not skip it.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/androidTest
git status --short   # MainActivity.kt must be unchanged against the previous commit
git commit -m "Android: instrumented tests for tap, text-selection hand-off and rotation"
```

---

### Task 13: Themed adaptive icon

The icon is drawn from `logo.svg`: dark gradient background, the red diagonal cut, the 断 glyph. The glyph is a text element in the SVG, so its outline is extracted from Noto Serif JP (SIL Open Font License 1.1, the font `logo.svg` already lists as a fallback), downloaded from an official source, and written as vector-drawable path data. Hiragino is not used.

**Files:**
- Create: `android/app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `android/app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `android/app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Create: `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `android/app/src/main/AndroidManifest.xml` (`android:icon`)

**Interfaces:**
- Consumes: `logo.svg` (geometry), Noto Serif JP downloaded in Step 1.
- Produces: launcher icon with an Android 13+ monochrome layer. minSdk is 26, so adaptive icons exist on every supported device and no legacy PNG mipmaps are needed.

- [ ] **Step 1: Generate the glyph path and write the drawables**

Everything below happens in a scratch directory outside the repo (`$SCRATCH`, set by `ENV`); the downloaded font is never committed. Download Noto Serif JP from an official source: the Google Fonts repository (`https://github.com/google/fonts/raw/main/ofl/notoserifjp/NotoSerifJP%5Bwght%5D.ttf`, a variable font; the same directory holds `OFL.txt`) or, if that URL has moved, the `notofonts` / `googlefonts` GitHub release or `fonts.google.com/noto/specimen/Noto+Serif+JP` download. Check that the file is a real font (`file` says TrueType, several MB) and read the licence text next to it to confirm SIL OFL 1.1. Then pin the weight with fontTools (`logo.svg` uses `font-weight` 700, so use wght 700) and extract the outline:

```bash
# ENV
python3 -m venv "$SCRATCH/venv" && "$SCRATCH/venv/bin/pip" install -q fonttools
curl -fL -o "$SCRATCH/NotoSerifJP-VF.ttf" "https://github.com/google/fonts/raw/main/ofl/notoserifjp/NotoSerifJP%5Bwght%5D.ttf"
curl -fL -o "$SCRATCH/OFL.txt" "https://github.com/google/fonts/raw/main/ofl/notoserifjp/OFL.txt"
head -5 "$SCRATCH/OFL.txt"
"$SCRATCH/venv/bin/fonttools" varLib.instancer "$SCRATCH/NotoSerifJP-VF.ttf" wght=700 -o "$SCRATCH/NotoSerifJP-700.ttf"
cat > "$SCRATCH/icon.py" <<'PY'
import sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen

out, font_path = sys.argv[1], sys.argv[2]
font = TTFont(font_path)
gs, name = font.getGlyphSet(), font.getBestCmap()[ord("断")]
upem = font["head"].unitsPerEm
s = 290 / upem                       # font-size 290 in logo.svg (512 viewBox)
bp = BoundsPen(gs); gs[name].draw(bp)
x0, y0, x1, y1 = bp.bounds
tx = 256 - s * (x0 + x1) / 2         # centre the glyph box on (256, 262), as in logo.svg
ty = 262 + s * (y0 + y1) / 2
pen = SVGPathPen(gs, ntos=lambda v: f"{v:.1f}")
gs[name].draw(TransformPen(pen, (s, 0, 0, -s, tx, ty)))
glyph = pen.getCommands()

def vec(body):
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android" '
            'xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" '
            'android:viewportWidth="108" android:viewportHeight="108">\n'
            '    <group android:translateX="21" android:translateY="21" '
            'android:scaleX="0.12890625" android:scaleY="0.12890625">\n' + body + '    </group>\n</vector>\n')

def paths(cut, fill, outline):
    return (f'        <path android:pathData="M84,448 L448,84" android:strokeColor="{cut}" '
            f'android:strokeWidth="22" android:strokeLineCap="round" />\n'
            + (f'        <path android:pathData="{glyph}" android:strokeColor="{outline}" '
               f'android:strokeWidth="10" android:strokeLineJoin="round" />\n' if outline else '')
            + f'        <path android:pathData="{glyph}" android:fillColor="{fill}" />\n')

open(f"{out}/ic_launcher_foreground.xml", "w").write(vec(paths("#FF5C4D", "#FFFFFF", "#121218")))
open(f"{out}/ic_launcher_monochrome.xml", "w").write(vec(paths("#000000", "#000000", None)))
open(f"{out}/ic_launcher_background.xml", "w").write(
    '<vector xmlns:android="http://schemas.android.com/apk/res/android" '
    'xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" '
    'android:viewportWidth="108" android:viewportHeight="108">\n'
    '    <path android:pathData="M0,0h108v108h-108z">\n'
    '        <aapt:attr name="android:fillColor">\n'
    '            <gradient android:startX="0" android:startY="0" android:endX="108" android:endY="108" '
    'android:startColor="#2E3047" android:endColor="#121218" android:type="linear" />\n'
    '        </aapt:attr>\n    </path>\n</vector>\n')
PY
mkdir -p android/app/src/main/res/drawable android/app/src/main/res/mipmap-anydpi-v26
"$SCRATCH/venv/bin/python" -I "$SCRATCH/icon.py" android/app/src/main/res/drawable "$SCRATCH/NotoSerifJP-700.ttf"
```

Keep `$SCRATCH/NotoSerifJP-700.ttf` for Task 16 (if the shell is gone, repeat the download and instancer commands above).

The monochrome layer is the cut and glyph in one flat colour (Android tints it); the 108dp canvas keeps everything inside the 66dp safe zone (the 512 art is scaled by 66/512 and centred).

`android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

In `AndroidManifest.xml` add `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher"` to `<application>`.

Provenance note for the report: the glyph outline comes from Noto Serif JP (SIL OFL 1.1, downloaded from the official Google Fonts repository into a scratch directory); no font file is shipped in the repo, only the extracted shape of one character, with the OFL attribution in the About sheet (`about_font_credit`, Task 8) and README. Report the exact source URL and the OFL copyright line from `OFL.txt`.

- [ ] **Step 2: Build, install and look at the icon**

```bash
# ENV; phone emulator booted
cd android && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell input swipe 540 1800 540 600 300; sleep 1       # open the app drawer
adb exec-out screencap -p > "$SCRATCH/drawer.png"
```

Look at the screenshot: the Tatsu icon shows a dark rounded tile with a red diagonal and a white 断 (Noto Serif JP, so the strokes differ slightly from the Hiragino original in `logo.png`; that is expected; open `logo.png` to compare). If the glyph is off-centre or clipped by the mask, adjust only the centring in the script and regenerate. The themed (monochrome) version is checked on the manual checklist.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main
git commit -m "Android: adaptive and themed launcher icon drawn from logo.svg"
```

---

### Task 14: Release build (R8, signing, AAB)

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create (outside the repo): `~/Keys/tatsu/upload.jks`
- Modify (outside the repo): `~/.gradle/gradle.properties`

**Interfaces:**
- Consumes: the finished app.
- Produces: `./gradlew bundleRelease` writes `android/app/build/outputs/bundle/release/app-release.aab` signed with the upload key; `assembleRelease` yields an R8-shrunk APK that runs.

- [ ] **Step 1: Make an upload keystore outside the repo**

```bash
mkdir -p ~/Keys/tatsu && chmod 700 ~/Keys ~/Keys/tatsu
PW=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
"$JAVA_HOME/bin/keytool" -genkeypair -v -keystore ~/Keys/tatsu/upload.jks \
  -alias upload -keyalg RSA -keysize 2048 -validity 10000 -storepass "$PW" -keypass "$PW" \
  -dname "CN=Tatsu upload key, O=Abbabon, C=IL"
umask 077
cat >> ~/.gradle/gradle.properties <<EOF
TATSU_UPLOAD_STORE_FILE=$HOME/Keys/tatsu/upload.jks
TATSU_UPLOAD_STORE_PASSWORD=$PW
TATSU_UPLOAD_KEY_ALIAS=upload
TATSU_UPLOAD_KEY_PASSWORD=$PW
EOF
```

`JAVA_HOME` is exported on the first line, so the keytool path expands correctly. Do not echo the password anywhere. Tell the user in the report: the keystore and `~/.gradle/gradle.properties` hold the only copy of the upload key; back both up (a password manager works). Losing the upload key is recoverable through Play Console support (Play App Signing holds the real signing key), but it is slow.

- [ ] **Step 2: Sign the release build when the properties exist**

In `app/build.gradle.kts`, inside `android { ... }`, before `buildTypes`:

```kotlin
    // Upload key: only present on the maintainer's Mac (properties live in ~/.gradle/gradle.properties).
    // Without them `bundleRelease` still builds, just unsigned.
    val uploadStore = providers.gradleProperty("TATSU_UPLOAD_STORE_FILE")
    if (uploadStore.isPresent) {
        signingConfigs.create("upload") {
            storeFile = file(uploadStore.get())
            storePassword = providers.gradleProperty("TATSU_UPLOAD_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("TATSU_UPLOAD_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("TATSU_UPLOAD_KEY_PASSWORD").get()
        }
    }
```

and inside `buildTypes { release { ... } }` add:

```kotlin
            signingConfigs.findByName("upload")?.let { signingConfig = it }
```

- [ ] **Step 3: Build the release artifacts, run lint, check the contents**

```bash
# ENV
cd android && ./gradlew clean bundleRelease assembleRelease lintRelease
unzip -l app/build/outputs/bundle/release/app-release.aab | grep -E "kanji.json|AndroidManifest"
$ANDROID_HOME/build-tools/36.0.0/aapt2 dump permissions app/build/outputs/apk/release/app-release.apk | grep -c INTERNET || true   # must print 0
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk | head -5
```

Expected: `BUILD SUCCESSFUL` (fix any lint errors, not warnings; common ones are missing translation or unused resources, fix at the source), the AAB lists `base/assets/kanji.json`, the INTERNET count is `0`, and `apksigner` reports `Verifies`.

- [ ] **Step 4: Smoke-test the shrunk build on the emulator (R8 can break serialization)**

```bash
# ENV; phone emulator booted
adb uninstall com.abbabon.kanjioffline
adb install app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.abbabon.kanjioffline/.MainActivity; sleep 3
adb shell input text water; sleep 2
adb exec-out screencap -p > "$SCRATCH/release.png"
adb logcat -d | grep -E "AndroidRuntime|FATAL" | head
```

Expected: the screenshot shows 水 as the first result and logcat shows no `FATAL EXCEPTION`. If the app crashes decoding JSON, add the missing keep rule for `com.abbabon.kanjioffline.Kanji` to `proguard-rules.pro` (and note it in the commit message). The release APK is signed with the upload key, which is fine for this local smoke test.

- [ ] **Step 5: Commit**

```bash
git add android/app/build.gradle.kts android/app/proguard-rules.pro
git status --short   # no .jks, no passwords
git commit -m "Android: release signing config, R8 verified, AAB build"
```

---

### Task 15: Docs and Play listing text

**Files:**
- Modify: `CLAUDE.md`, `README.md`, `PRIVACY.md`
- Create: `docs/playstore.md`

**Interfaces:**
- Consumes: the finished app and the numbers from earlier tasks.
- Produces: written instructions for running Android tests, an Android section in the README, Android privacy wording, and paste-ready Play Console text.

- [ ] **Step 1: `CLAUDE.md`: add a "Running Android tests" section** after "Running tests":

```markdown
## Running Android tests

Work from `android/`. The Android SDK is in `~/Library/Android/sdk`; the build needs JDK 17:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH
cd android && ./gradlew test                     # JVM unit tests, no emulator needed
```

Instrumented tests (`connectedDebugAndroidTest`) run on an **emulator only**, never on a physical device without asking. Start one headless and check it is the only device:

```bash
$HOME/Library/Android/sdk/emulator/emulator -avd tatsu_phone -no-window -no-audio -gpu swiftshader_indirect &
adb devices                                      # must list only emulator-5554
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
```

Never drive the Mac UI to work with the emulator; use `adb` (`input`, `screencap`).
```

(Keep the nested fences intact in the real file.)

- [ ] **Step 2: `README.md`**: change the tagline to "Offline kanji lookup for iPhone, iPad, Mac and Android." Under "Install" add: "Android: from Google Play (phones, tablets, foldables). To build it yourself, install JDK 17 and run `./gradlew assembleDebug` in `android/`." Add to the project layout table: `android/` | Native Android app (Kotlin, Jetpack Compose). Same search and data; Material 3. | and `docs/playstore.md` | Play Console listing text. Add an "Android" subsection under "Test":

```markdown
### Android

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
cd android && ./gradlew test
```

JVM tests read `Tatsu/kanji.json` directly. UI tests (`./gradlew connectedDebugAndroidTest`) need an emulator. Gradle copies `Tatsu/kanji.json` into the APK at build time; there is only one copy in the repo.
```

Also replace "Can't type a kanji on iPhone? Add the Chinese – Handwriting keyboard and draw it." with the same sentence plus "On Android, add Gboard's Japanese Handwriting layout." Add a short credits line to the README's data or licence section: "The Android icon glyph 断 is drawn from Noto Serif JP (SIL Open Font License 1.1, https://openfontlicense.org)." And add to the search section: "Android: select text in any app and choose Tatsu from the selection menu to look it up."

- [ ] **Step 3: `PRIVACY.md`**: replace "It is stored on your device with SwiftData, is never synced or uploaded, and is visible only to the app." with "It is stored on your device (SwiftData on iPhone, iPad and Mac; Jetpack DataStore on Android), is never synced or uploaded, and is visible only to the app. On Android the recent list never leaves the device and is not included in Google backups (the app sets `allowBackup=\"false\"`)." and replace the deletion paragraph with:

```markdown
To delete it, right-click (Mac) or long-press (iPhone, iPad and Android) an item and choose Remove from history, or use Clear in the Recent list. Deleting the app removes all of its data; on Android you can also clear it in Settings › Apps › Tatsu › Storage › Clear data.
```

Update "Effective date" to `2026-10-06`. The Android app declares no `INTERNET` permission; add that fact to the "Network and third parties" paragraph: "On Android the app does not even request the Internet permission."

- [ ] **Step 4: `docs/playstore.md`**: write this file (model it on `docs/appstore.md`: headings, fenced paste-ready blocks, character counts). Content:

```markdown
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

(the exact text below)

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
```

Verify the counts with Python before committing:

```bash
python3 - <<'PY'
import re,sys
t=open("docs/playstore.md",encoding="utf-8").read()
short=re.search(r"### Short description.*?\n\n    (.+)\n",t).group(1).strip()
print(len(short))
PY
```

Expected: `80`. Fix the header count if different.

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md README.md PRIVACY.md docs/playstore.md
git commit -m "Android: docs, privacy wording and Play listing text"
```

---

### Task 16: Store assets (screenshots, feature graphic, icon)

**Files:**
- Create: `docs/playstore/screenshots/{phone,tablet10}-{search,detail,recent}-light.png` (6 files)
- Create: `docs/playstore/feature-graphic.png`, `docs/playstore/icon-512.png`

**Interfaces:**
- Consumes: the release-quality app; AVDs `tatsu_phone`, `tatsu_tablet10`.
- Produces: the image files Play Console asks for.

Play limits: PNG or JPEG, each side 320-3840 px, the long side at most 2× the short side, at least 2 phone screenshots. 10-inch tablet screenshots are optional but recommended. Light mode only; no dark screenshots and no 7-inch set.

- [ ] **Step 1: Icon and feature graphic**

```bash
mkdir -p docs/playstore/screenshots
```

The 512 icon and the feature graphic are both drawn with Pillow from the Task 13 Noto Serif JP glyph (never from `ios-1024.png`, which uses Hiragino). Run them with `-I` and keep the scripts in `$SCRATCH`. `FONT="$SCRATCH/NotoSerifJP-700.ttf"` (repeat the Task 13 download and instancer commands if it is gone). Full-bleed square, no alpha (Play applies its own mask):

```bash
# ENV
FONT="$SCRATCH/NotoSerifJP-700.ttf"
cat > "$SCRATCH/icon512.py" <<'PY'
import sys
from PIL import Image, ImageDraw, ImageFont
N = 512
img = Image.new("RGB", (N, N))
px = img.load()
for y in range(N):
    for x in range(N):
        t = (x + y) / (2 * N)
        px[x, y] = (int(0x2E + (0x12 - 0x2E) * t), int(0x30 + (0x12 - 0x30) * t), int(0x47 + (0x18 - 0x47) * t))
d = ImageDraw.Draw(img)
d.line([(84, 448), (448, 84)], fill=(0xFF, 0x5C, 0x4D), width=22)
d.text((256, 262), "断", font=ImageFont.truetype(sys.argv[1], 290), fill="white", anchor="mm", stroke_width=5, stroke_fill=(0x12, 0x12, 0x18))
img.save("docs/playstore/icon-512.png")
PY
python3 -I "$SCRATCH/icon512.py" "$FONT"
```

Feature graphic with Pillow (already installed for system Python; run it with `-I` and keep the script in a scratch directory):

```bash
# ENV
cat > "$SCRATCH/feature.py" <<'PY'
import sys
from PIL import Image, ImageDraw, ImageFont
W, H = 1024, 500
img = Image.new("RGB", (W, H))
px = img.load()
for y in range(H):
    for x in range(W):
        t = (x / W + y / H) / 2
        px[x, y] = (int(0x2E + (0x12 - 0x2E) * t), int(0x30 + (0x12 - 0x30) * t), int(0x47 + (0x18 - 0x47) * t))
d = ImageDraw.Draw(img)
d.line([(40, 470), (260, 250)], fill=(0xFF, 0x5C, 0x4D), width=14)
mincho = ImageFont.truetype(sys.argv[1], 300)   # Noto Serif JP wght=700 instance from Task 13
d.text((330, 235), "断", font=mincho, fill="white", anchor="mm")
sans = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 84)
small = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 38)
d.text((520, 190), "Tatsu", font=sans, fill="white", anchor="lm")
d.text((522, 280), "Offline kanji dictionary", font=small, fill=(0xDD, 0xDD, 0xE6), anchor="lm")
img.save("docs/playstore/feature-graphic.png")
PY
python3 -I "$SCRATCH/feature.py" "$FONT"
sips -g pixelWidth -g pixelHeight -g hasAlpha docs/playstore/feature-graphic.png docs/playstore/icon-512.png
```

Expected: 1024×500 with `hasAlpha: no`, and 512×512. Look at both images (the icon must show the Noto glyph and the cut, not the iOS icon); adjust positions in the scripts if the glyph or text collide. If the glyph renders as a box, the wrong font path was passed.

- [ ] **Step 2: Capture phone and tablet screenshots (light mode only; three scenes)**

For each AVD in turn (`tatsu_phone`, `tatsu_tablet10`): boot it headless as in Task 10, install `app-debug.apk` or the release APK, then:

```bash
# ENV; for the phone only: make the aspect ratio 2:1 for Play
adb shell wm size 1080x2160
# demo-clean status bar (no notifications, full battery, fixed clock)
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false

for MODE in light; do
  adb shell cmd uimode night no
  adb shell pm clear com.abbabon.kanjioffline
  adb shell am start -n com.abbabon.kanjioffline/.MainActivity; sleep 3
  adb shell input text water; sleep 2
  adb exec-out screencap -p > docs/playstore/screenshots/DEVICE-search-$MODE.png      # scene 1: results
  # scene 2: open the first result, 水 (tap its row; get the coordinates from the screenshot)
  adb shell input tap X Y; sleep 2
  adb exec-out screencap -p > docs/playstore/screenshots/DEVICE-detail-$MODE.png
  # scene 3: recents. 水 is already recorded; now open 日 so Recent shows 日 then 水
  adb shell input keyevent KEYCODE_BACK; sleep 1                # phone: back to the list (tablet: harmless, may exit; relaunch if so)
  adb shell input text sun; sleep 2
  adb shell input tap X Y; sleep 2                              # first result is 日
  adb shell input keyevent KEYCODE_BACK; sleep 1
  # clear the field: tap the x (read its coordinates off a screenshot) so the Recent header shows
  adb shell input tap CX CY; sleep 2
  adb exec-out screencap -p > docs/playstore/screenshots/DEVICE-recent-$MODE.png
done
```

Replace `DEVICE` with `phone` or `tablet10`, `X Y` with a point inside the first result row (read it off the screenshot), and `CX CY` with the clear (x) button. Scene 3 must show a Recent header with two rows (日, 水). For tablets the detail scene is the two-pane view with 水 highlighted. After each device: `adb shell wm size reset`, `adb shell am broadcast -a com.android.systemui.demo -e command exit`, `adb shell cmd uimode night auto`, then `adb emu kill`. Never use `osascript` or any Mac UI automation.

- [ ] **Step 3: Check every image**

```bash
for f in docs/playstore/screenshots/*.png; do sips -g pixelWidth -g pixelHeight "$f" | tr '\n' ' '; echo "$f"; done
```

Expected: 6 files; each side between 320 and 3840; long side at most 2× the short side. Look at one shot per device class. Reject any with a keyboard covering the content or the system clock showing a real time.

- [ ] **Step 4: Commit**

```bash
git add docs/playstore
git commit -m "Android: Play Store screenshots, feature graphic and icon"
```

---

## Final verification (before handing over)

- [ ] **Run the full automated suite once more, from a clean build**

```bash
# ENV; phone emulator booted and the only device
cd android && ./gradlew clean test bundleRelease && ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
git status --short   # clean
git log --format='%an <%ae> | %cn <%ce>' main..HEAD 2>/dev/null | sort -u   # one line: Abbabon <1280330+Abbabon@users.noreply.github.com> | same
git log --format=%B main..HEAD | grep -i -E "co-authored-by" || echo "no trailers"
```

- [ ] **Report to the user**: branch name `android`, the AAB path, the Noto Serif JP source and licence (Task 13), SDK licence acceptance in Task 1 (the executor accepted them on the user's behalf and says so), and anything in the manual checklist the executor could not do. End the report with a prominent final block:

  **BACK UP THESE TWO FILES (the only copy of the upload key):**
  1. `~/Keys/tatsu/upload.jks`
  2. `~/.gradle/gradle.properties` (holds the keystore password)

- [ ] **Merge and push** (after the user has seen the report, or as instructed): from `/Users/amit/repos/kanji-offline`, `git checkout main && git merge --no-ff android && git push origin main`; the branch `android` was already pushed after each task. No tag yet.

---

## Handover: manual checklist (for the person who ships it)

You do not need Android knowledge. All of this runs on the emulators; you can start one with a window by removing `-no-window` from the emulator command, or install Android Studio (optional) and use its Device Manager, which shows the same AVDs.

Setup for a visible emulator (any of `tatsu_phone`, `tatsu_tablet10`, `tatsu_fold`):

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
$ANDROID_HOME/emulator/emulator -avd tatsu_tablet10 &
cd android && ./gradlew installDebug
```

Then open "Tatsu" from the app drawer (swipe up on the home screen).

1. **Two-pane layout, tablet.** On `tatsu_tablet10`: search `water`, tap a result. The list stays on the left and the detail appears on the right. Rotate with the emulator's toolbar rotate button: still two panes, the query is still in the field.
2. **Foldable posture.** On `tatsu_fold` (phone-sized when folded, tablet-sized when open): open Tatsu folded, then unfold it. In a terminal run `adb shell cmd device_state print-states`, then `adb shell cmd device_state state <number for "OPENED">` and back to the folded state number. Folded: one pane; open: two panes, the selected kanji stays selected.
3. **Predictive back.** On `tatsu_phone`: open a kanji, then drag slowly from the left edge. You should see the detail slide away revealing the list behind it before you let go; releasing completes the back. (If it just jumps, check Settings › System › Developer options › "Predictive back animations" is on.)
4. **Themed icon.** On the phone emulator: long-press the home screen, choose Wallpaper & style, turn on Themed icons. The Tatsu icon should turn into a single-colour glyph matching the wallpaper. Turn it off again: the dark tile with red cut and white 断.
5. **Text-selection menu.** Open Chrome (or any app with selectable text), long-press a word, open the three-dot overflow of the selection toolbar, tap "Tatsu". Tatsu opens with that text searched. Select kanji text such as 日本語 to see the list of three kanji.
6. **Hardware keyboard (tablet).** The emulator accepts your Mac keyboard when it has focus. Type with no field focused: letters go into the search box. Up and Down arrows move the highlighted row and the detail pane follows. Enter picks. Ctrl+F focuses the search box. Confirm that merely moving with the arrows does not add rows to "Recent".
7. **TalkBack.** Settings › Accessibility › TalkBack, turn on. Swipe right through the list: each row reads once as "kanji, meanings, readings" (not three separate stops). Open a kanji: each ON/KUN tile reads as one item. Turn TalkBack off.
8. **Dark mode.** Settings › Display › Dark theme. Check the list, detail, About sheet and the "No matches." screen (search `zzzzqq`) are readable.
9. **The Gboard handwriting hint (needs a real device).** The hint's path was verified on 2026-10-06 against Gboard Help (support.google.com/gboard/answer/9108773). On a physical Android phone with Gboard: search `zzzzqq`, read the hint, follow it (Gboard settings → Languages → Japanese → Handwriting), then draw 水 and a few other kanji and confirm Japanese handwriting recognises them and the menu labels match the hint. If the wording differs, edit `handwriting_hint` in `android/app/src/main/res/values/strings.xml`.
10. **Copy.** Copy a kanji from the detail screen and from a row's long-press menu. On Android 12 and older a "Copied" snackbar shows; on 13+ the system shows its own clipboard preview instead. Paste into the search field to confirm.
11. **Long-press menu.** In Recent, long-press a row: Copy and "Remove from history" appear. In search results only Copy.

If any item fails, note which emulator and Android version, and what you saw.

## Handover: Google Play Console steps

All done by hand in the browser; the upload is not automated.

1. **Developer account.** Go to play.google.com/console and sign up (one-time US$25 fee). Choose "Personal" unless you have a registered organisation. Complete identity verification (government ID, address). Note whether your account falls under the closed-testing requirement (step 7): personal accounts created after 13 November 2023 do, and you may not know which applies until Play Console shows (or does not show) the closed-testing banner.
2. **Create the app.** Create app, name `Tatsu – Offline Kanji`, default language English (United States), App, Free, accept the declarations.
3. **Set up the app (the "Dashboard" checklist).** Work through each task using `docs/playstore.md`: privacy policy URL, app access, ads, content rating questionnaire, target audience (13 and over), data safety (no data collected, none shared), government/financial/health declarations.
4. **Store listing.** Main store listing: paste the short and full description from `docs/playstore.md`; upload `docs/playstore/icon-512.png`, `docs/playstore/feature-graphic.png`, the phone and 10-inch tablet screenshots (light mode only) from `docs/playstore/screenshots/`. Category Education.
5. **Play App Signing and the first upload.** Upload to Testing › Internal testing first: create a release. When asked, keep Play App Signing on (the default). Upload `android/app/build/outputs/bundle/release/app-release.aab` (build it with `cd android && ./gradlew bundleRelease`). It is signed with the upload key in `~/Keys/tatsu/upload.jks`; Google re-signs it with its own key. Add the release notes from `docs/playstore.md`.
6. **Try it yourself.** Add your own Google account as an internal tester, open the opt-in link on an Android device or emulator with the Play Store, and install Tatsu from Play to check the real install.
7. **Closed test (only if Play Console shows the closed-testing requirement banner; unknown for this account until you look).** Create a closed test track, add at least 12 testers (a Google Group or an email list; they need Google accounts and must opt in and stay opted in), release the same AAB there, and keep it running for 14 continuous days. Then in the Dashboard choose "Apply for production" and answer the short questionnaire about the test. If Play Console shows "Apply for production" right away, your account is exempt and you can skip this.
8. **Production.** Create a production release with the same AAB (or a newer one: versionCode must go up with every upload, edit `versionCode` in `android/app/build.gradle.kts`), send for review. First reviews can take several days. After approval choose a staged or full rollout.
9. **Keep safe.** Back up `~/Keys/tatsu/upload.jks` and the passwords in `~/.gradle/gradle.properties`. Remember the package name `com.abbabon.kanjioffline` can never change after the first upload.
10. **Tag (user-gated).** Once you confirm the upload to Play went through, tell the executor; only then create and push the tag: `git tag android-0.1 && git push origin android-0.1` (from `main` after the merge; exact case, no `v`). Future tags: `android-X.Y`, `iOS-X.Y`.
