import os
import RIFE2NativeApp
import WebKit

// What the checks and the store scripts of this folder ask of the app from outside, through
// its environment, see the README. Only a simulator has any of it: nothing sets the environment
// of an app on a device, and none of it belongs in the app that people use.
enum Probe {
    private static func asked(_ name: String) -> String? {
        #if targetEnvironment(simulator)
        ProcessInfo.processInfo.environment["GATES_" + name]
        #else
        nil
        #endif
    }

    // Safari on a Mac can look into the pages.
    static var inspectable: Bool {
        #if targetEnvironment(simulator) || DEBUG
        true
        #else
        false
        #endif
    }

    #if targetEnvironment(simulator)
    // What stands in for iCloud in a check, with the people that another device saved there.
    private final class Stood: Elsewhere {
        let most = 1000
        var there: [String: Person]

        init(_ written: String) {
            there = (try? JSONDecoder().decode([String: Person].self, from: Data(written.utf8))) ?? [:]
        }

        func people() -> [String: Person] {
            there
        }

        func keep(_ person: Person, named name: String) {
            there[name] = person
        }

        func remove(_ name: String) {
            there[name] = nil
        }
    }

    private static let stood = asked("ICLOUD").map(Stood.init)
    #endif

    // The people of GATES_ICLOUD, written as the pages keep people, for the pages to keep
    // theirs the same with rather than with iCloud itself: a simulator has no account for it.
    static var elsewhere: Elsewhere? {
        #if targetEnvironment(simulator)
        stood
        #else
        nil
        #endif
    }

    // Another page to open than the Birth Chart.
    static var start: String? {
        asked("START")
    }

    // Has the chart in the room from the start, beside its window on visionOS, and taps the
    // shape of this name there once it's built.
    static var volume: String? {
        asked("VOLUME")
    }

    // A file of shapes for the chart in the room, in the place of what the site describes: to
    // look at shapes while they're designed.
    static var shapes: String? {
        asked("SHAPES")
    }

    // The size of the window of the pages on visionOS when it opens, as 600x800.
    static var size: CGSize? {
        guard let sides = asked("SIZE")?.split(separator: "x").compactMap({ Double($0) }), sides.count == 2 else {
            return nil
        }
        return CGSize(width: sides[0], height: sides[1])
    }

    // Prints how wide the bar below the window on visionOS is, and how wide it may be.
    static func below(_ width: CGFloat, of room: CGFloat) {
        #if targetEnvironment(simulator)
        print("below \(Int(width.rounded())) of \(Int(room.rounded()))")
        #endif
    }

    // A file to write what the watch would be sent to, for check-watch.sh.
    static var watch: String? {
        asked("WATCH")
    }

    // An address to open as a tap on a widget would, where no widget can be tapped, once the
    // first page is there.
    static func tapped() async -> URL? {
        guard let path = asked("OPEN") else {
            return nil
        }
        try? await Task.sleep(for: .seconds(6))
        return URL(string: Site.root + path)
    }

    // Keeps the welcome of the pages away, which would cover what a check looks at and taps,
    // unless GATES_WELCOME asks for it. Prints what a page logs and what goes wrong in it.
    static func listen(to content: WKUserContentController) {
        #if targetEnvironment(simulator)
        if asked("WELCOME") == nil {
            content.addUserScript(WKUserScript(source: "try { localStorage.setItem('hdapp-welcomed', '1') } catch (e) {}", injectionTime: .atDocumentStart, forMainFrameOnly: true))
        }
        #endif
        guard asked("EVAL") != nil else {
            return
        }
        final class Log: NSObject, WKScriptMessageHandler {
            func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
                print("page \(message.body)")
            }
        }
        content.add(Log(), name: "log")
        content.addUserScript(WKUserScript(source: """
            window.addEventListener('error', e => webkit.messageHandlers.log.postMessage('error: ' + e.message + ' ' + (e.filename || '') + ':' + e.lineno));
            window.addEventListener('unhandledrejection', e => webkit.messageHandlers.log.postMessage('rejection: ' + e.reason));
            for (const level of ['log', 'warn', 'error']) {
                const original = console[level];
                console[level] = (...args) => { webkit.messageHandlers.log.postMessage(level + ': ' + args.join(' ')); original.apply(console, args); };
            }
            """, injectionTime: .atDocumentStart, forMainFrameOnly: true))
    }

    // A few seconds after a page loaded: runs the script of GATES_EVAL in it and prints what it
    // returns.
    static func loaded(_ view: WKWebView) {
        if let script = asked("EVAL") {
            DispatchQueue.main.asyncAfter(deadline: .now() + 4) {
                view.callAsyncJavaScript(script, in: nil, in: .page) { result in
                    // What the last answers hold on to is let go of a moment after the page has them.
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                        print("probe \(result), \(NativeSchemeHandler.held) requests held on to")
                        #if targetEnvironment(simulator)
                        if let stood, let written = try? JSONEncoder().encode(stood.there.sorted { $0.key < $1.key }.map { [$0.key, $0.value.datetime, $0.value.group ?? ""] }) {
                            print("elsewhere \(String(decoding: written, as: UTF8.self))")
                        }
                        #endif
                    }
                }
            }
        }
    }
}
