import SwiftUI
import WidgetKit

// What a watch face shows of the transits: the drawings that the watch app keeps for it, see
// Drawings, each from its moment on, so that the face changes at those moments without the
// app. The app has the watch ask again when the iPhone sent other ones.
struct KeptProvider: TimelineProvider {
    func placeholder(in context: Context) -> SiteEntry {
        SiteEntry(date: .now)
    }

    func getSnapshot(in context: Context, completion: @escaping (SiteEntry) -> Void) {
        completion(entries(context.family)[0])
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SiteEntry>) -> Void) {
        completion(Timeline(entries: entries(context.family), policy: .never))
    }

    // The drawing of this moment and the ones after it, until what's true isn't known anymore.
    private func entries(_ family: WidgetFamily) -> [SiteEntry] {
        let waiting = "Open Gates"
        guard let drawings = Drawings.kept(), drawings.until > .now, let kept = drawings.complications[LockScreen.sizes[family] ?? ""],
              let current = Drawings.current(of: kept, at: .now) else {
            return [SiteEntry(date: .now, failure: waiting)]
        }
        return ([current] + kept.filter { $0.date > current.date && $0.date < drawings.until }).map { SiteEntry(date: $0.date, drawn: Drawn(svg: $0.svg)) }
            + [SiteEntry(date: drawings.until, failure: waiting)]
    }
}

struct TransitsComplication: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "transits", provider: KeptProvider()) { SiteEntryView(entry: $0) }
            .called("Transits Now", "The gate and line that the Sun is in, and until when.", sizes: Array(LockScreen.sizes.keys))
    }
}

@main
struct Complications: WidgetBundle {
    var body: some Widget {
        TransitsComplication()
    }
}
