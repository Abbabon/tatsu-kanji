# Tatsu SwiftUI App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the web-view Mac app with one SwiftUI app for iPhone, iPad and Mac. It does offline kanji lookup, the same as today, plus a list of recent lookups.

**Architecture:** There is one Xcode project with one app target for iOS and macOS, and one unit-test target. Kanji data ships as `kanji.json` in the app bundle and is loaded into memory at launch. `Search.swift` ports `search.js` line for line. Recent lookups are a SwiftData model. The UI is one `NavigationSplitView`.

**Tech Stack:** Swift 5 language mode, SwiftUI, SwiftData, Swift Testing, Xcode 26.2 (installed), Python 3 for the data script. The project has no third-party dependencies and uses no Swift packages.

**Spec:** `docs/superpowers/specs/2026-09-28-tatsu-swiftui-design.md`

## Global Constraints

- Minimum OS: iOS 17.0, iPadOS 17.0, macOS 14.0.
- Bundle ID `com.abbabon.tatsu`. Home-screen name "Tatsu".
- One multiplatform app target (`SUPPORTED_PLATFORMS = "iphoneos iphonesimulator macosx"`, `TARGETED_DEVICE_FAMILY = "1,2"`). No Mac Catalyst.
- No network access anywhere in the app.
- No third-party dependencies. No Swift packages in version 1.
- Recent lookups: at most 100, newest first. Stored on the device only and never synced.
- User-facing strings are plain string literals in SwiftUI views, so Xcode picks them up into `Localizable.xcstrings`.
- Commits: git identity must be `Abbabon <1280330+Abbabon@users.noreply.github.com>` (check with `git config user.name`). **Never** add `Co-Authored-By` or other attribution trailers.
- Work happens on the `swiftui` branch, which already exists and contains the spec.

## Review Focus

These inputs are implied by the spec but not covered by any task's main tests. Each one gets a test in Task 2.

1. **Uppercase English** (`WATER`) should give the same results as `water`.
2. **Repeated kanji in pasted text** (`日日本`) should list each kanji once, in the order it first appears.
3. **Kanji mixed with Latin letters** (`日本 water`) should list only the kanji, as the web version does.
4. **Characters not in the data** (emoji `🍣`, rare CJK such as `〇`, full-width space alone) should return no results and never crash.
5. **Small っ at the end of a reading** (`あっ`) should convert to romaji without crashing (expected result `a`).

## File Map

| File | Responsibility | Task |
|---|---|---|
| `Tatsu.xcodeproj/project.pbxproj` | Project and targets (Xcode 16+ synchronised folders) | 1 |
| `Tatsu.xcodeproj/xcshareddata/xcschemes/Tatsu.xcscheme` | Shared scheme so `xcodebuild test` works | 1 |
| `Tatsu/kanji.json` | Generated KANJIDIC2 data | 1 |
| `Tatsu/Kanji.swift` | `Kanji` model + `Kanji.loadBundled()` | 1 |
| `Tatsu/TatsuApp.swift` | App entry point. Placeholder view in Task 1, real app in Task 4 | 1, 4 |
| `Tatsu/Search.swift` | `toHira`, `romaji`, scoring, `Searcher` | 2 |
| `Tatsu/Recent.swift` | SwiftData `Recent` + `touch` / `clear` | 3 |
| `Tatsu/Format.swift` | `readingParts`, `gradeLabel`, `metaLine`, `copyToPasteboard` | 4 |
| `Tatsu/ContentView.swift` | Search list, recent lookups, empty states, keyboard handling | 4 |
| `Tatsu/KanjiDetail.swift` | Detail view, reading chips | 4 |
| `Tatsu/AboutView.swift` | Attribution sheet | 4 |
| `Tatsu/Assets.xcassets` | App icon (iOS 1024 + macOS sizes) | 5 |
| `Tatsu/Localizable.xcstrings` | String Catalog | 5 |
| `Tatsu/PrivacyInfo.xcprivacy` | Privacy manifest | 5 |
| `TatsuTests/SearchTests.swift` | Data load + search tests | 1, 2 |
| `TatsuTests/RecentTests.swift` | Recent lookups tests | 3 |
| `TatsuTests/FormatTests.swift` | Detail formatting tests | 4 |
| `build_data.py` | Writes `Tatsu/kanji.json` | 1 |
| `README.md`, `.gitignore` | Rewritten or cleaned up | 6 |
| `index.html`, `search.js`, `data.js`, `test.js`, `main.swift`, `make_app.sh` | Deleted | 6 |

Commands used throughout (run from the repo root):

```bash
# unit tests on macOS (no signing needed)
xcodebuild -project Tatsu.xcodeproj -scheme Tatsu -destination 'platform=macOS' CODE_SIGNING_ALLOWED=NO test -quiet
# iOS compile check
xcodebuild -project Tatsu.xcodeproj -scheme Tatsu -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build -quiet
```

These are called **TEST** and **IOS-BUILD** below.

---

### Task 1: Xcode project, kanji data and model

**Files:**
- Create: `Tatsu.xcodeproj/project.pbxproj`, `Tatsu.xcodeproj/xcshareddata/xcschemes/Tatsu.xcscheme`
- Create: `Tatsu/kanji.json` (generated), `Tatsu/Kanji.swift`, `Tatsu/TatsuApp.swift`
- Create: `TatsuTests/SearchTests.swift`
- Modify: `build_data.py:2,37-39`, `.gitignore`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `struct Kanji: Codable, Hashable, Identifiable, Sendable`. Fields: `k: String` (the character), `m: [String]` (meanings), `on: [String]`, `kun: [String]`, `n: [String]` (name readings), `s: Int` (strokes), `g: Int` (grade, 0 = none), `j: Int` (JLPT, 0 = none), `f: Int` (frequency rank, 0 = none). `id` is `k`.
  - `static func Kanji.loadBundled() throws -> [Kanji]`, which reads `kanji.json` from `Bundle.main`.
  - A test target that can run `@testable import Tatsu`.

- [ ] **Step 1: Generate `Tatsu/kanji.json` from the committed `data.js`**

This keeps exactly the data already shipped: 10,384 kanji, sorted by frequency. No download is needed.

```bash
mkdir -p Tatsu TatsuTests
python3 -c "import json;s=open('data.js',encoding='utf-8').read();d=json.loads(s[s.index('=')+1:s.rstrip().rindex(';')]);open('Tatsu/kanji.json','w',encoding='utf-8').write(json.dumps(d,ensure_ascii=False,separators=(',',':')));print(len(d), d[0]['k'])"
```

Expected output: `10384 日`

- [ ] **Step 2: Point `build_data.py` at the new file**

In `build_data.py`, change line 2 to:

```python
"""Download KANJIDIC2 and emit Tatsu/kanji.json for the app. Run once; output is committed."""
```

and replace lines 37-39 with:

```python
with open("Tatsu/kanji.json", "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
print(len(out), "kanji written to Tatsu/kanji.json")
```

Do not run it. Running it downloads a newer KANJIDIC2, which is a separate data update.

- [ ] **Step 3: Write the failing test**

`TatsuTests/SearchTests.swift`:

```swift
import Testing
@testable import Tatsu

struct DataTests {
    @Test func bundledDataLoads() throws {
        let all = try Kanji.loadBundled()
        // 10,384 in the current KANJIDIC2 build; the exact count changes when build_data.py is re-run
        #expect(all.count > 10_000)
        #expect(all[0].k == "日")
        let water = try #require(all.first { $0.k == "水" })
        #expect(water.m.contains("water"))
        #expect(water.kun.contains("みず"))
        #expect(water.s == 4)
    }
}
```

- [ ] **Step 4: Create the minimal app entry point**

`Tatsu/TatsuApp.swift` (replaced in Task 4):

```swift
import SwiftUI

@main
struct TatsuApp: App {
    var body: some Scene {
        WindowGroup { Text("Tatsu") }
    }
}
```

- [ ] **Step 5: Create the Xcode project**

