import RIFE2NativeApp
import SwiftUI
import WebKit

// What a tapped part of a chart explains, in a panel beside the window. The page of the chart
// fills the card that it has for it without showing it, and says what the card holds whenever
// that changes, see app.js of the site. The panel is a page of the site too, which is all card
// and shows what it's given, and a button that's pressed there is pressed in the card.
final class Explained: NativePages {
    @Published private(set) var shown = false
    weak var pages: Pages?
    // Hears that what the card holds changed.
    var changed: () -> Void = {}
    // What the card holds, which is nothing when it's closed.
    private var holds = ""
    private var loaded = false

    init() {
        super.init(start: "/panel", site: Site.shared, background: .clear, inspectable: Probe.inspectable)
        // there before it's shown
        _ = view
    }

    override func configure(_ configuration: WKWebViewConfiguration) {
        listen("pressed") { [weak self] place in
            if let place = place as? Int {
                self?.pages?.call("appPressed", place)
            }
        }
    }

    func close() {
        pages?.press("#explore-card-close")
    }

    private func show() {
        guard loaded else {
            return
        }
        if !holds.isEmpty {
            call("appShown", holds) { _, _ in
                #if targetEnvironment(simulator)
                // for vision/check.sh: what the panel has, as text, also before it's on the screen
                self.view.evaluateJavaScript("document.getElementById('explore-card-content').textContent.replace(/\\s+/g, ' ').trim().slice(0, 80)") { text, _ in
                    print("explained \(text as? String ?? "")")
                }
                #endif
            }
        }
        #if targetEnvironment(simulator)
        if shown, holds.isEmpty {
            print("explained nothing")
        }
        #endif
        shown = !holds.isEmpty
    }

    // The pages say what their card holds.
    func heard(_ holds: Any) {
        self.holds = holds as? String ?? ""
        show()
        changed()
    }

    // Also when the panel is loaded again after visionOS stopped what draws it.
    override func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        super.webView(webView, didFinish: navigation)
        loaded = true
        show()
    }

    // A link in the panel to a page of the site leads there in the window.
    override func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction) async -> WKNavigationActionPolicy {
        if action.navigationType == .linkActivated, let url = action.request.url, own(url) {
            pages?.open(url)
            return .cancel
        }
        return await super.webView(webView, decidePolicyFor: action)
    }
}
