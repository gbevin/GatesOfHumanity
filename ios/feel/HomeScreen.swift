import XCTest

/// Puts widgets of the app on the Home Screen of a simulator, as a finger would, for the
/// screenshots and the previews of the store. See Store/screenshots.py.
final class HomeScreen: XCTestCase {
    let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    override func setUp() {
        continueAfterFailure = false
    }

    /// Has the Home Screen let its icons be changed, with a held press where no icon is: under the
    /// dock of a phone, whose page can be full, or in the room that a page has left.
    private func change() {
        if springboard.buttons["Edit"].exists {
            return
        }
        for spot in [CGVector(dx: 0.5, dy: 0.985), CGVector(dx: 0.5, dy: 0.72), CGVector(dx: 0.5, dy: 0.5), CGVector(dx: 0.985, dy: 0.5)] {
            springboard.coordinate(withNormalizedOffset: spot).press(forDuration: 1.8)
            if springboard.buttons["Edit"].waitForExistence(timeout: 2.5) {
                return
            }
            // whatever that opened instead
            XCUIDevice.shared.press(.home)
            Thread.sleep(forTimeInterval: 1.5)
        }
        print("TREE of a Home Screen that can't be changed\n" + springboard.debugDescription)
        XCTFail("the Home Screen can't be changed")
    }

