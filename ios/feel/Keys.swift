import XCTest

/// Presses keys of a keyboard that's connected, which nothing else can from a command line.
/// What the page did with them is the page's to tell, see check-keys.sh.
final class Keys: XCTestCase {
    func testKeys() {
        let app = XCUIApplication(bundleIdentifier: "com.uwyn.gatesofhumanity")
        app.activate()
        XCTAssertTrue(app.buttons["Ready for keys"].waitForExistence(timeout: 90), "the page isn't ready")
        // A page that was touched has the keys first, and the app only gets what the page leaves:
        // it scrolls with an arrow that has one key with it, so the steps have two.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.6)).tap()
        Thread.sleep(forTimeInterval: 1)
        // a day later, then the third of the charts
        app.typeKey(.rightArrow, modifierFlags: [.command, .shift])
        Thread.sleep(forTimeInterval: 4)
        app.typeKey("3", modifierFlags: .command)
        // the page that opens tells where it is
        Thread.sleep(forTimeInterval: 8)
    }
}
