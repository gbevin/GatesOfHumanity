import SwiftDraw
import SwiftUI
import WidgetKit

// What a widget shows from a moment on: a drawing of the site, or why there's none.
struct SiteEntry: TimelineEntry {
    let date: Date
    var drawn: Drawn?
    var failure: String?
}

// A drawing in the room it's given: all of it, as large as fits, or stretched over the room as
// what's behind a widget is.
struct DrawnView: View {
    let svg: String
    var stretched = false

    var body: some View {
        if let parsed = SVG(xml: svg) {
            if stretched {
                SVGView(svg: parsed).resizable()
            } else {
                SVGView(svg: parsed).resizable().aspectRatio(parsed.size, contentMode: .fit)
            }
        }
    }
}

// The sizes of the Lock Screen and of a watch face, which a Mac doesn't have: the same widgets
// are on its desktop.
enum LockScreen {
    #if os(iOS) || os(watchOS)
    static let sizes: [WidgetFamily: String] = [
        .accessoryRectangular: "accessoryRectangular", .accessoryInline: "accessoryInline", .accessoryCircular: "accessoryCircular",
    ]

    // The size that only takes words.
    static func words(_ family: WidgetFamily) -> Bool {
        family == .accessoryInline
    }

    static var backdrop: some View {
        AccessoryWidgetBackground()
    }
    #else
    static let sizes: [WidgetFamily: String] = [:]

    static func words(_ family: WidgetFamily) -> Bool {
        false
    }

    static var backdrop: some View {
        EmptyView()
    }
    #endif
}

struct SiteEntryView: View {
    @Environment(\.widgetFamily) private var family
    let entry: SiteEntry

    // On the Lock Screen iOS shows nothing behind a widget but what the widget shows itself.
    private var behindIsDrawn: Bool {
        LockScreen.sizes[family] != nil && !LockScreen.words(family)
    }

    var body: some View {
        ZStack {
            if let drawn = entry.drawn {
                if LockScreen.words(family) {
                    Text(drawn.words)
                } else {
                    // The Lock Screen has no colors: where a drawing has something behind it,
                    // that's the backdrop of iOS.
                    if behindIsDrawn, drawn.background != nil {
                        LockScreen.backdrop
                    }
                    DrawnView(svg: drawn.foreground)
                }
            } else if let failure = entry.failure {
                Text(failure).font(.caption2).minimumScaleFactor(0.5).padding(8)
            }
        }
        .widgetURL(entry.drawn?.url)
        .containerBackground(for: .widget) {
            if !behindIsDrawn, let background = entry.drawn?.background {
                DrawnView(svg: background, stretched: true)
            }
        }
    }
}

extension WidgetConfiguration {
    // What iOS has to know of a widget before it asks the site: what it's called, what it's for
    // and its sizes. A drawing is the whole widget, with its own room around what it shows.
    func called(_ name: String, _ description: String, sizes: [WidgetFamily]) -> some WidgetConfiguration {
        configurationDisplayName(name).description(description).supportedFamilies(sizes).contentMarginsDisabled()
    }
}
