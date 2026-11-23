import XCTest

/// Drags a finger over the app that runs in the simulator, which nothing else can from a
/// command line. What the page did under it is the page's to tell, see check-feel.sh.
final class Feel: XCTestCase {
    func testDrags() {
        let app = XCUIApplication(bundleIdentifier: "com.uwyn.gatesofhumanity")
        app.activate()
        // the page says when its script is there to keep where it goes
        XCTAssertTrue(app.buttons["Ready for a finger"].waitForExistence(timeout: 90), "the page isn't ready")
        func point(_ height: Double) -> XCUICoordinate {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: height))
        }
        // each as "from to speed hold": heights as parts of the screen, points a second, seconds held at the end
        for drag in (ProcessInfo.processInfo.environment["FEEL_DRAGS"] ?? "").split(separator: ",") {
            let parts = drag.split(separator: " ").compactMap { Double($0) }
            guard parts.count == 4 else {
                XCTFail("not a drag: \(drag)")
                return
            }
            point(parts[0]).press(forDuration: 0.05, thenDragTo: point(parts[1]), withVelocity: XCUIGestureVelocity(parts[2]), thenHoldForDuration: parts[3])
            Thread.sleep(forTimeInterval: 3.5)
        }
    }

    // The status bar is iOS's and not the app's, so it's tapped where it is on the screen.
    func testStatusBar() {
        let app = XCUIApplication(bundleIdentifier: "com.uwyn.gatesofhumanity")
        app.activate()
        XCTAssertTrue(app.buttons["Ready for the status bar"].waitForExistence(timeout: 90), "the page isn't scrolled down")
        // on the time, beside what a phone has in the middle of its status bar
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.15, dy: 0)).withOffset(CGVector(dx: 0, dy: 12)).tap()
        Thread.sleep(forTimeInterval: 3)
    }

    // The title in the bar of the page, which is the page's own to go back up from.
    func testTitle() {
        let app = XCUIApplication(bundleIdentifier: "com.uwyn.gatesofhumanity")
        app.activate()
        XCTAssertTrue(app.buttons["Ready for the title"].waitForExistence(timeout: 90), "the page isn't scrolled down again")
        let title = app.staticTexts.matching(NSPredicate(format: "label CONTAINS[c] %@", "Gates of Humanity")).firstMatch
        XCTAssertTrue(title.exists, "the bar has no title to tap")
        title.tap()
        Thread.sleep(forTimeInterval: 3)
    }
}
