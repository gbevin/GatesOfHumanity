import RIFE2NativeApp
import SwiftUI

// The app on a Mac: the pages of the site in one window, and a menu bar for what the pages do.
@main
struct Gates: App {
    @NSApplicationDelegateAdaptor private var mac: Mac
    @StateObject private var pages = Pages()

    var body: some Scene {
        Window("Gates of Humanity", id: "pages") {
            NativePagesView(pages: pages)
                .frame(minWidth: 420, minHeight: 620)
                .background(Framed())
                // A widget opens the page of what it shows, and no other address gets in this way.
                .onOpenURL(perform: pages.show)
        }
        .defaultSize(width: 1000, height: 1100)
        .commands {
            Menus(pages: pages)
        }
    }
}

// What a Mac asks of an app as a whole.
final class Mac: NSObject, NSApplicationDelegate {
    func applicationWillFinishLaunching(_ notification: Notification) {
        // There's one window, which has nothing to do with tabs.
        NSWindow.allowsAutomaticWindowTabbing = false
    }

    // An app of one window is done when that window is closed.
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        true
    }
}

// The window around the pages. A page has a title bar of its own, so the one of the window
// goes on in its color above it, without a title: it's where the window is moved by and where
// its three buttons are. The window is where it was left the last time.
private struct Framed: NSViewRepresentable {
    private final class Placed: NSView {
        override func viewDidMoveToWindow() {
            guard let window else {
                return
            }
            window.titlebarAppearsTransparent = true
            window.titleVisibility = .hidden
            window.backgroundColor = .edge
            // the buttons of the window as they are on something dark
            window.appearance = NSAppearance(named: .darkAqua)
            window.setFrameAutosaveName("pages")
        }
    }

    func makeNSView(context: Context) -> NSView {
        Placed()
    }

    func updateNSView(_ view: NSView, context: Context) {
    }
}
