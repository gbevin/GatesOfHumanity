import Foundation

// What begins at a moment, to show from then on.
protocol Timed {
    var date: Date { get }
}

// What the watch shows, all of it from the app on the iPhone: a watch has no web view for the
// pages and nothing that works out a chart. A screen is one of the drawings that the site makes
// for the widgets, and a tap on it opens rows to read, each with what explains it in the words
// of the Explore panel of the pages. Each comes with the moments at which it changes for some
// days ahead, so that the watch shows what's true at every moment without the iPhone near.
struct Drawings: Codable, Equatable {
    // A drawing in SVG and the moment from which it's what to show.
    struct Entry: Codable, Equatable, Timed {
        let date: Date
        let svg: String
    }

    // Something that's explained, under a title.
    struct Part: Codable, Hashable {
        let title: String
        let texts: [String]
    }

    // A row of what a tap on a screen opens: a planet with its gate and line, or a channel, by
    // name, until when it lasts, and what explains it. The site says how the moment is written,
    // as a skeleton of Unicode: a time while that says which day it is, a date after that.
    // What explains it is the same from one moment to the next, so it's kept once for all, in
    // `parts`, and a row has the places of its own there.
    struct Row: Codable, Hashable {
        var label: String?
        let value: String
        let name: String
        let until: Date
        let untilAs: String
        let about: [Int]
    }

    // What a tap on a screen opens at a moment: a heading, a note, what's said without a row,
    // what explains the screen as a whole, and its rows.
    struct Told: Codable, Hashable {
        let heading: String
        var note: String?
        var none: String?
        let about: [Int]
        let rows: [Row]
    }

    // That, and the moment from which it's what to show.
    struct Detail: Codable, Hashable, Timed {
        let date: Date
        let told: Told
    }

    // A screen of the watch app, under a title, and what a tap on it opens when the site has
    // more to say of it.
    struct Screen: Codable, Equatable {
        let title: String
        let entries: [Entry]
        var details: [Detail]?
    }

    // What they're called on their way to the watch.
    static let sent = "drawings"
    // How many of the saved people have a screen: the one whose chart the app showed last and
    // the next ones by name, as the widgets offer them.
    static let people = 8

    // When to ask the iPhone for the next ones, and until when these are what's true: what
    // changes after that isn't known here.
    let reload: Date
    let until: Date
    let screens: [Screen]
    // What a watch face shows of the transits, by the size of what shows it.
    let complications: [String: [Entry]]
    // What explains the rows and the screens, each once.
    let parts: [Part]

    func explained(_ places: [Int]) -> [Part] {
        places.compactMap { parts.indices.contains($0) ? parts[$0] : nil }
    }

    // What to show at a moment: the last one that began, or the first one before any did.
    static func current<Shown: Timed>(of entries: [Shown], at moment: Date) -> Shown? {
        entries.last { $0.date <= moment } ?? entries.first
    }

    // As it's sent and kept: the drawings of a timeline are much alike, which packs small.
    func packed() throws -> Data {
        try (JSONEncoder().encode(self) as NSData).compressed(using: .lzfse) as Data
    }

    init(packed: Data) throws {
        self = try JSONDecoder().decode(Drawings.self, from: (packed as NSData).decompressed(using: .lzfse) as Data)
    }

    init(reload: Date, until: Date, screens: [Screen], complications: [String: [Entry]], parts: [Part] = []) {
        self.parts = parts
        self.reload = reload
        self.until = until
        self.screens = screens
        self.complications = complications
    }
}

#if os(watchOS)
extension Drawings {
    // The watch app and what it has on a watch face are two programs with one folder between
    // them, where the app keeps what it was sent, packed as it came.
    private static var file: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.com.uwyn.gatesofhumanity")?.appendingPathComponent(sent)
    }

    static func kept() -> Drawings? {
        file.flatMap { try? Data(contentsOf: $0) }.flatMap { try? Drawings(packed: $0) }
    }

    // Keeps what was sent when it can be read, and answers it.
    static func keep(_ packed: Data) -> Drawings? {
        guard let file, let drawings = try? Drawings(packed: packed), (try? packed.write(to: file, options: .atomic)) != nil else {
            return nil
        }
        return drawings
    }
}
#else
extension Drawings {
    // Asks the site for the timelines at the addresses of the screens, of what a tap on one
    // opens and of the complications, and for every drawing of each. A screen that the site
    // doesn't draw is left out, and there's nothing to send without any.
    static func asked(screens: [(title: String, path: String, details: String?)], complications: [String: String], get: (String) throws -> Data) throws -> Drawings {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        var reload = Date.distantFuture, until = Date.distantFuture
        func entries(_ path: String) throws -> [Entry] {
            let timeline = try decoder.decode(DrawnTimeline.self, from: get(path))
            reload = min(reload, timeline.reload)
            until = min(until, timeline.until)
            return try timeline.entries.map { Entry(date: $0.date, svg: String(decoding: try get($0.drawing), as: UTF8.self)) }
        }
        // What the site says of a moment, with what explains each row beside it.
        struct Said: Decodable {
            struct Row: Decodable {
                var label: String?
                let value: String
                let name: String
                let until: Date
                let untilAs: String
                let about: [Part]
            }

            let heading: String
            var note: String?
            var none: String?
            let about: [Part]
            let rows: [Row]
        }
        var parts: [Part] = []
        var places: [Part: Int] = [:]
        func placed(_ explaining: [Part]) -> [Int] {
            explaining.map { part in
                if let place = places[part] {
                    return place
                }
                places[part] = parts.count
                parts.append(part)
                return parts.count - 1
            }
        }
        // What a tap opens, when the site says it for every moment of its timeline.
        func told(_ path: String?) -> [Detail]? {
            guard let path, let timeline = try? decoder.decode(DrawnTimeline.self, from: get(path)), !timeline.entries.isEmpty else {
                return nil
            }
            let details = timeline.entries.compactMap { entry in
                (try? decoder.decode(Said.self, from: get(entry.drawing))).map { said in
                    Detail(date: entry.date, told: Told(heading: said.heading, note: said.note, none: said.none, about: placed(said.about), rows: said.rows.map {
                        Row(label: $0.label, value: $0.value, name: $0.name, until: $0.until, untilAs: $0.untilAs, about: placed($0.about))
                    }))
                }
            }
            guard details.count == timeline.entries.count else {
                return nil
            }
            reload = min(reload, timeline.reload)
            until = min(until, timeline.until)
            return details
        }
        let shown = screens.compactMap { screen in
            (try? entries(screen.path)).flatMap { $0.isEmpty ? nil : Screen(title: screen.title, entries: $0, details: told(screen.details)) }
        }
        guard !shown.isEmpty else {
            throw Refusal("The site has nothing to show on a watch")
        }
        // before the moments are read, which every timeline has a say in
        let faces = complications.compactMapValues { try? entries($0) }.filter { !$0.value.isEmpty }
        return Drawings(reload: reload, until: until, screens: shown, complications: faces, parts: parts)
    }
}
#endif