`Tatsu.xcodeproj/project.pbxproj`. The IDs are fixed on purpose so the scheme can reference them. `Tatsu/` and `TatsuTests/` are synchronised folders: every file in them is picked up automatically, with Swift files compiled and everything else copied as a resource.

```
// !$*UTF8*$!
{
	archiveVersion = 1;
	classes = {
	};
	objectVersion = 77;
	objects = {

/* Begin PBXContainerItemProxy section */
		A10000000000000000000010 /* PBXContainerItemProxy */ = {
			isa = PBXContainerItemProxy;
			containerPortal = A10000000000000000000001 /* Project object */;
			proxyType = 1;
			remoteGlobalIDString = A10000000000000000000020;
			remoteInfo = Tatsu;
		};
/* End PBXContainerItemProxy section */

/* Begin PBXFileReference section */
		A10000000000000000000030 /* Tatsu.app */ = {isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = Tatsu.app; sourceTree = BUILT_PRODUCTS_DIR; };
		A10000000000000000000031 /* TatsuTests.xctest */ = {isa = PBXFileReference; explicitFileType = wrapper.cfbundle; includeInIndex = 0; path = TatsuTests.xctest; sourceTree = BUILT_PRODUCTS_DIR; };
/* End PBXFileReference section */

/* Begin PBXFileSystemSynchronizedRootGroup section */
		A10000000000000000000040 /* Tatsu */ = {isa = PBXFileSystemSynchronizedRootGroup; path = Tatsu; sourceTree = "<group>"; };
		A10000000000000000000041 /* TatsuTests */ = {isa = PBXFileSystemSynchronizedRootGroup; path = TatsuTests; sourceTree = "<group>"; };
/* End PBXFileSystemSynchronizedRootGroup section */

/* Begin PBXFrameworksBuildPhase section */
		A10000000000000000000050 /* Frameworks */ = {
			isa = PBXFrameworksBuildPhase;
			buildActionMask = 2147483647;
			files = (
			);
			runOnlyForDeploymentPostprocessing = 0;
		};
		A10000000000000000000051 /* Frameworks */ = {
			isa = PBXFrameworksBuildPhase;
			buildActionMask = 2147483647;
			files = (
			);
			runOnlyForDeploymentPostprocessing = 0;
		};
/* End PBXFrameworksBuildPhase section */

/* Begin PBXGroup section */
		A10000000000000000000002 = {
			isa = PBXGroup;
			children = (
				A10000000000000000000040 /* Tatsu */,
				A10000000000000000000041 /* TatsuTests */,
				A10000000000000000000003 /* Products */,
			);
			sourceTree = "<group>";
		};
		A10000000000000000000003 /* Products */ = {
			isa = PBXGroup;
			children = (
				A10000000000000000000030 /* Tatsu.app */,
				A10000000000000000000031 /* TatsuTests.xctest */,
			);
			name = Products;
			sourceTree = "<group>";
		};
/* End PBXGroup section */

/* Begin PBXNativeTarget section */
		A10000000000000000000020 /* Tatsu */ = {
			isa = PBXNativeTarget;
			buildConfigurationList = A10000000000000000000060 /* Build configuration list for PBXNativeTarget "Tatsu" */;
			buildPhases = (
				A10000000000000000000052 /* Sources */,
				A10000000000000000000050 /* Frameworks */,
				A10000000000000000000054 /* Resources */,
			);
			buildRules = (
			);
			dependencies = (
			);
			fileSystemSynchronizedGroups = (
				A10000000000000000000040 /* Tatsu */,
			);
			name = Tatsu;
			productName = Tatsu;
			productReference = A10000000000000000000030 /* Tatsu.app */;
			productType = "com.apple.product-type.application";
		};
		A10000000000000000000021 /* TatsuTests */ = {
			isa = PBXNativeTarget;
			buildConfigurationList = A10000000000000000000061 /* Build configuration list for PBXNativeTarget "TatsuTests" */;
			buildPhases = (
				A10000000000000000000053 /* Sources */,
				A10000000000000000000051 /* Frameworks */,
				A10000000000000000000055 /* Resources */,
			);
			buildRules = (
			);
			dependencies = (
				A10000000000000000000011 /* PBXTargetDependency */,
			);
			fileSystemSynchronizedGroups = (
				A10000000000000000000041 /* TatsuTests */,
			);
			name = TatsuTests;
			productName = TatsuTests;
			productReference = A10000000000000000000031 /* TatsuTests.xctest */;
			productType = "com.apple.product-type.bundle.unit-test";
		};
/* End PBXNativeTarget section */

/* Begin PBXProject section */
		A10000000000000000000001 /* Project object */ = {
			isa = PBXProject;
			attributes = {
				BuildIndependentTargetsInParallel = 1;
				LastSwiftUpdateCheck = 2620;
				LastUpgradeCheck = 2620;
				TargetAttributes = {
					A10000000000000000000020 = {
						CreatedOnToolsVersion = 26.2;
					};
					A10000000000000000000021 = {
						CreatedOnToolsVersion = 26.2;
						TestTargetID = A10000000000000000000020;
					};
				};
			};
			buildConfigurationList = A10000000000000000000062 /* Build configuration list for PBXProject "Tatsu" */;
			developmentRegion = en;
			hasScannedForEncodings = 0;
			knownRegions = (
				en,
				Base,
			);
			mainGroup = A10000000000000000000002;
			minimizedProjectReferenceProxies = 1;
			preferredProjectObjectVersion = 77;
			productRefGroup = A10000000000000000000003 /* Products */;
			projectDirPath = "";
			projectRoot = "";
			targets = (
				A10000000000000000000020 /* Tatsu */,
				A10000000000000000000021 /* TatsuTests */,
			);
		};
/* End PBXProject section */

/* Begin PBXResourcesBuildPhase section */
		A10000000000000000000054 /* Resources */ = {
			isa = PBXResourcesBuildPhase;
			buildActionMask = 2147483647;
			files = (
			);
			runOnlyForDeploymentPostprocessing = 0;
		};
		A10000000000000000000055 /* Resources */ = {
			isa = PBXResourcesBuildPhase;
			buildActionMask = 2147483647;
			files = (
			);
			runOnlyForDeploymentPostprocessing = 0;
		};
/* End PBXResourcesBuildPhase section */

/* Begin PBXSourcesBuildPhase section */
		A10000000000000000000052 /* Sources */ = {
			isa = PBXSourcesBuildPhase;
			buildActionMask = 2147483647;
			files = (
			);
			runOnlyForDeploymentPostprocessing = 0;
		};
		A10000000000000000000053 /* Sources */ = {
			isa = PBXSourcesBuildPhase;
			buildActionMask = 2147483647;
			files = (
			);
			runOnlyForDeploymentPostprocessing = 0;
		};
/* End PBXSourcesBuildPhase section */

/* Begin PBXTargetDependency section */
		A10000000000000000000011 /* PBXTargetDependency */ = {
			isa = PBXTargetDependency;
			target = A10000000000000000000020 /* Tatsu */;
			targetProxy = A10000000000000000000010 /* PBXContainerItemProxy */;
		};
/* End PBXTargetDependency section */

/* Begin XCBuildConfiguration section */
		A10000000000000000000070 /* Debug */ = {
			isa = XCBuildConfiguration;
			buildSettings = {
				ALWAYS_SEARCH_USER_PATHS = NO;
				CLANG_ENABLE_MODULES = YES;
				DEBUG_INFORMATION_FORMAT = dwarf;
				ENABLE_STRICT_OBJC_MSGSEND = YES;
				ENABLE_TESTABILITY = YES;
				ENABLE_USER_SCRIPT_SANDBOXING = YES;
				GCC_OPTIMIZATION_LEVEL = 0;
				IPHONEOS_DEPLOYMENT_TARGET = 17.0;
				MACOSX_DEPLOYMENT_TARGET = 14.0;
				ONLY_ACTIVE_ARCH = YES;
				SWIFT_ACTIVE_COMPILATION_CONDITIONS = "DEBUG $(inherited)";
				SWIFT_OPTIMIZATION_LEVEL = "-Onone";
			};
			name = Debug;
		};
		A10000000000000000000071 /* Release */ = {
			isa = XCBuildConfiguration;
			buildSettings = {
				ALWAYS_SEARCH_USER_PATHS = NO;
				CLANG_ENABLE_MODULES = YES;
				DEBUG_INFORMATION_FORMAT = "dwarf-with-dsym";
				ENABLE_NS_ASSERTIONS = NO;
				ENABLE_STRICT_OBJC_MSGSEND = YES;
				ENABLE_USER_SCRIPT_SANDBOXING = YES;
				IPHONEOS_DEPLOYMENT_TARGET = 17.0;
				MACOSX_DEPLOYMENT_TARGET = 14.0;
				SWIFT_COMPILATION_MODE = wholemodule;
			};
			name = Release;
		};
		A10000000000000000000072 /* Debug */ = {
			isa = XCBuildConfiguration;
			buildSettings = {
				ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
				CODE_SIGN_STYLE = Automatic;
				CURRENT_PROJECT_VERSION = 1;
				ENABLE_APP_SANDBOX = YES;
				ENABLE_HARDENED_RUNTIME = YES;
				ENABLE_PREVIEWS = YES;
				GENERATE_INFOPLIST_FILE = YES;
				INFOPLIST_KEY_CFBundleDisplayName = Tatsu;
				INFOPLIST_KEY_LSApplicationCategoryType = "public.app-category.education";
				INFOPLIST_KEY_UIApplicationSceneManifest_Generation = YES;
				INFOPLIST_KEY_UILaunchScreen_Generation = YES;
				INFOPLIST_KEY_UISupportedInterfaceOrientations_iPad = "UIInterfaceOrientationPortrait UIInterfaceOrientationPortraitUpsideDown UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight";
				INFOPLIST_KEY_UISupportedInterfaceOrientations_iPhone = "UIInterfaceOrientationPortrait UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight";
				LD_RUNPATH_SEARCH_PATHS = "@executable_path/Frameworks";
				"LD_RUNPATH_SEARCH_PATHS[sdk=macosx*]" = "@executable_path/../Frameworks";
				MARKETING_VERSION = 1.0;
				PRODUCT_BUNDLE_IDENTIFIER = com.abbabon.tatsu;
				PRODUCT_NAME = "$(TARGET_NAME)";
				SDKROOT = auto;
				SUPPORTED_PLATFORMS = "iphoneos iphonesimulator macosx";
				SUPPORTS_MACCATALYST = NO;
				SWIFT_EMIT_LOC_STRINGS = YES;
				SWIFT_VERSION = 5.0;
				TARGETED_DEVICE_FAMILY = "1,2";
			};
			name = Debug;
		};
		A10000000000000000000073 /* Release */ = {
			isa = XCBuildConfiguration;
			buildSettings = {
				ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
				CODE_SIGN_STYLE = Automatic;
				CURRENT_PROJECT_VERSION = 1;
				ENABLE_APP_SANDBOX = YES;
				ENABLE_HARDENED_RUNTIME = YES;
				ENABLE_PREVIEWS = YES;
				GENERATE_INFOPLIST_FILE = YES;
				INFOPLIST_KEY_CFBundleDisplayName = Tatsu;
				INFOPLIST_KEY_LSApplicationCategoryType = "public.app-category.education";
				INFOPLIST_KEY_UIApplicationSceneManifest_Generation = YES;
				INFOPLIST_KEY_UILaunchScreen_Generation = YES;
				INFOPLIST_KEY_UISupportedInterfaceOrientations_iPad = "UIInterfaceOrientationPortrait UIInterfaceOrientationPortraitUpsideDown UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight";
				INFOPLIST_KEY_UISupportedInterfaceOrientations_iPhone = "UIInterfaceOrientationPortrait UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight";
				LD_RUNPATH_SEARCH_PATHS = "@executable_path/Frameworks";
				"LD_RUNPATH_SEARCH_PATHS[sdk=macosx*]" = "@executable_path/../Frameworks";
				MARKETING_VERSION = 1.0;
				PRODUCT_BUNDLE_IDENTIFIER = com.abbabon.tatsu;
				PRODUCT_NAME = "$(TARGET_NAME)";
				SDKROOT = auto;
				SUPPORTED_PLATFORMS = "iphoneos iphonesimulator macosx";
				SUPPORTS_MACCATALYST = NO;
				SWIFT_EMIT_LOC_STRINGS = YES;
				SWIFT_VERSION = 5.0;
				TARGETED_DEVICE_FAMILY = "1,2";
			};
			name = Release;
		};
		A10000000000000000000074 /* Debug */ = {
			isa = XCBuildConfiguration;
			buildSettings = {
				BUNDLE_LOADER = "$(TEST_HOST)";
				CODE_SIGN_STYLE = Automatic;
				GENERATE_INFOPLIST_FILE = YES;
				PRODUCT_BUNDLE_IDENTIFIER = com.abbabon.tatsu.tests;
				PRODUCT_NAME = "$(TARGET_NAME)";
				SDKROOT = auto;
				SUPPORTED_PLATFORMS = "iphoneos iphonesimulator macosx";
				SUPPORTS_MACCATALYST = NO;
				SWIFT_VERSION = 5.0;
				TARGETED_DEVICE_FAMILY = "1,2";
				TEST_HOST = "$(BUILT_PRODUCTS_DIR)/Tatsu.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/Tatsu";
			};
			name = Debug;
		};
		A10000000000000000000075 /* Release */ = {
			isa = XCBuildConfiguration;
			buildSettings = {
				BUNDLE_LOADER = "$(TEST_HOST)";
				CODE_SIGN_STYLE = Automatic;
				GENERATE_INFOPLIST_FILE = YES;
				PRODUCT_BUNDLE_IDENTIFIER = com.abbabon.tatsu.tests;
				PRODUCT_NAME = "$(TARGET_NAME)";
				SDKROOT = auto;
				SUPPORTED_PLATFORMS = "iphoneos iphonesimulator macosx";
				SUPPORTS_MACCATALYST = NO;
				SWIFT_VERSION = 5.0;
				TARGETED_DEVICE_FAMILY = "1,2";
				TEST_HOST = "$(BUILT_PRODUCTS_DIR)/Tatsu.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/Tatsu";
			};
			name = Release;
		};
/* End XCBuildConfiguration section */

/* Begin XCConfigurationList section */
		A10000000000000000000060 /* Build configuration list for PBXNativeTarget "Tatsu" */ = {
			isa = XCConfigurationList;
			buildConfigurations = (
				A10000000000000000000072 /* Debug */,
				A10000000000000000000073 /* Release */,
			);
			defaultConfigurationIsVisible = 0;
			defaultConfigurationName = Release;
		};
		A10000000000000000000061 /* Build configuration list for PBXNativeTarget "TatsuTests" */ = {
			isa = XCConfigurationList;
			buildConfigurations = (
				A10000000000000000000074 /* Debug */,
				A10000000000000000000075 /* Release */,
			);
			defaultConfigurationIsVisible = 0;
			defaultConfigurationName = Release;
		};
		A10000000000000000000062 /* Build configuration list for PBXProject "Tatsu" */ = {
			isa = XCConfigurationList;
			buildConfigurations = (
				A10000000000000000000070 /* Debug */,
				A10000000000000000000071 /* Release */,
			);
			defaultConfigurationIsVisible = 0;
			defaultConfigurationName = Release;
		};
/* End XCConfigurationList section */
	};
	rootObject = A10000000000000000000001 /* Project object */;
}
```

