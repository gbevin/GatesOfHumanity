import AppIntents
import SwiftUI
import WidgetKit

// What the site inside the app has a widget show: the moments at which it changes for some
// days ahead and a drawing for each, so that iOS changes the widget at those moments without
// running anything.
enum Shown {
    // One request at a time, away from the thread that iOS asks on.
    private static let asking = DispatchQueue(label: "site")
    private static let sizes: [WidgetFamily: String] = [
        .systemSmall: "systemSmall", .systemMedium: "systemMedium", .systemLarge: "systemLarge",
    ].merging(LockScreen.sizes) { size, _ in size }

    // The entries of the widget whose address a size gives, all of them or only the first, and
    // when to ask again.
    static func entries(_ family: WidgetFamily, first: Bool, at address: @escaping (String) -> String) async -> (entries: [SiteEntry], reload: Date) {
        await withCheckedContinuation { asked in
            asking.async {
                do {
                    let timeline = try DrawnTimeline.asked(address(sizes[family] ?? ""), first: first)
                    asked.resume(returning: (timeline.drawings.map { SiteEntry(date: $0.date, drawn: $0.drawn) }, timeline.reload))
                } catch {
                    log.error("A widget couldn't be drawn: \(Refusal(error).why, privacy: .public)")
                    asked.resume(returning: ([SiteEntry(date: .now, failure: "This can't be shown right now.")], .now.addingTimeInterval(900)))
                }
            }
        }
    }
}

// A widget that's the same for everyone, at the address that the site has it at.
struct SiteProvider: TimelineProvider {
    let path: String

    func placeholder(in context: Context) -> SiteEntry {
        SiteEntry(date: .now)
    }

    func getSnapshot(in context: Context, completion: @escaping (SiteEntry) -> Void) {
        Task {
            completion(await Shown.entries(context.family, first: true) { path + "/" + $0 }.entries[0])
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SiteEntry>) -> Void) {
        Task {
            let shown = await Shown.entries(context.family, first: false) { path + "/" + $0 }
            completion(Timeline(entries: shown.entries, policy: .after(shown.reload)))
        }
    }
}

struct TransitsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "transits", provider: SiteProvider(path: "/widget/transits")) { SiteEntryView(entry: $0) }
            .called("Transits Now", "The gates and lines that the Sun, the Earth and the Moon are in, and until when.",
                    sizes: [.systemSmall, .systemMedium] + LockScreen.sizes.keys)
    }
}

struct BodygraphWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "bodygraph", provider: SiteProvider(path: "/widget/bodygraph")) { SiteEntryView(entry: $0) }
            .called("Body Graph Now", "The centers and the channels that the planets define at this moment, and until when.",
                    sizes: [.systemSmall, .systemMedium, .systemLarge])
    }
}

// Someone that the pages saved, to choose for a widget in what iOS shows to change one.
struct PersonEntity: AppEntity {
    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Person"
    static let defaultQuery = PersonQuery()

    let saved: Saved

    var id: String {
        saved.name
    }

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(saved.name)")
    }
}

struct PersonQuery: EntityQuery {
    func entities(for names: [String]) async throws -> [PersonEntity] {
        Saved.all().filter { names.contains($0.name) }.map { PersonEntity(saved: $0) }
    }

    // By name: the app keeps the one whose chart it showed last first.
    func suggestedEntities() async throws -> [PersonEntity] {
        Saved.all().sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }.map { PersonEntity(saved: $0) }
    }
}

struct PersonChoice: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Person"
    static let description = IntentDescription("Whose chart the widget shows with the transits.")

    @Parameter(title: "Person")
    var person: PersonEntity?
}

// The widget of a person: the one that was chosen, as they're saved now, or else the first of
// those that the app kept, who is the one whose chart it showed last.
struct PersonProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> SiteEntry {
        SiteEntry(date: .now)
    }

    func snapshot(for choice: PersonChoice, in context: Context) async -> SiteEntry {
        await shown(for: choice, context.family, first: true).entries[0]
    }

    func timeline(for choice: PersonChoice, in context: Context) async -> Timeline<SiteEntry> {
        let shown = await shown(for: choice, context.family, first: false)
        return Timeline(entries: shown.entries, policy: .after(shown.reload))
    }

    private func shown(for choice: PersonChoice, _ family: WidgetFamily, first: Bool) async -> (entries: [SiteEntry], reload: Date) {
        let people = Saved.all()
        guard let person = people.first(where: { $0.name == choice.person?.saved.name }) ?? people.first else {
            return ([SiteEntry(date: .now, failure: "Save a person in Gates to see their transits here.")], .now.addingTimeInterval(3600))
        }
        return await Shown.entries(family, first: first) { person.widget($0) }
    }
}

struct PersonWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "person", intent: PersonChoice.self, provider: PersonProvider()) { SiteEntryView(entry: $0) }
            .called("Transits of a Person", "The body graph of someone you saved, with the channels and the centers that the transits add to it now.",
                    sizes: [.systemSmall, .systemMedium, .systemLarge])
    }
}

@main
struct Widgets: WidgetBundle {
    var body: some Widget {
        TransitsWidget()
        BodygraphWidget()
        PersonWidget()
    }
}
