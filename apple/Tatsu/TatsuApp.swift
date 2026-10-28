import SwiftData
import SwiftUI

@main
struct TatsuApp: App {
    #if os(macOS)
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    #endif

    /// `-TatsuScreenshots` (UI tests only): throwaway in-memory Recent store, fixed window size.
    private let screenshotMode = ProcessInfo.processInfo.arguments.contains("-TatsuScreenshots")

    var body: some Scene {
        WindowGroup {
            RootView()
        }
        .modelContainer(for: Recent.self, inMemory: screenshotMode)
        #if os(macOS)
        // 1440x900 is the Mac App Store screenshot size at Retina; the UI-test-only flag leaves normal launches alone
        .defaultSize(width: screenshotMode ? 1440 : 960, height: screenshotMode ? 900 : 720)
        #endif
    }
}

#if os(macOS)
final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationWillFinishLaunching(_ notification: Notification) {
        // Hide the Dock icon and prevent focus steal when running under XCTest.
        if ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil,
           !ProcessInfo.processInfo.arguments.contains("-TatsuScreenshots") {
            NSApp.setActivationPolicy(.prohibited)
        }
        // `-TatsuScreenshots -TatsuAppearance dark|light` forces the appearance for App Store shots
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-TatsuScreenshots") { NSApp.setActivationPolicy(.regular); NSApp.activate(ignoringOtherApps: true) }
        if args.contains("-TatsuScreenshots"), let i = args.firstIndex(of: "-TatsuAppearance"), i + 1 < args.count {
            NSApp.appearance = NSAppearance(named: args[i + 1] == "dark" ? .darkAqua : .aqua)
        }
    }

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