    /// Adds the widgets that FEEL_WIDGETS names to the page of the Home Screen that's shown: each
    /// the place of a widget among those that the app offers, counted from zero, in the order
    /// that the bundle of its widgets lists them and their sizes. The last one added is at the top.
    func testAddWidgets() {
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 1.5)
        for place in (ProcessInfo.processInfo.environment["FEEL_WIDGETS"] ?? "").split(separator: ",").compactMap({ Int($0) }) {
            change()
            springboard.buttons["Edit"].tap()
            XCTAssertTrue(springboard.buttons["Add Widget"].waitForExistence(timeout: 5))
            springboard.buttons["Add Widget"].tap()
            let search = springboard.searchFields["Search Widgets"]
            XCTAssertTrue(search.waitForExistence(timeout: 8))
            search.tap()
            // a simulator that's new tells how to type first
            if springboard.buttons["Continue"].waitForExistence(timeout: 1.5) {
                springboard.buttons["Continue"].tap()
            }
            search.typeText("Gates")
            let app = springboard.cells.matching(NSPredicate(format: "label BEGINSWITH 'Gates'")).firstMatch
            if !app.waitForExistence(timeout: 8) {
                print("TREE of the widgets found\n" + springboard.debugDescription)
                XCTFail("the app has no widgets here")
            }
            app.tap()
            let add = springboard.buttons.matching(NSPredicate(format: "label CONTAINS 'Add Widget'")).firstMatch
            if !add.waitForExistence(timeout: 8) {
                print("TREE of the widgets of the app\n" + springboard.debugDescription)
                XCTFail("nothing adds a widget")
            }
            for _ in 0..<place {
                springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.45)).press(forDuration: 0.05, thenDragTo: springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.2, dy: 0.45)), withVelocity: .fast, thenHoldForDuration: 0)
                Thread.sleep(forTimeInterval: 0.8)
            }
            if ProcessInfo.processInfo.environment["FEEL_TREE"] != nil {
                print("TREE of the widget to add\n" + springboard.debugDescription)
            }
            add.tap()
            Thread.sleep(forTimeInterval: 2)
        }
        if springboard.buttons["Done"].exists {
            springboard.buttons["Done"].tap()
        }
        Thread.sleep(forTimeInterval: 1.5)
        XCUIDevice.shared.press(.home)
    }

    /// Takes the widgets of the app off the Home Screen again, or with FEEL_ALL every widget and
    /// icon of the page that's shown, but for the dock: iOS puts widgets with the news of the day
    /// on a new Home Screen, and icons beside the widgets of the app make a page of them untidy.
    func testRemoveWidgets() {
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 1.5)
        let all = ProcessInfo.processInfo.environment["FEEL_ALL"] != nil
        let widgets = springboard.icons.matching(NSPredicate(format: all ? "value BEGINSWITH 'Widget'" : "identifier == 'Gates' AND value BEGINSWITH 'Widget'"))
        for _ in 0..<12 {
            guard widgets.firstMatch.exists else {
                break
            }
            change()
            widgets.firstMatch.buttons["DeleteButton"].tap()
            let remove = springboard.buttons["Remove"]
            XCTAssertTrue(remove.waitForExistence(timeout: 5))
            remove.tap()
            Thread.sleep(forTimeInterval: 1.5)
        }
        if all {
            let dock = springboard.otherElements["Dock"]
            for _ in 0..<40 {
                change()
                // of this page, where a finger can reach them, and not of the dock
                let loose = springboard.icons.allElementsBoundByIndex.first { icon in
                    icon.isHittable && !dock.frame.contains(icon.frame) && icon.buttons["DeleteButton"].exists
                }
                guard let icon = loose else {
                    break
                }
                icon.buttons["DeleteButton"].tap()
                let remove = springboard.buttons["Remove from Home Screen"]
                if remove.waitForExistence(timeout: 4) {
                    remove.tap()
                } else if springboard.buttons["Remove"].exists {
                    springboard.buttons["Remove"].tap()
                }
                Thread.sleep(forTimeInterval: 1.2)
            }
        }
        if springboard.buttons["Done"].exists {
            springboard.buttons["Done"].tap()
        }
        XCTAssertFalse(widgets.firstMatch.exists)
    }

    /// Gives the Home Screen one color as its wallpaper, FEEL_COLOR as six hex digits. The Settings
    /// of a simulator have nothing for a wallpaper, the Home Screen itself has, and a photo that's
    /// chosen there comes out black in a simulator.
    func testWallpaper() {
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 1.5)
        change()
        springboard.buttons["Edit"].tap()
        XCTAssertTrue(springboard.buttons["Edit Wallpaper"].waitForExistence(timeout: 5))
        springboard.buttons["Edit Wallpaper"].tap()
        let color = springboard.buttons["Color"]
        XCTAssertTrue(color.waitForExistence(timeout: 8))
        color.tap()
        // once more for the colors to choose from, when it wasn't the choice already
        if !springboard.buttons["Custom Color"].waitForExistence(timeout: 2.5) {
            color.tap()
        }
        XCTAssertTrue(springboard.buttons["Custom Color"].waitForExistence(timeout: 5))
        springboard.buttons["Custom Color"].tap()
        XCTAssertTrue(springboard.buttons["Sliders"].waitForExistence(timeout: 5))
        springboard.buttons["Sliders"].tap()
        // the one field that isn't of a slider has the digits
        let digits = springboard.textFields.matching(NSPredicate(format: "identifier == ''")).firstMatch
        XCTAssertTrue(digits.waitForExistence(timeout: 5))
        digits.tap()
        if springboard.buttons["Continue"].waitForExistence(timeout: 1.5) {
            springboard.buttons["Continue"].tap()
        }
        digits.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 8) + (ProcessInfo.processInfo.environment["FEEL_COLOR"] ?? "111133") + "\n")
        Thread.sleep(forTimeInterval: 1.5)
        // a phone has what closes the colors, a tablet shows them beside what was tapped
        if springboard.buttons["close"].exists {
            springboard.buttons["close"].tap()
        }
        // what's still in front of what ends this is put away by a tap beside it
        let done = springboard.buttons["posterboard-done-button"]
        for _ in 0..<4 where !(done.waitForExistence(timeout: 2) && done.isHittable) {
            springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.04, dy: 0.45)).tap()
        }
        XCTAssertTrue(done.waitForExistence(timeout: 8))
        done.tap()
        Thread.sleep(forTimeInterval: 3)
        XCUIDevice.shared.press(.home)
    }

    /// Has the Home Screen show its icons and widgets large, which is without their names under them.
    func testWithoutNames() {
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 1.5)
        change()
        springboard.buttons["Edit"].tap()
        XCTAssertTrue(springboard.buttons["Customize"].waitForExistence(timeout: 5))
        springboard.buttons["Customize"].tap()
        Thread.sleep(forTimeInterval: 2)
        if ProcessInfo.processInfo.environment["FEEL_TREE"] != nil {
            print("TREE of Customize\n" + springboard.debugDescription)
        }
        // what's offered is the other size than the icons have
        let large = springboard.buttons["Large app icons"]
        if large.waitForExistence(timeout: 3) {
            large.tap()
            Thread.sleep(forTimeInterval: 1)
        } else {
            XCTAssertTrue(springboard.buttons["Regular app icons"].exists, "nothing sets the size of the icons")
        }
        // a tap beside what was offered puts it away
        springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2)).tap()
        Thread.sleep(forTimeInterval: 1.5)
        if springboard.buttons["Done"].exists {
            springboard.buttons["Done"].tap()
        }
        XCUIDevice.shared.press(.home)
    }

    /// What the Home Screen shows, for when iOS changes the names of what's tapped here.
    func testTree() {
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 1.5)
        springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.66)).press(forDuration: 1.8)
        Thread.sleep(forTimeInterval: 1.5)
        print("TREE after a held press\n" + springboard.debugDescription)
        springboard.buttons["Edit"].tap()
        Thread.sleep(forTimeInterval: 1.5)
        print("TREE after Edit\n" + springboard.debugDescription)
        let add = springboard.buttons["Add Widget"]
        if add.waitForExistence(timeout: 3) {
            add.tap()
            Thread.sleep(forTimeInterval: 2.5)
            print("TREE after Add Widget\n" + springboard.debugDescription)
            let search = springboard.searchFields.firstMatch
            if search.waitForExistence(timeout: 3) {
                search.tap()
                search.typeText("Gates")
                Thread.sleep(forTimeInterval: 2.5)
                print("TREE after a search\n" + springboard.debugDescription)
            }
        }
    }
}