`Tatsu.xcodeproj/xcshareddata/xcschemes/Tatsu.xcscheme`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion = "2620" version = "1.7">
   <BuildAction parallelizeBuildables = "YES" buildImplicitDependencies = "YES">
      <BuildActionEntries>
         <BuildActionEntry buildForTesting = "YES" buildForRunning = "YES" buildForProfiling = "YES" buildForArchiving = "YES" buildForAnalyzing = "YES">
            <BuildableReference BuildableIdentifier = "primary" BlueprintIdentifier = "A10000000000000000000020" BuildableName = "Tatsu.app" BlueprintName = "Tatsu" ReferencedContainer = "container:Tatsu.xcodeproj">
            </BuildableReference>
         </BuildActionEntry>
      </BuildActionEntries>
   </BuildAction>
   <TestAction buildConfiguration = "Debug" selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv = "YES">
      <Testables>
         <TestableReference skipped = "NO">
            <BuildableReference BuildableIdentifier = "primary" BlueprintIdentifier = "A10000000000000000000021" BuildableName = "TatsuTests.xctest" BlueprintName = "TatsuTests" ReferencedContainer = "container:Tatsu.xcodeproj">
            </BuildableReference>
         </TestableReference>
      </Testables>
   </TestAction>
   <LaunchAction buildConfiguration = "Debug" selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB" launchStyle = "0" useCustomWorkingDirectory = "NO" ignoresPersistentStateOnLaunch = "NO" debugDocumentVersioning = "YES" debugServiceExtension = "internal" allowLocationSimulation = "YES">
      <BuildableProductRunnable runnableDebuggingMode = "0">
         <BuildableReference BuildableIdentifier = "primary" BlueprintIdentifier = "A10000000000000000000020" BuildableName = "Tatsu.app" BlueprintName = "Tatsu" ReferencedContainer = "container:Tatsu.xcodeproj">
         </BuildableReference>
      </BuildableProductRunnable>
   </LaunchAction>
   <ProfileAction buildConfiguration = "Release" shouldUseLaunchSchemeArgsEnv = "YES" savedToolIdentifier = "" useCustomWorkingDirectory = "NO" debugDocumentVersioning = "YES">
   </ProfileAction>
   <AnalyzeAction buildConfiguration = "Debug">
   </AnalyzeAction>
   <ArchiveAction buildConfiguration = "Release" revealArchiveInOrganizer = "YES">
   </ArchiveAction>
