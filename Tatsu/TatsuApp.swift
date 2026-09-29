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
    func applicationWillFinishLaunching(_ notification: Notification) {
        // Hide the Dock icon and prevent focus steal when running under XCTest.
        if ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil {
            NSApp.setActivationPolicy(.prohibited)
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
