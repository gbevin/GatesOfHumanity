import SwiftUI
import WebKit

// What the pages get of a Mac in its own way, beside what Pages does on every system of Apple.
// Leaving the site, printing and what a page saves are RIFE2's to do, see NativePages.

extension NSColor {
    // The color at the top of the title bar of the pages, which the window has around them.
    static let edge = NSColor(red: 33 / 255, green: 33 / 255, blue: 78 / 255, alpha: 1)
}

extension Pages {
    func prepared(_ view: WKWebView) {
        // as large as it was left
        view.pageZoom = UserDefaults.standard.object(forKey: "zoom") as? Double ?? 1
    }

    func zoom(to size: Double) {
        view.pageZoom = min(max(size, 0.5), 3)
        UserDefaults.standard.set(view.pageZoom, forKey: "zoom")
    }

    func copy(_ text: String) {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(text, forType: .string)
    }
}
