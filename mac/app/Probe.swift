import AppKit
import os
import WebKit

// What the checks of this folder ask of the app from outside, through its environment, as
// ios/app/Probe.swift does in a simulator. Only an app that mac/build.sh built to be looked
// into has any of it.
enum Probe {
    private static func asked(_ name: String) -> String? {
        #if PROBED
        ProcessInfo.processInfo.environment["GATES_" + name]
        #else
        nil
        #endif
    }

    // Safari can look into the pages.
    static var inspectable: Bool {
        #if PROBED
        true
        #else
        false
        #endif
    }

    // Nothing stands in for iCloud on a Mac.
    static let elsewhere: Elsewhere? = nil

    // Another page to open than the Birth Chart.
    static var start: String? {
        asked("START")
    }

    // Keeps the welcome of the pages away, which would cover what a check looks at, unless
    // GATES_WELCOME asks for it.
    static func listen(to content: WKUserContentController) {
        #if PROBED
        if asked("WELCOME") == nil {
            content.addUserScript(WKUserScript(source: "try { localStorage.setItem('hdapp-welcomed', '1') } catch (e) {}", injectionTime: .atDocumentStart, forMainFrameOnly: true))
        }
        #endif
    }

    // A few seconds after a page loaded: runs the script of GATES_EVAL in it and prints what it
    // returns, for GATES_SHOT writes a picture of what the window shows where the app may write and prints
    // where, and ends the app. GATES_SIZE is the size of what the window shows for that, as
    // 1000x1100, which isn't kept for the next start.
    static func loaded(_ view: WKWebView) {
        guard asked("EVAL") != nil || asked("SHOT") != nil else {
            return
        }
        if let sides = asked("SIZE")?.split(separator: "x").compactMap({ Double($0) }), sides.count == 2, let window = view.window {
            window.setFrameAutosaveName("")
            window.setContentSize(NSSize(width: sides[0], height: sides[1]))
        }
        let film = filmed(view)
        DispatchQueue.main.asyncAfter(deadline: .now() + 4) {
            view.callAsyncJavaScript(asked("EVAL") ?? "return ''", in: nil, in: .page) { result in
                print("probe \(result)")
                if let film {
                    print("film \(film.path)")
                }
                guard asked("SHOT") != nil else {
                    exit(0)
                }
                // what the web view shows, which is drawn apart from the window
                view.takeSnapshot(with: nil) { picture, _ in
                    if let shown = picture?.tiffRepresentation.flatMap(NSBitmapImageRep.init(data:))?.representation(using: .png, properties: [:]) {
                        let file = FileManager.default.temporaryDirectory.appendingPathComponent("window.png")
                        try? shown.write(to: file)
                        print("shot \(file.path)")
                    }
                    exit(0)
                }
            }
        }
    }

    // For GATES_FILM, pictures of what the window shows, one after the other as fast as they
    // come, each named after the thousandths of a second since the first: a recording of the
    // app that needs no leave to record the screen. Answers the folder that they're written to.
    private static func filmed(_ view: WKWebView) -> URL? {
        guard let rest = asked("FILM").flatMap({ Double($0) }).map({ $0 / 1000 }) else {
            return nil
        }
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("film")
        try? FileManager.default.removeItem(at: folder)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let began = Date()
        let writing = DispatchQueue(label: "film")
        func next() {
            // half of the points of the window, which is as many dots as the window has points
            let half = WKSnapshotConfiguration()
            half.snapshotWidth = NSNumber(value: Double(view.bounds.width) / 2)
            view.takeSnapshot(with: half) { picture, _ in
                let at = Int(Date().timeIntervalSince(began) * 1000)
                // written apart from what the app does meanwhile, which answers the pages
                writing.async {
                    if let shown = picture?.tiffRepresentation.flatMap(NSBitmapImageRep.init(data:))?.representation(using: .jpeg, properties: [.compressionFactor: 0.92]) {
                        try? shown.write(to: folder.appendingPathComponent(String(format: "%08d.jpg", at)))
                    }
                }
                // A picture takes the page a while, during which nothing else happens in it, so
                // GATES_FILM says how many thousandths of a second it's left alone after each.
                DispatchQueue.main.asyncAfter(deadline: .now() + rest, execute: next)
            }
        }
        next()
        return folder
    }
}
