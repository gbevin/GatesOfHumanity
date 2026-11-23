import SwiftUI
import WebKit

// What the pages get of iOS in its own way, beside what Pages does on every system of Apple.
// Leaving the site, printing and what a page saves are RIFE2's to do, see NativePages.

extension UIColor {
    #if os(visionOS)
    // A window of visionOS is glass, which shows around what a page has on it.
    static let edge = UIColor.clear
    #else
    // The color at the top of the title bar of the pages and at the bottom of their footer, which
    // goes on above and below them: behind the status bar and before the first page is there.
    static let edge = UIColor(red: 33 / 255, green: 33 / 255, blue: 78 / 255, alpha: 1)
    #endif
}

extension Pages: UIScrollViewDelegate {
    func prepared(_ view: WKWebView) {
        // The window stays where it is: pulled, it looks like a web page that's about to reload,
        // with its title bar moving away. What's pulled is the page inside it.
        view.scrollView.bounces = false
        // iOS asks a view that scrolls to go to its top after a tap on the status bar, though not
        // one that's there already or that can't be touched. The window is always at its top, so a
        // view of one point that stays scrolled down is asked in its place, behind the page where
        // no finger reaches it.
        view.scrollView.scrollsToTop = false
        let asked = UIScrollView(frame: CGRect(x: 0, y: 0, width: 0, height: 1))
        asked.autoresizingMask = .flexibleWidth
        asked.contentInsetAdjustmentBehavior = .never
        asked.contentSize.height = 2
        asked.contentOffset.y = 1
        asked.delegate = self
        view.insertSubview(asked, at: 0)
    }

    // After a tap on the status bar the page goes to its top, and the view that was asked stays.
    func scrollViewShouldScrollToTop(_ scrollView: UIScrollView) -> Bool {
        call("document.body.scrollTo", ["top": 0, "behavior": "smooth"])
        return false
    }

    func copy(_ text: String) {
        UIPasteboard.general.string = text
    }
}
