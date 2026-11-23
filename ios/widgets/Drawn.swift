import Foundation

// What a widget shows, since it can't show a page: a drawing that the site makes in SVG, as it
// draws its charts. A drawing says three things in ways that SVG has and that a browser shows
// too. They're taken out of its text here, since the library that draws it doesn't act on them:
//
// * The link around what it shows is the address that a tap opens in the app.
// * The one element with the id "background" is what's behind the widget, which iOS draws apart
//   and leaves out where it has its own, on the Lock Screen and on a tinted Home Screen.
// * A text with a data-time is a moment that the device writes in its own language and time
//   zone, as its data-text says, with a skeleton of Unicode between braces: "until {EEEjmm}".
struct Drawn {
    let foreground: String
    let background: String?
    let url: URL?
    // What the drawing says, for the size of widget that only takes words.
    let words: String

    private static let entities = [("&lt;", "<"), ("&gt;", ">"), ("&quot;", "\""), ("&apos;", "'"), ("&amp;", "&")]

    init(svg: String, locale: Locale = .current, timeZone: TimeZone = .current) {
        let text = #/<text\b([^>]*)>([^<]*)</text>/#
        let timed = svg.replacing(text) { match -> String in
            let attributes = match.1
            guard let time = attributes.firstMatch(of: #/\bdata-time="([^"]*)"/#)?.1,
                  let moment = ISO8601DateFormatter().date(from: String(time)),
                  let pattern = attributes.firstMatch(of: #/\bdata-text="([^"]*)"/#)?.1 else {
                return String(match.0)
            }
            let said = Self.plain(pattern).replacing(#/\{([A-Za-z]+)\}/#) { skeleton -> String in
                let formatter = DateFormatter()
                formatter.locale = locale
                formatter.timeZone = timeZone
                formatter.setLocalizedDateFormatFromTemplate(String(skeleton.1))
                return formatter.string(from: moment)
            }
            return "<text\(attributes)>\(Self.escaped(said))</text>"
        }
        url = timed.firstMatch(of: #/<a\b[^>]*\bhref="([^"]*)"/#).flatMap { URL(string: Self.plain($0.1)) }
        words = timed.matches(of: text)
            .map { Self.plain($0.2).trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: "  ")
        if let behind = timed.firstMatch(of: #/<\w+\b[^>]*\bid="background"[^>]*/>/#), let root = timed.firstMatch(of: #/<svg\b[^>]*>/#) {
            // Alone in a drawing of the same size, with the styles and the gradients that it may use.
            let shared = timed.matches(of: #/(?s)<(?:style|defs)\b.*?</(?:style|defs)>/#).map { $0.0 }.joined()
            background = root.0 + shared + behind.0 + "</svg>"
            foreground = timed.replacingCharacters(in: behind.range, with: "")
        } else {
            background = nil
            foreground = timed
        }
    }

    private static func plain(_ text: Substring) -> String {
        entities.reduce(String(text)) { $0.replacingOccurrences(of: $1.0, with: $1.1) }
    }

    private static func escaped(_ text: String) -> String {
        text.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;").replacingOccurrences(of: ">", with: "&gt;")
    }
}

// The moments at which a widget changes, each with the address of what it shows from then on,
// when to ask the site for the next ones, and until when these are all of them.
struct DrawnTimeline: Decodable {
    struct Entry: Decodable {
        let date: Date
        let drawing: String
    }

    let reload: Date
    let until: Date
    let entries: [Entry]
}

// A watch has no site to ask: its drawings come from the iPhone.
#if !os(watchOS)
extension DrawnTimeline {
    // What the site answers at the address of a widget in a size, with the drawing of each
    // moment, or only of the first one. On the thread that calls it.
    static func asked(_ path: String, first: Bool) throws -> (reload: Date, drawings: [(date: Date, drawn: Drawn)]) {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let timeline = try decoder.decode(DrawnTimeline.self, from: Site.shared.get(path))
        guard !timeline.entries.isEmpty else {
            throw Refusal("The site has nothing to show at \(path)")
        }
        return (timeline.reload, try timeline.entries.prefix(first ? 1 : .max).map { entry in
            (entry.date, Drawn(svg: String(decoding: try Site.shared.get(entry.drawing), as: UTF8.self)))
        })
    }
}
#endif
