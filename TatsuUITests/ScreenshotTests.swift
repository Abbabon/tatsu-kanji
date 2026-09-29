import XCTest

/// App Store screenshot driver. Skipped unless TATSU_SCREENSHOTS=1 (pass as TEST_RUNNER_TATSU_SCREENSHOTS=1).
/// Writes PNGs to $TATSU_SCREENSHOT_DIR named "<n>-<name>-<TATSU_APPEARANCE>.png"; see docs/appstore/screenshots/.
final class ScreenshotTests: XCTestCase {
    private let app = XCUIApplication()
    private let env = ProcessInfo.processInfo.environment
    private var isPad: Bool { UIDevice.current.userInterfaceIdiom == .pad }

    private func shot(_ n: Int, _ name: String) throws {
        Thread.sleep(forTimeInterval: 2)  // let sheet/sidebar animations settle
        let dir = env["TATSU_SCREENSHOT_DIR"] ?? "/tmp"
        let prefix = isPad ? "ipad" : "iphone"
        let url = URL(fileURLWithPath: dir).appendingPathComponent("\(prefix)-\(n)-\(name)-\(env["TATSU_APPEARANCE"] ?? "light").png")
        try XCUIScreen.main.screenshot().pngRepresentation.write(to: url)
    }

    private func search(_ q: String) {
        let field = app.searchFields.firstMatch
        // iPad portrait starts with the sidebar hidden
        if isPad, !field.waitForExistence(timeout: 3) { openSidebar() }
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.tap()
        field.typeText(q)
    }

    private func openSidebar() {
        let toggle = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'sidebar'")).firstMatch
        XCTAssertTrue(toggle.waitForExistence(timeout: 5), app.debugDescription)
        toggle.tap()
    }

    private func firstResult() -> XCUIElement {
        let cell = app.cells.containing(.staticText, identifier: "水").firstMatch
        XCTAssertTrue(cell.waitForExistence(timeout: 10))
        return cell
    }

    func testScreenshots() throws {
        try XCTSkipUnless(env["TATSU_SCREENSHOTS"] == "1", "set TEST_RUNNER_TATSU_SCREENSHOTS=1")
        continueAfterFailure = false
        app.launch()

        let clear = app.buttons.matching(NSPredicate(format: "label ==[c] 'clear'")).firstMatch
        if clear.waitForExistence(timeout: 3) { clear.tap() }

        // Fill Recent (oldest first; 水 ends up on top).
        for (q, g) in [("flower", "花"), ("language", "語"), ("eat", "食"), ("sun", "日"), ("mountain", "山"), ("water", "水")] {
            search(q)
            let cell = app.cells.containing(.staticText, identifier: g).firstMatch
            XCTAssertTrue(cell.waitForExistence(timeout: 10))
            cell.tap()
            XCTAssertTrue(app.buttons["Copy"].waitForExistence(timeout: 5))
            if !isPad { app.navigationBars.buttons.firstMatch.tap() }
            app.buttons["Cancel"].tap()
        }

        // 1: results for "water", keyboard dismissed.
        search("water")
        _ = firstResult()
        if isPad {
            let hide = app.keyboards.buttons["Hide keyboard"]
            if hide.exists { hide.tap() }
        } else if app.keyboards.buttons["Search"].exists {
            // The Search key opens the first result; going back leaves results without the keyboard.
            app.keyboards.buttons["Search"].tap()
            XCTAssertTrue(app.buttons["Copy"].waitForExistence(timeout: 5))
            app.navigationBars.buttons.firstMatch.tap()
            _ = firstResult()
        }
        try shot(1, "search")

        // 2: detail (iPad: close the sidebar overlay by tapping the detail pane)
        firstResult().tap()
        XCTAssertTrue(app.buttons["Copy"].waitForExistence(timeout: 5))
        if isPad, app.searchFields.firstMatch.exists {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.6)).tap()
            XCTAssertTrue(app.searchFields.firstMatch.waitForNonExistence(timeout: 5))
        }
        try shot(2, "detail")

        // 3: Recent with empty search
        if isPad { openSidebar() } else { app.navigationBars.buttons.firstMatch.tap() }
        app.buttons["Cancel"].tap()
        XCTAssertTrue(clear.waitForExistence(timeout: 5))
        try shot(3, "recent")
    }
}