</Scheme>
```

Check that the project parses: `xcodebuild -list -project Tatsu.xcodeproj`. Expected output: targets `Tatsu`, `TatsuTests` and scheme `Tatsu`.

**Fallback if the project doesn't parse:** in Xcode, choose File → New → Project → Multiplatform → App, name it `Tatsu`, and save it at the repo root. Then set the build settings listed above and point it at the existing `Tatsu/` and `TatsuTests/` folders. Then continue.

- [ ] **Step 6: Run TEST to verify it fails**

Expected: compile error `cannot find 'Kanji' in scope`.

- [ ] **Step 7: Implement `Kanji`**

`Tatsu/Kanji.swift`:

```swift
import Foundation

/// One KANJIDIC2 entry. Short keys match kanji.json (see build_data.py).
struct Kanji: Codable, Hashable, Identifiable, Sendable {
    let k: String      // the character
    let m: [String]    // English meanings
    let on: [String]   // on'yomi, katakana
    let kun: [String]  // kun'yomi, hiragana; "." marks okurigana, "-" marks a prefix/suffix
    let n: [String]    // nanori (name readings)
    let s: Int         // stroke count
    let g: Int         // school grade, 0 = none
    let j: Int         // JLPT level (old 1-4 scale), 0 = none
    let f: Int         // frequency rank, 0 = unranked

    var id: String { k }

    static func loadBundled() throws -> [Kanji] {
        guard let url = Bundle.main.url(forResource: "kanji", withExtension: "json") else {
            throw CocoaError(.fileNoSuchFile)
        }
        return try JSONDecoder().decode([Kanji].self, from: Data(contentsOf: url))
    }
}
```

- [ ] **Step 8: Run TEST and IOS-BUILD to verify they pass**

Expected: TEST prints `** TEST SUCCEEDED **` (or just exits 0 with `-quiet`), and IOS-BUILD exits 0.

- [ ] **Step 9: Ignore Xcode user files and commit**

Add these lines to `.gitignore`:

```
xcuserdata/
build/
```

```bash
git add .gitignore build_data.py Tatsu Tatsu.xcodeproj TatsuTests
git commit -m "Xcode project, kanji.json and Kanji model"
```

---

### Task 2: Port the search

**Files:**
- Create: `Tatsu/Search.swift`
- Modify: `TatsuTests/SearchTests.swift` (add a second suite)

**Interfaces:**
- Consumes: `Kanji`, `Kanji.loadBundled()` (Task 1).
- Produces:
  - `func toHira(_ s: String) -> String`, which converts katakana to hiragana.
  - `func romaji(_ kana: String) -> String`, which converts kana to wapuro romaji.
  - `struct Searcher: Sendable` with `init(_ list: [Kanji])`, `func search(_ query: String, limit: Int = 60) -> [Kanji]` and `func kanji(_ character: String) -> Kanji?`.

- [ ] **Step 1: Write the failing tests**

Append to `TatsuTests/SearchTests.swift`:

```swift
struct RomajiTests {
    // same cases as the old test.js
    @Test func romajiConversion() {
        #expect(romaji("きょう") == "kyou")
        #expect(romaji("ニチ") == "nichi")
        #expect(romaji("た.べる") == "taberu")
        #expect(romaji("まっちゃ") == "matcha")
        #expect(romaji("がっこう") == "gakkou")
        #expect(romaji("じゅう") == "juu")
    }

    @Test func sokuonAtEndDoesNotCrash() {
        #expect(romaji("あっ") == "a")
    }

    @Test func katakanaToHiragana() {
        #expect(toHira("ミズ") == "みず")
        #expect(toHira("water") == "water")
    }
}

struct SearchTests {
    let searcher: Searcher
    init() throws { searcher = Searcher(try Kanji.loadBundled()) }

    func first(_ q: String) -> String? { searcher.search(q).first?.k }
    func top3(_ q: String) -> [String] { searcher.search(q).prefix(3).map(\.k) }

    // same cases as the old test.js
    @Test func rankingMatchesWebVersion() {
        #expect(first("water") == "水")
        #expect(first("みず") == "水")
        #expect(first("ミズ") == "水")
        #expect(first("mizu") == "水")
        #expect(first("sun") == "日")
        #expect(first("nichi") == "日")
        #expect(first("taberu") == "食")
    }

    @Test func kanjiTextListsEachKanji() {
        #expect(searcher.search("日本語").map(\.k) == ["日", "本", "語"])
    }

    @Test func typoTolerance() {
        #expect(top3("watr").contains("水"))
        #expect(top3("mountian").contains("山"))
    }

    @Test func blankQueryIsEmpty() {
        #expect(searcher.search("   ").isEmpty)
        #expect(searcher.search("").isEmpty)
        #expect(searcher.search("\u{3000}").isEmpty)
    }

    // Review Focus
    @Test func uppercaseMatchesLowercase() {
        #expect(first("WATER") == "水")
    }

    @Test func repeatedKanjiListedOnce() {
        #expect(searcher.search("日日本").map(\.k) == ["日", "本"])
    }

    @Test func kanjiMixedWithLatinListsOnlyKanji() {
        #expect(searcher.search("日本 water").map(\.k) == ["日", "本"])
    }

    @Test func unknownCharactersReturnNothing() {
        #expect(searcher.search("🍣").isEmpty)
        #expect(searcher.search("〇〇").isEmpty)
    }

    @Test func lookupByCharacter() {
        #expect(searcher.kanji("水")?.m.contains("water") == true)
        #expect(searcher.kanji("x") == nil)
    }

    @Test func limitIsRespected() {
        #expect(searcher.search("a", limit: 5).count <= 5)
    }
}
```

- [ ] **Step 2: Run TEST to verify it fails**

Expected: compile errors `cannot find 'romaji'`, `cannot find 'Searcher'`.

- [ ] **Step 3: Implement `Search.swift`**

This is a line-for-line port of `search.js`, so ranking must stay the same.

`Tatsu/Search.swift`:

```swift
import Foundation

