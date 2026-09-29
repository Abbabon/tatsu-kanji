import XCTest

/// Tapping anywhere on a sidebar row must open its detail (pushes on iPhone) and record it.
final class RowTapUITests: XCTestCase {
    private let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
        app.launch()
    }

    private func searchAndTap(dx: CGFloat) {
        let field = app.searchFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.tap()
        field.typeText("water")
        let cell = app.cells.containing(.staticText, identifier: "水").firstMatch
        XCTAssertTrue(cell.waitForExistence(timeout: 10))
        cell.coordinate(withNormalizedOffset: CGVector(dx: dx, dy: 0.5)).tap()
        XCTAssertTrue(app.buttons["Copy"].waitForExistence(timeout: 5), "detail didn't open for tap at dx \(dx)")
    }

    func testTapTrailingEmptySpaceOpensDetail() { searchAndTap(dx: 0.9) }
    func testTapNearLeadingEdgeOpensDetail() { searchAndTap(dx: 0.02) }
    func testTapTextOpensDetail() { searchAndTap(dx: 0.3) }

    func testTapRecordsHistory() {
        // iOS shows section headers uppercased
        let clear = app.buttons.matching(NSPredicate(format: "label ==[c] 'clear'")).firstMatch
        if clear.waitForExistence(timeout: 3) { clear.tap() }
        searchAndTap(dx: 0.9)
        app.navigationBars.buttons.firstMatch.tap()  // back to the sidebar
        app.buttons["Cancel"].tap()                  // clear the query: Recent shows
        XCTAssertTrue(clear.waitForExistence(timeout: 5))
        let rows = app.cells.containing(.staticText, identifier: "水")
        XCTAssertEqual(rows.count, 1)
        XCTAssertEqual(app.cells.count, 2)  // header + the one recorded row
    }
}
