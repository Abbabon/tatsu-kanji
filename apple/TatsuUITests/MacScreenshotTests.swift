#if os(macOS)
import XCTest

/// Mac App Store screenshots. Skipped unless TATSU_SCREENSHOTS=1 (pass as TEST_RUNNER_TATSU_SCREENSHOTS=1).
/// Writes mac-<n>-<name>-<light|dark>.png to the runner's temp dir and prints "SCREENSHOT <path>". The app runs with -TatsuScreenshots
/// (in-memory Recent store, 1440x900pt window), so the real history is untouched.
final class MacScreenshotTests: XCTestCase {
    private let env = ProcessInfo.processInfo.environment

    func testLight() throws { try run("light") }
    func testDark() throws { try run("dark") }

    private func run(_ mode: String) throws {
        try XCTSkipUnless(env["TATSU_SCREENSHOTS"] == "1", "set TEST_RUNNER_TATSU_SCREENSHOTS=1")
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-TatsuScreenshots", "-TatsuAppearance", mode, "-ApplePersistenceIgnoreState", "YES"]
        app.launch()
        app.activate()
        let window = app.windows.firstMatch
        if !window.waitForExistence(timeout: 5) { app.menuBars.menuBarItems["File"].click(); app.menuBars.menuItems["New Window"].click() }
        XCTAssertTrue(window.waitForExistence(timeout: 15), app.debugDescription)

        func shot(_ n: Int, _ name: String) throws {
            Thread.sleep(forTimeInterval: 1.5)
            // the sandboxed runner can only write to its own temp dir; the caller copies the PNGs from the printed path
            let dir = NSTemporaryDirectory()
            let url = URL(fileURLWithPath: dir).appendingPathComponent("mac-\(n)-\(name)-\(mode).png")
            try window.screenshot().pngRepresentation.write(to: url)
            print("SCREENSHOT \(url.path)")
        }
        func search(_ q: String) {
            let field = app.searchFields.firstMatch
            XCTAssertTrue(field.waitForExistence(timeout: 10))
            field.click()
            field.typeKey("a", modifierFlags: .command)
            field.typeText(q)
        }
        func row(_ glyph: String) -> XCUIElement {
            let r = app.staticTexts[glyph].firstMatch
            XCTAssertTrue(r.waitForExistence(timeout: 10), app.debugDescription)
            return r
        }

        // Fill Recent (oldest first; 水 ends up on top).
        for (q, g) in [("flower", "花"), ("language", "語"), ("eat", "食"), ("sun", "日"), ("mountain", "山"), ("water", "水")] {
            search(q)
            row(g).click()
            XCTAssertTrue(app.buttons["Copy"].waitForExistence(timeout: 5))
        }

        search("water")
        _ = row("水")
        try shot(1, "search")

        row("水").click()
        XCTAssertTrue(app.buttons["Copy"].waitForExistence(timeout: 5))
        try shot(2, "detail")

        let field = app.searchFields.firstMatch
        field.click()
        field.typeKey("a", modifierFlags: .command)
        field.typeKey(.delete, modifierFlags: [])
        XCTAssertTrue(app.staticTexts["Recent, Clear"].waitForExistence(timeout: 5))
        try shot(3, "recent")
    }
}
#endif
