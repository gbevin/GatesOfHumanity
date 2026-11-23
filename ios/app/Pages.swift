import RIFE2NativeApp
import SwiftUI
import WebKit

// The pages of the site in the web view that RIFE2 has for them, see NativePages, with what's
// this app's own: what the pages ask of it beyond what every app does, see app.js of the site,
// what the menus and the keys of a keyboard ask of the pages, see Menus, and a widget that was
// tapped. This is the same for every system of Apple: what one of them does in its own way is
// in the Platform.swift of its app.
final class Pages: NativePages {
    private let people = PeopleKept(elsewhere: Probe.elsewhere ?? ICloud())

    init() {
        super.init(start: Probe.start ?? "/wheel/birth", site: Site.shared, background: .edge, inspectable: Probe.inspectable)
        people.pages = self
    }

    // Whether the page is one of a chart or of the transits, which have the dates, the people
    // and the chart that the menus are about.
    var charted: Bool {
        path.hasPrefix("/wheel") || path.hasPrefix("/range")
    }

    override func configure(_ configuration: WKWebViewConfiguration) {
        listen("people", people.saved)
        listen("elsewhere", people.asked)
        Probe.listen(to: configuration.userContentController)
    }

    override func prepare(_ view: WKWebView) {
        prepared(view)
    }

    override func activated() {
        people.again()
    }

    // The button of a page that shows its chart as large as the window, or the one that closes
    // that again while it's so.
    static let enlarge = "body:has(#chart-viewer[hidden]) #chart-zoom, #chart-viewer:not([hidden]) #chart-viewer-close"

    // The buttons of a page that load and save a person: the one of its first person, which the
    // charts of a connection and its transits have after another name than the other charts.
    static let load = "#action-load, #action-load-second"
    static let save = "#action-save, #action-save-second"

    // Shows the chart of a day, an hour or a minute later or earlier, with the page's own button.
    func step(_ unit: String, later: Bool) {
        press("#\(unit)-\(later ? "plus" : "min")")
    }

    // The link that the page shares, for wherever it's pasted.
    func copyLink() {
        call("shareUrl") { [weak self] _, link in
            if let link = link as? String, !link.isEmpty {
                self?.copy(link)
            }
        }
    }

    // Shows what a widget was tapped for, when it's an address of the site. One that names a
    // person is a chart for someone the pages saved: the page that's shown remembers them as
    // the People page does for one of its charts, and goes there.
    func show(_ url: URL) {
        guard var parts = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let person = parts.queryItems?.first(where: { $0.name == "person" })?.value else {
            open(url)
            return
        }
        parts.queryItems = nil
        guard let chart = parts.url, own(chart) else {
            return
        }
        whenLoaded { [weak self] in
            self?.call("showPersonChart", chart.path, person) { called, _ in
                if !called {
                    self?.open(chart)
                }
            }
        }
    }

    override func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        super.webView(webView, didFinish: navigation)
        Probe.loaded(webView)
    }
}