/// Katakana (ァ-ヶ) to hiragana; everything else unchanged.
func toHira(_ s: String) -> String {
    String(String.UnicodeScalarView(s.unicodeScalars.map { c in
        (0x30A1...0x30F6).contains(c.value) ? Unicode.Scalar(c.value - 0x60)! : c
    }))
}

// kana -> wapuro romaji (what people actually type). Digraphs are looked up first so they win.
private let roma: [String: String] = {
    let table = "きゃkya きゅkyu きょkyo しゃsha しゅshu しょsho ちゃcha ちゅchu ちょcho にゃnya にゅnyu にょnyo " +
        "ひゃhya ひゅhyu ひょhyo みゃmya みゅmyu みょmyo りゃrya りゅryu りょryo ぎゃgya ぎゅgyu ぎょgyo " +
        "じゃja じゅju じょjo ぢゃja ぢゅju ぢょjo びゃbya びゅbyu びょbyo ぴゃpya ぴゅpyu ぴょpyo " +
        "あa いi うu えe おo かka きki くku けke こko さsa しshi すsu せse そso たta ちchi つtsu てte とto " +
        "なna にni ぬnu ねne のno はha ひhi ふfu へhe ほho まma みmi むmu めme もmo やya ゆyu よyo " +
        "らra りri るru れre ろro わwa ゐwi ゑwe をwo んn がga ぎgi ぐgu げge ごgo ざza じji ずzu ぜze ぞzo " +
        "だda ぢji づzu でde どdo ばba びbi ぶbu べbe ぼbo ぱpa ぴpi ぷpu ぺpe ぽpo ぁa ぃi ぅu ぇe ぉo ゔvu"
    var out: [String: String] = [:]
    for token in table.split(separator: " ") {
        let kana = token.prefix { !("a"..."z").contains($0) }
        out[String(kana)] = String(token.dropFirst(kana.count))
    }
    return out
}()

func romaji(_ kana: String) -> String {
    let k = Array(toHira(kana).filter { $0 != "." && $0 != "-" && $0 != "ー" })
    var out = ""
    var i = 0
    while i < k.count {
        if k[i] == "っ" {
            // small tsu doubles the next consonant ("tch" for ch)
            let rest = romaji(String(k[(i + 1)...]))
            let double = rest.first.map { "aeiou".contains($0) ? "" : $0 == "c" ? "t" : String($0) } ?? ""
            return out + double + rest
        }
        if i + 1 < k.count, let two = roma[String(k[i...i + 1])] {
            out += two
            i += 2
        } else {
            out += roma[String(k[i])] ?? String(k[i])
            i += 1
        }
    }
    return out
}

/// Edit distance <= 1 (insert, delete, substitute, or swap two neighbours).
private func within1(_ a: [Character], _ b: [Character]) -> Bool {
    if abs(a.count - b.count) > 1 { return false }
    var i = 0, j = 0, used = false
    while i < a.count && j < b.count {
        if a[i] == b[j] { i += 1; j += 1; continue }
        if used { return false }
        used = true
        if a.count == b.count, i + 1 < a.count, j + 1 < b.count, a[i] == b[j + 1], a[i + 1] == b[j] {
            i += 2; j += 2; continue
        }
        if a.count > b.count { i += 1 } else if a.count < b.count { j += 1 } else { i += 1; j += 1 }
    }
    return true
}

/// True if all of q's characters appear in s, in order.
private func subseq(_ q: String, _ s: String) -> Bool {
    var it = q.makeIterator()
    var want = it.next()
    for c in s where c == want { want = it.next() }
    return want == nil
}

private func score(_ q: String, _ s: String) -> Int {
    if s == q { return 100 }
    if s.hasPrefix(q) { return 80 }
    if (" " + s).contains(" " + q) { return 70 }
    if s.contains(q) { return 55 }
    if q.count >= 4 {
        let qc = Array(q)
        if s.split(separator: " ").contains(where: { within1(qc, Array($0)) }) { return 50 }
    }
    if q.count >= 3 && subseq(q, s) { return 30 }
    return 0
}

/// In-memory index over all kanji. Build once at launch.
// ponytail: linear scan of ~10k entries per keystroke (a few ms); add a prefix index if the words pack makes it slow
struct Searcher: Sendable {
    private struct Entry: Sendable {
        let kanji: Kanji
        let meanings: [String]  // lowercased
        let kana: [String]      // hiragana, no "." or "-"
        let roma: [String]
    }

    private let entries: [Entry]
    private let byKanji: [String: Kanji]

    init(_ list: [Kanji]) {
        entries = list.map { e in
            let kana = (e.on + e.kun).map { toHira($0).filter { $0 != "." && $0 != "-" } }
            return Entry(kanji: e, meanings: e.m.map { $0.lowercased() }, kana: kana, roma: kana.map(romaji))
        }
        byKanji = Dictionary(list.map { ($0.k, $0) }, uniquingKeysWith: { first, _ in first })
    }

    func kanji(_ character: String) -> Kanji? { byKanji[character] }

    func search(_ query: String, limit: Int = 60) -> [Kanji] {
        let q = toHira(query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())
        if q.isEmpty { return [] }

        // any known kanji in the input: list those, in order, once each
        var seen = Set<String>()
        var listed: [Kanji] = []
        for c in q {
            let s = String(c)
            if let e = byKanji[s], seen.insert(s).inserted { listed.append(e) }
        }
        if !listed.isEmpty { return listed }

        let isKana = q.unicodeScalars.allSatisfy { (0x3041...0x309F).contains($0.value) }
        var hits: [(score: Int, index: Int, kanji: Kanji)] = []
        for (index, e) in entries.enumerated() {
            var best = 0
            for field in isKana ? e.kana : e.meanings + e.roma {
                best = max(best, score(q, field))
                if best == 100 { break }
            }
            if best > 0 { hits.append((best, index, e.kanji)) }
        }
        // higher score, then more frequent (unranked last), then data order (JS sort is stable)
        func rank(_ f: Int) -> Int { f == 0 ? 9999 : f }
        hits.sort { a, b in
            if a.score != b.score { return a.score > b.score }
            if rank(a.kanji.f) != rank(b.kanji.f) { return rank(a.kanji.f) < rank(b.kanji.f) }
            return a.index < b.index
        }
        return hits.prefix(limit).map(\.kanji)
    }
}
```

- [ ] **Step 4: Run TEST to verify it passes**

Expected: every test in `RomajiTests`, `SearchTests` and `DataTests` passes.

If `unknownCharactersReturnNothing` fails because `〇` exists in `kanji.json`, replace `"〇〇"` with another character, `"〆"`, and check that `searcher.kanji("〆") == nil` first. The goal of the test is a character that isn't in the data.

- [ ] **Step 5: Commit**

```bash
git add Tatsu/Search.swift TatsuTests/SearchTests.swift
git commit -m "Port search.js to Swift"
```

---

### Task 3: Recent lookups

**Files:**
- Create: `Tatsu/Recent.swift`, `TatsuTests/RecentTests.swift`

**Interfaces:**
- Consumes: nothing from earlier tasks. It stores only the kanji character.
- Produces:
  - `@Model final class Recent` with `kanji: String` (unique) and `opened: Date`.
  - `static func Recent.touch(_ kanji: String, at date: Date = .now, in context: ModelContext, keep: Int = 100)`
  - `static func Recent.clear(in context: ModelContext)`

- [ ] **Step 1: Write the failing tests**

`TatsuTests/RecentTests.swift`:

```swift
import Foundation
import SwiftData
import Testing
@testable import Tatsu

@MainActor
struct RecentTests {
    let container: ModelContainer  // held so the in-memory store lives as long as the test
    var context: ModelContext { container.mainContext }

    init() throws {
        container = try ModelContainer(for: Recent.self, configurations: ModelConfiguration(isStoredInMemoryOnly: true))
    }

