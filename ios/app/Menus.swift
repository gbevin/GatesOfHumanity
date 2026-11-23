import SwiftUI

// What the pages have buttons for, with a key for each: the menu bar of a Mac and of an iPad,
// and the keys of a keyboard that's connected to an iPhone or an iPad. The charts and the other
// pages go by their numbers, a step through time by the arrows, and saving and printing by the
// keys that a Mac has for those.
struct Menus: Commands {
    @ObservedObject var pages: Pages

    private static let charts = [
        ("Birth Chart", "/wheel/birth"), ("Connection Chart", "/wheel/connection"), ("Transit Chart", "/wheel"),
        ("Birth Transit Chart", "/wheel/birth-transit"), ("Connection Transit Chart", "/wheel/connection-transit"),
        ("Transits by Month", "/range"), ("Transits by Day", "/range/d"),
    ]
    // Each step with the keys that it takes beside an arrow. They're two at least: a web view
    // scrolls with an arrow that has one key with it, Command alone too, and never lets the
    // app see that one.
    private static let steps: [(String, String, EventModifiers)] = [
        ("Day", "day", [.command, .shift]), ("Hour", "hour", [.command, .option]), ("Minute", "minute", [.command, .option, .shift]),
    ]

    var body: some Commands {
        #if os(macOS)
        CommandGroup(replacing: .appInfo) {
            Button("About Gates of Humanity") {
                pages.go("/about")
            }
        }
        #endif
        // a window of pages has nothing to make anew
        CommandGroup(replacing: .newItem) {
            Button("Load Person…") {
                pages.press(Pages.load)
            }
            .keyboardShortcut("o")
            .disabled(!pages.charted)
            Button("Save Person…") {
                pages.press(Pages.save)
            }
            .keyboardShortcut("s")
            .disabled(!pages.charted)
            Divider()
            Button("Save Chart…") {
                pages.press("#chart-download")
            }
            .keyboardShortcut("s", modifiers: [.command, .shift])
            .disabled(!pages.charted)
            Button("Copy Link") {
                pages.copyLink()
            }
            .keyboardShortcut("c", modifiers: [.command, .shift])
            .disabled(!pages.charted)
        }
        CommandGroup(replacing: .printItem) {
            Button("Print…") {
                pages.printPage()
            }
            .keyboardShortcut("p")
        }
        CommandGroup(after: .toolbar) {
            Button("Back") {
                pages.back()
            }
            .keyboardShortcut("[")
            .disabled(!pages.canGoBack)
            Button("Forward") {
                pages.forward()
            }
            .keyboardShortcut("]")
            .disabled(!pages.canGoForward)
            Button("Reload") {
                pages.reload()
            }
            .keyboardShortcut("r")
            #if os(macOS)
            // a page of the iOS app keeps its size
            Divider()
            Button("Actual Size") {
                pages.zoom(to: 1)
            }
            .keyboardShortcut("0")
            Button("Zoom In") {
                pages.zoom(to: pages.view.pageZoom * 1.1)
            }
            .keyboardShortcut("+")
            Button("Zoom Out") {
                pages.zoom(to: pages.view.pageZoom / 1.1)
            }
            .keyboardShortcut("-")
            #endif
            Divider()
            Button("Enlarge Chart") {
                pages.press(Pages.enlarge)
            }
            .keyboardShortcut("e")
            .disabled(!pages.charted)
            Divider()
        }
        CommandMenu("Chart") {
            ForEach(Array(Self.charts.enumerated()), id: \.offset) { number, chart in
                Button(chart.0) {
                    pages.go(chart.1)
                }
                .keyboardShortcut(KeyEquivalent(Character(String(number + 1))))
            }
            Divider()
            Button("People") {
                pages.go("/people")
            }
            .keyboardShortcut("8")
        }
        CommandMenu("Time") {
            Group {
                Button("Now") {
                    pages.press("#change-time button[name=now]")
                }
                .keyboardShortcut("t")
                ForEach(Self.steps, id: \.0) { step in
                    Divider()
                    Button("Next \(step.0)") {
                        pages.step(step.1, later: true)
                    }
                    .keyboardShortcut(.rightArrow, modifiers: step.2)
                    Button("Previous \(step.0)") {
                        pages.step(step.1, later: false)
                    }
                    .keyboardShortcut(.leftArrow, modifiers: step.2)
                }
            }
            .disabled(!pages.charted)
        }
        CommandGroup(replacing: .help) {
            Button("Learn about Human Design") {
                pages.go("/learn")
            }
            .keyboardShortcut("?")
            Button("About These Charts") {
                pages.go("/about")
            }
            Button("Show the Welcome Again") {
                pages.go("/wheel/birth#welcome")
            }
        }
    }
}