    func all() throws -> [String] {
        try context.fetch(FetchDescriptor<Recent>(sortBy: [SortDescriptor(\.opened, order: .reverse)])).map(\.kanji)
    }

    @Test func newestFirst() throws {
        Recent.touch("水", at: Date(timeIntervalSince1970: 1), in: context)
        Recent.touch("日", at: Date(timeIntervalSince1970: 2), in: context)
        #expect(try all() == ["日", "水"])
    }

    @Test func touchingAgainMovesToTopWithoutDuplicate() throws {
        Recent.touch("水", at: Date(timeIntervalSince1970: 1), in: context)
        Recent.touch("日", at: Date(timeIntervalSince1970: 2), in: context)
        Recent.touch("水", at: Date(timeIntervalSince1970: 3), in: context)
        #expect(try all() == ["水", "日"])
    }

    @Test func keepsOnlyNewestHundred() throws {
        // 101 distinct CJK characters starting at 一 (U+4E00)
        let chars = (0..<101).map { String(Unicode.Scalar(0x4E00 + $0)!) }
        for (i, c) in chars.enumerated() {
            Recent.touch(c, at: Date(timeIntervalSince1970: Double(i)), in: context)
        }
        let kept = try all()
        #expect(kept.count == 100)
        #expect(kept.first == chars[100])
        #expect(!kept.contains(chars[0]))
    }

    @Test func clearRemovesEverything() throws {
        Recent.touch("水", in: context)
        Recent.touch("日", in: context)
        Recent.clear(in: context)
        #expect(try all().isEmpty)
    }
}
```

- [ ] **Step 2: Run TEST to verify it fails**

Expected: compile error `cannot find 'Recent' in scope`.

- [ ] **Step 3: Implement `Recent.swift`**

`Tatsu/Recent.swift`:

```swift
import Foundation
import SwiftData

/// A kanji the user opened or copied. Local to the device, never synced.
@Model
final class Recent {
    @Attribute(.unique) var kanji: String
    var opened: Date

    init(kanji: String, opened: Date) {
        self.kanji = kanji
        self.opened = opened
    }

    /// Insert or bump `kanji` to the top, then drop everything past the newest `keep`.
    static func touch(_ kanji: String, at date: Date = .now, in context: ModelContext, keep: Int = 100) {
        let same = FetchDescriptor<Recent>(predicate: #Predicate { $0.kanji == kanji })
        if let existing = try? context.fetch(same).first {
            existing.opened = date
        } else {
            context.insert(Recent(kanji: kanji, opened: date))
        }
        var overflow = FetchDescriptor<Recent>(sortBy: [SortDescriptor(\.opened, order: .reverse)])
        overflow.fetchOffset = keep
        for old in (try? context.fetch(overflow)) ?? [] { context.delete(old) }
        try? context.save()
    }

    static func clear(in context: ModelContext) {
        for r in (try? context.fetch(FetchDescriptor<Recent>())) ?? [] { context.delete(r) }
        try? context.save()
    }
}
```

- [ ] **Step 4: Run TEST to verify it passes**

Expected: all `RecentTests` pass, and the earlier suites still pass.

- [ ] **Step 5: Commit**

```bash
git add Tatsu/Recent.swift TatsuTests/RecentTests.swift
git commit -m "Recent lookups model"
```

---

### Task 4: User interface

**Files:**
- Create: `Tatsu/Format.swift`, `Tatsu/ContentView.swift`, `Tatsu/KanjiDetail.swift`, `Tatsu/AboutView.swift`, `TatsuTests/FormatTests.swift`
- Modify: `Tatsu/TatsuApp.swift` (full replacement)

**Interfaces:**
- Consumes: `Kanji` (Task 1). `Searcher`, `romaji` (Task 2). `Recent`, `Recent.touch`, `Recent.clear` (Task 3).
- Produces:
  - `struct ReadingParts: Equatable { let prefix, kana, okurigana, suffix: String }`
  - `func readingParts(_ reading: String) -> ReadingParts`
  - `func gradeLabel(_ g: Int) -> String`
  - `func metaLine(_ e: Kanji) -> String`
  - `func copyToPasteboard(_ s: String)`
  - Views: `ContentView(searcher:)`, `KanjiDetail(kanji:onCopy:)`, `AboutView()`

- [ ] **Step 1: Write the failing formatting tests**

`TatsuTests/FormatTests.swift`:

```swift
import Testing
@testable import Tatsu

struct FormatTests {
    @Test func readingWithOkurigana() {
        #expect(readingParts("た.べる") == ReadingParts(prefix: "", kana: "た", okurigana: "べる", suffix: ""))
    }

    @Test func readingWithAffixDashes() {
        #expect(readingParts("-び") == ReadingParts(prefix: "-", kana: "び", okurigana: "", suffix: ""))
        #expect(readingParts("お-") == ReadingParts(prefix: "", kana: "お", okurigana: "", suffix: "-"))
        #expect(readingParts("スイ") == ReadingParts(prefix: "", kana: "スイ", okurigana: "", suffix: ""))
    }

    @Test func gradeLabels() {
        #expect(gradeLabel(1) == "grade 1")
        #expect(gradeLabel(8) == "jōyō (secondary)")
        #expect(gradeLabel(9) == "jinmeiyō")
    }

    @Test func metaLineSkipsMissingValues() {
        let water = Kanji(k: "水", m: ["water"], on: ["スイ"], kun: ["みず"], n: [], s: 4, g: 1, j: 4, f: 223)
        #expect(metaLine(water) == "4 strokes · grade 1 · JLPT 4 · #223 freq")
        let rare = Kanji(k: "鬱", m: ["gloom"], on: [], kun: [], n: [], s: 29, g: 0, j: 0, f: 0)
        #expect(metaLine(rare) == "29 strokes")
    }
}
```

- [ ] **Step 2: Run TEST to verify it fails**

Expected: compile errors `cannot find 'readingParts'`, `cannot find 'ReadingParts'`.

- [ ] **Step 3: Implement `Format.swift`**

`Tatsu/Format.swift`:

```swift
import Foundation
#if canImport(UIKit)
import UIKit
#else
import AppKit
#endif

/// "た.べる" -> kana た (shown over the kanji), okurigana べる after it.
/// "-び" / "お-" keep their dash outside, marking a suffix / prefix reading.
struct ReadingParts: Equatable {
    let prefix, kana, okurigana, suffix: String
}

func readingParts(_ reading: String) -> ReadingParts {
    let pieces = reading.split(separator: ".", maxSplits: 1, omittingEmptySubsequences: false)
    var stem = String(pieces[0])
    let okurigana = pieces.count > 1 ? String(pieces[1]) : ""
    let prefix = stem.hasPrefix("-") ? "-" : ""
    if !prefix.isEmpty { stem.removeFirst() }
    let suffix = stem.hasSuffix("-") ? "-" : ""
    if !suffix.isEmpty { stem.removeLast() }
    return ReadingParts(prefix: prefix, kana: stem, okurigana: okurigana, suffix: suffix)
}

func gradeLabel(_ g: Int) -> String {
    g <= 6 ? "grade \(g)" : g == 8 ? "jōyō (secondary)" : "jinmeiyō"
}

func metaLine(_ e: Kanji) -> String {
    var parts = ["\(e.s) strokes"]
    if e.g > 0 { parts.append(gradeLabel(e.g)) }
    if e.j > 0 { parts.append("JLPT \(e.j)") }
    if e.f > 0 { parts.append("#\(e.f) freq") }
    return parts.joined(separator: " · ")
}

func copyToPasteboard(_ s: String) {
    #if canImport(UIKit)
    UIPasteboard.general.string = s
    #else
    NSPasteboard.general.clearContents()
    NSPasteboard.general.setString(s, forType: .string)
    #endif
}
```

- [ ] **Step 4: Run TEST to verify `FormatTests` passes**

Expected: all suites pass.

- [ ] **Step 5: Implement the detail view**

`Tatsu/KanjiDetail.swift`:

```swift
import SwiftUI

struct KanjiDetail: View {
    let kanji: Kanji
    let onCopy: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .top) {
                    Text(kanji.k)
                        .font(.system(size: 96))
                        .textSelection(.enabled)
                    Spacer()
                    Button("Copy", systemImage: "doc.on.doc", action: onCopy)
                        .buttonStyle(.bordered)
                }
                Text(kanji.m.joined(separator: ", "))
                    .font(.title3.weight(.semibold))
                ReadingRow(label: "on", kanji: kanji.k, readings: kanji.on)
                ReadingRow(label: "kun", kanji: kanji.k, readings: kanji.kun)
                if !kanji.n.isEmpty {
                    LabeledRow(label: "names") {
                        Text(kanji.n.joined(separator: "、")).foregroundStyle(.secondary)
                    }
                }
                Text(metaLine(kanji))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .navigationTitle(kanji.k)
    }
}

private struct LabeledRow<Content: View>: View {
    let label: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label.uppercased())
                .font(.caption2)
                .tracking(1)
                .foregroundStyle(.secondary)
            content
        }
    }
}

/// Furigana stand-in: SwiftUI has no <ruby>, so each reading is kana / kanji+okurigana / romaji stacked.
private struct ReadingRow: View {
    let label: String
    let kanji: String
    let readings: [String]

    var body: some View {
        if !readings.isEmpty {
            LabeledRow(label: label) {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 76), alignment: .leading)], alignment: .leading, spacing: 10) {
                    ForEach(readings, id: \.self) { r in
                        let p = readingParts(r)
                        VStack(alignment: .leading, spacing: 0) {
                            Text(p.kana).font(.caption).foregroundStyle(.tint)
                            Text(p.prefix + kanji + p.okurigana + p.suffix).font(.title3)
                            Text(romaji(r)).font(.caption2).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 6: Implement the About sheet**

`Tatsu/AboutView.swift`:

```swift
import SwiftUI

struct AboutView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Tatsu · 断 · to cut off, to sever")
                    Text("Offline kanji lookup. No account, no network.")
                        .foregroundStyle(.secondary)
                }
                Section("Kanji data") {
                    Text("KANJIDIC2 by the Electronic Dictionary Research and Development Group, used under CC BY-SA 4.0.")
                    Link("KANJIDIC Project", destination: URL(string: "https://www.edrdg.org/wiki/index.php/KANJIDIC_Project")!)
                    Link("CC BY-SA 4.0 license", destination: URL(string: "https://creativecommons.org/licenses/by-sa/4.0/")!)
                }
                Section("App") {
                    Text("App code is MIT licensed.")
                    Link("Source code", destination: URL(string: "https://github.com/Abbabon/tatsu-kanji")!)
                }
            }
            .navigationTitle("About")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        #if os(macOS)
        .frame(minWidth: 420, minHeight: 360)
        #endif
    }
}
```

- [ ] **Step 7: Implement the main screen**

`Tatsu/ContentView.swift`:

```swift
import SwiftData
import SwiftUI

struct ContentView: View {
    let searcher: Searcher

    @Environment(\.modelContext) private var context
    @Query(sort: \Recent.opened, order: .reverse) private var recents: [Recent]
    @State private var query = ""
    @State private var searching = false
    @State private var selection: String?
    @State private var showAbout = false

    private var isBlank: Bool { query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    var body: some View {
        NavigationSplitView {
            List(selection: $selection) {
                if isBlank {
                    recentSection
                } else {
                    let results = searcher.search(query)
                    if results.isEmpty {
                        noMatches
                    } else {
                        ForEach(results) { row($0) }
                    }
                }
            }
            .navigationTitle("Tatsu")
            .searchable(text: $query, isPresented: $searching, prompt: "English, kana, romaji or kanji")
            .autocorrectionDisabled()
            #if os(iOS)
            .textInputAutocapitalization(.never)
            #endif
            // Mac / iPad hardware keyboard: typing while the list has focus goes to the search field
            .onKeyPress(characters: .alphanumerics, phases: .down) { press in
                guard !press.modifiers.contains(.command) else { return .ignored }
                query.append(press.characters)
                searching = true
                return .handled
            }
            .toolbar {
                ToolbarItem {
                    Button("About", systemImage: "info.circle") { showAbout = true }
                }
            }
            .background {
                // Cmd+F focuses search
                Button("Find") { searching = true }
                    .keyboardShortcut("f", modifiers: .command)
                    .opacity(0)
            }
        } detail: {
            if let k = selection, let e = searcher.kanji(k) {
                KanjiDetail(kanji: e) { copy(e.k) }
            } else {
                Text("Pick a kanji").foregroundStyle(.secondary)
            }
        }
        .onChange(of: selection) { _, k in
            if let k { Recent.touch(k, in: context) }
        }
        .sheet(isPresented: $showAbout) { AboutView() }
    }

    @ViewBuilder private var recentSection: some View {
        // recents whose kanji vanished after a data update are skipped
        let items = recents.compactMap { searcher.kanji($0.kanji) }
        if items.isEmpty {
            Text("Type English, hiragana, katakana, romaji, or paste kanji.")
                .foregroundStyle(.secondary)
        } else {
            Section {
                ForEach(items) { row($0) }
            } header: {
                HStack {
                    Text("Recent")
                    Spacer()
                    Button("Clear") { Recent.clear(in: context) }
                        .font(.caption)
                }
            }
        }
    }

    @ViewBuilder private var noMatches: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("No matches.")
            #if os(iOS)
            Text("Can't type it? Add the Chinese – Handwriting keyboard in Settings › General › Keyboard › Keyboards, then draw the kanji.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            #endif
        }
    }

    private func row(_ e: Kanji) -> some View {
        HStack(spacing: 12) {
            Text(e.k).font(.system(size: 40))
            VStack(alignment: .leading, spacing: 2) {
                Text(e.m.joined(separator: ", ")).lineLimit(2)
                Text((e.on + e.kun).joined(separator: "、"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .tag(e.k)
        .contextMenu {
            Button("Copy", systemImage: "doc.on.doc") { copy(e.k) }
        }
    }

    private func copy(_ k: String) {
        copyToPasteboard(k)
        Recent.touch(k, in: context)
    }
}
```

- [ ] **Step 8: Replace the app entry point**

`Tatsu/TatsuApp.swift`:

```swift
import SwiftData
import SwiftUI

@main
struct TatsuApp: App {
    #if os(macOS)
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    #endif

    var body: some Scene {
        WindowGroup {
            RootView()
        }
        .modelContainer(for: Recent.self)
        #if os(macOS)
        .defaultSize(width: 960, height: 720)
        #endif
    }
}

#if os(macOS)
final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }
}
#endif

/// Loads kanji.json off the main thread, then shows the app.
private struct RootView: View {
    @State private var searcher: Searcher?

    var body: some View {
        Group {
            if let searcher {
                ContentView(searcher: searcher)
            } else {
                ProgressView()
            }
        }
        .task {
            searcher = await Task.detached { () -> Searcher in
                do {
                    return Searcher(try Kanji.loadBundled())
                } catch {
                    // a missing or broken kanji.json is a build error, and DataTests catches it before release
                    fatalError("kanji.json failed to load: \(error)")
                }
            }.value
        }
    }
}
```

- [ ] **Step 9: Run TEST and IOS-BUILD**

Expected: both succeed. If IOS-BUILD reports that `onKeyPress` or `searchable(text:isPresented:prompt:)` is unavailable, check that the deployment targets are 17.0 / 14.0 in `project.pbxproj`, since both APIs need exactly those versions.

- [ ] **Step 10: Manual check**

Run it on Mac: `open Tatsu.xcodeproj`, choose the "My Mac" destination, and press Run. Then run it on the "iPhone 17" simulator and on an iPad simulator. On each, check:

- `water`, `watr`, `mountian`, `みず`, `ミズ`, `taberu` and `日本語` give the same top results as the old app.
- Tapping a result opens the detail view. The kanji appears under Recent after you clear the search.
- Copy puts the kanji on the clipboard and adds it to Recent.
- Clear empties Recent.
- A gibberish query shows "No matches." On iOS it also shows the handwriting hint.
- Dark mode looks correct, and rotating the iPhone and iPad keeps the layout correct.
- **Mac only:** Cmd+F focuses the search field. Esc clears it. With a result selected, typing letters goes into the search field.
- The About sheet opens and its links work.

If one of the Mac keyboard behaviours doesn't work, write down which one in the commit message. Don't block the task on it; a follow-up fixes it.

- [ ] **Step 11: Commit**

```bash
git add Tatsu TatsuTests
git commit -m "SwiftUI interface: search, detail, recent lookups, about"
```

---

### Task 5: App icon, String Catalog, privacy manifest

**Files:**
- Create: `Tatsu/Assets.xcassets/Contents.json`, `Tatsu/Assets.xcassets/AppIcon.appiconset/Contents.json` + PNGs
- Create: `Tatsu/Localizable.xcstrings`, `Tatsu/PrivacyInfo.xcprivacy`

**Interfaces:**
- Consumes: `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon` (set in Task 1) and `logo.svg`.
- Produces: store-ready resources. No code.

- [ ] **Step 1: Render the icons**

The iOS icon is full-bleed with no rounded corners and no transparency, because Apple rejects icons with an alpha channel. The Mac icon keeps the rounded logo, padded to about 80% of the canvas, as `make_app.sh` did.

```bash
SET=Tatsu/Assets.xcassets/AppIcon.appiconset
mkdir -p "$SET"
TMP=$(mktemp -d)
# iOS: square corners, flattened through JPEG to drop the alpha channel
sed 's/rx="116"/rx="0"/' logo.svg > "$TMP/ios.svg"
qlmanage -t -s 1024 -o "$TMP" "$TMP/ios.svg" >/dev/null 2>&1
sips -s format jpeg "$TMP/ios.svg.png" --out "$TMP/ios.jpg" >/dev/null
sips -s format png "$TMP/ios.jpg" --out "$SET/ios-1024.png" >/dev/null
# macOS: padded rounded logo
sed 's/viewBox="0 0 512 512"/viewBox="-62 -62 636 636"/' logo.svg > "$TMP/mac.svg"
qlmanage -t -s 1024 -o "$TMP" "$TMP/mac.svg" >/dev/null 2>&1
for s in 16 32 64 128 256 512 1024; do
  sips -z $s $s "$TMP/mac.svg.png" --out "$SET/mac-$s.png" >/dev/null
done
rm -rf "$TMP"
sips -g hasAlpha "$SET/ios-1024.png"
```

Expected: the last line prints `hasAlpha: no`. Open `ios-1024.png` and check that the 断 logo fills the square with a dark background.

- [ ] **Step 2: Write the asset catalog JSON**

`Tatsu/Assets.xcassets/Contents.json`:

```json
{ "info" : { "author" : "xcode", "version" : 1 } }
```

`Tatsu/Assets.xcassets/AppIcon.appiconset/Contents.json`:

```json
{
  "images" : [
    { "filename" : "ios-1024.png", "idiom" : "universal", "platform" : "ios", "size" : "1024x1024" },
    { "filename" : "mac-16.png", "idiom" : "mac", "scale" : "1x", "size" : "16x16" },
    { "filename" : "mac-32.png", "idiom" : "mac", "scale" : "2x", "size" : "16x16" },
    { "filename" : "mac-32.png", "idiom" : "mac", "scale" : "1x", "size" : "32x32" },
    { "filename" : "mac-64.png", "idiom" : "mac", "scale" : "2x", "size" : "32x32" },
    { "filename" : "mac-128.png", "idiom" : "mac", "scale" : "1x", "size" : "128x128" },
    { "filename" : "mac-256.png", "idiom" : "mac", "scale" : "2x", "size" : "128x128" },
    { "filename" : "mac-256.png", "idiom" : "mac", "scale" : "1x", "size" : "256x256" },
    { "filename" : "mac-512.png", "idiom" : "mac", "scale" : "2x", "size" : "256x256" },
    { "filename" : "mac-512.png", "idiom" : "mac", "scale" : "1x", "size" : "512x512" },
    { "filename" : "mac-1024.png", "idiom" : "mac", "scale" : "2x", "size" : "512x512" }
  ],
  "info" : { "author" : "xcode", "version" : 1 }
}
```

- [ ] **Step 3: Add the String Catalog and privacy manifest**

`Tatsu/Localizable.xcstrings`. It starts empty, and Xcode fills in the strings from the views on the next build:

```json
{
  "sourceLanguage" : "en",
  "strings" : {
  },
  "version" : "1.0"
}
```

`Tatsu/PrivacyInfo.xcprivacy`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>NSPrivacyTracking</key>
	<false/>
	<key>NSPrivacyTrackingDomains</key>
	<array/>
	<key>NSPrivacyCollectedDataTypes</key>
	<array/>
	<key>NSPrivacyAccessedAPITypes</key>
	<array/>
</dict>
</plist>
```

- [ ] **Step 4: Build and verify**

Run TEST and IOS-BUILD. Both should succeed with no "AppIcon" warnings. Then check that the icon made it into the built app:

```bash
xcodebuild -project Tatsu.xcodeproj -scheme Tatsu -destination 'platform=macOS' CODE_SIGNING_ALLOWED=NO -showBuildSettings | grep -m1 ' BUILT_PRODUCTS_DIR'
ls "<BUILT_PRODUCTS_DIR from above>/Tatsu.app/Contents/Resources" | grep -E 'AppIcon|Assets.car|PrivacyInfo'
```

Expected: `AppIcon.icns`, `Assets.car` and `PrivacyInfo.xcprivacy` are listed.

- [ ] **Step 5: Commit**

```bash
git add Tatsu
git commit -m "App icon, String Catalog, privacy manifest"
```

---

### Task 6: Remove the web version and update the docs

**Files:**
- Delete: `index.html`, `search.js`, `data.js`, `test.js`, `main.swift`, `make_app.sh`
- Modify: `README.md` (full rewrite), `.gitignore`

**Interfaces:**
- Consumes: the whole app from Tasks 1-5.
- Produces: a clean repo.

- [ ] **Step 1: Delete the old files**

```bash
git rm index.html search.js data.js test.js main.swift make_app.sh
```

Also remove the `Tatsu.app` line from `.gitignore`, since `make_app.sh` no longer exists. Keep `kanjidic2.xml.gz`.

- [ ] **Step 2: Check that nothing references the deleted files**

```bash
grep -rn --exclude-dir=.git --exclude-dir=docs -E 'data\.js|search\.js|index\.html|make_app|main\.swift|test\.js' . || echo clean
```

Expected: `clean`. `README.md` is rewritten in the next step, so if it's the only match, go ahead.

- [ ] **Step 3: Rewrite `README.md`**

````markdown
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
````

- [ ] **Step 4: Final verification**

Run TEST and IOS-BUILD. Both must succeed. Then run the grep from Step 2 again. Expected: `clean`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Remove web version; README for the SwiftUI app"
git log --format='%an <%ae>' | sort -u
```

Expected: the last command prints only `Abbabon <1280330+Abbabon@users.noreply.github.com>`.

---

## After the plan (not tasks: needs the Apple account, done by hand)

1. In Xcode → Tatsu target → Signing & Capabilities, choose your team.
2. In App Store Connect, create the app record for iOS and macOS with bundle ID `com.abbabon.tatsu` and name "Tatsu – Offline Kanji". If that name is taken, pick a new one (see the spec).
3. Archive, upload to TestFlight for both platforms, and test on a real device.
4. Submit.
5. Merge `swiftui` into `main` with superpowers:finishing-a-development-branch.

Follow-ups recorded in the spec's roadmap: action extension (1.1), camera (1.2), stroke order, words, radicals and handwriting packs.
