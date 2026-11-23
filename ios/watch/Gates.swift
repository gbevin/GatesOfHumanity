import SwiftUI
import WatchKit

// The app on a watch: the drawings that the iPhone sent, see Drawings, a screen for each, one
// below the other.
@main
struct Gates: App {
    @WKApplicationDelegateAdaptor private var wrist: Wrist
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            Shown(received: .shared)
                .onChange(of: phase, initial: true) { _, phase in
                    if phase == .active {
                        Received.shared.active()
                    }
                }
        }
    }
}

// What a watch asks of an app as a whole: the moments that it gives the app while the app
// isn't on the screen.
final class Wrist: NSObject, WKApplicationDelegate {
    func applicationDidFinishLaunching() {
        Received.shared.start()
    }

    func handle(_ tasks: Set<WKRefreshBackgroundTask>) {
        for task in tasks {
            switch task {
            // the moment that the app asked for, to ask the iPhone for the next drawings
            case let task as WKApplicationRefreshBackgroundTask:
                Received.shared.active {
                    task.setTaskCompletedWithSnapshot(false)
                }
            // the iPhone sent something
            case let task as WKWatchConnectivityRefreshBackgroundTask:
                Received.shared.wait(for: task)
            default:
                task.setTaskCompletedWithSnapshot(false)
            }
        }
    }
}

struct Shown: View {
    // The screen to begin at, which a check in a simulator says, where nothing swipes.
    private static var first: Int {
        #if targetEnvironment(simulator)
        ProcessInfo.processInfo.environment["GATES_SCREEN"].flatMap { Int($0) } ?? 0
        #else
        0
        #endif
    }

    // The screen whose tap to begin past, which a check says too.
    private static var tapped: Int? {
        #if targetEnvironment(simulator)
        ProcessInfo.processInfo.environment["GATES_DETAIL"].flatMap { Int($0) }
        #else
        nil
        #endif
    }

    // How long after its start a watch face is tapped, which a check says too.
    private static var faced: Double? {
        #if targetEnvironment(simulator)
        ProcessInfo.processInfo.environment["GATES_FACE"].flatMap { Double($0) }
        #else
        nil
        #endif
    }

    @ObservedObject var received: Received
    @State private var screen = first
    // The screen that was tapped, while what it opens is shown.
    @State private var detailed = tapped

    // A watch face shows the transits, so a tap on it opens their screen, which is the first,
    // wherever the app was left.
    private func faced() {
        detailed = nil
        screen = 0
    }

    var body: some View {
        shown
            .onOpenURL { _ in
                faced()
            }
            .task {
                if let seconds = Self.faced {
                    try? await Task.sleep(for: .seconds(seconds))
                    faced()
                    // for ios/check-watch.sh
                    print("faced \(received.drawings?.screens[screen].title ?? "")\(detailed == nil ? "" : " past its tap")")
                }
            }
    }

    @ViewBuilder private var shown: some View {
        if let drawings = received.drawings {
            // what's true after the last of them isn't known, so they give way then
            TimelineView(.explicit([drawings.until])) { _ in
                if drawings.until > .now {
                    NavigationStack {
                        TabView(selection: $screen) {
                            ForEach(Array(drawings.screens.enumerated()), id: \.offset) { index, screen in
                                ScreenView(screen: screen)
                                    .contentShape(Rectangle())
                                    .onTapGesture {
                                        if screen.details != nil {
                                            detailed = index
                                        }
                                    }
                                    .tag(index)
                            }
                        }
                        .tabViewStyle(.verticalPage)
                        .navigationDestination(item: $detailed) { index in
                            if drawings.screens.indices.contains(index) {
                                DetailView(screen: drawings.screens[index], drawings: drawings)
                            }
                        }
                    }
                } else {
                    Waiting()
                }
            }
        } else {
            Waiting()
        }
    }
}

// A screen shows the drawing of this moment, and the next one when its moment comes.
private struct ScreenView: View {
    let screen: Drawings.Screen

    var body: some View {
        TimelineView(.explicit(screen.entries.map(\.date))) { _ in
            if let entry = Drawings.current(of: screen.entries, at: .now) {
                let drawn = Drawn(svg: entry.svg)
                DrawnView(svg: drawn.foreground)
                    .accessibilityLabel(drawn.words)
                    .containerBackground(for: .tabView) {
                        if let background = drawn.background {
                            DrawnView(svg: background, stretched: true)
                        }
                    }
            }
        }
        .navigationTitle(screen.title)
    }
}

// The colors of the drawings, for what the watch writes itself.
private enum Look {
    static let gold = Color(red: 227 / 255, green: 168 / 255, blue: 60 / 255)
    static let quiet = Color(red: 185 / 255, green: 185 / 255, blue: 214 / 255)
    static let sky = LinearGradient(colors: [Color(red: 43 / 255, green: 43 / 255, blue: 102 / 255), Color(red: 23 / 255, green: 23 / 255, blue: 58 / 255)],
                                    startPoint: .top, endPoint: .bottom)
}

// What explains a row or a screen, under the title of its own screen.
private struct Explained: Hashable {
    let title: String
    let parts: [Drawings.Part]
}

// What a tap on a screen opens: its rows, which change at their own moments, and a row to read
// what the screen as a whole is about. A tap on a row opens what explains it.
private struct DetailView: View {
    // The row whose tap to begin past, which a check says: 0 for what the screen is about.
    private static var tapped: Int? {
        #if targetEnvironment(simulator)
        ProcessInfo.processInfo.environment["GATES_ROW"].flatMap { Int($0) }
        #else
        nil
        #endif
    }

    let screen: Drawings.Screen
    let drawings: Drawings
    @State private var explained: Explained?

    private func about(_ told: Drawings.Told) -> Explained {
        Explained(title: screen.title, parts: drawings.explained(told.about))
    }

    private func about(_ row: Drawings.Row) -> Explained {
        Explained(title: row.value + " " + row.name, parts: drawings.explained(row.about))
    }

    var body: some View {
        let details = screen.details ?? []
        TimelineView(.explicit(details.map(\.date))) { _ in
            if let told = Drawings.current(of: details, at: .now)?.told {
                List {
                    Section {
                        ForEach(told.rows, id: \.self) { row in
                            Button {
                                explained = about(row)
                            } label: {
                                RowView(row: row)
                            }
                        }
                        if let none = told.none {
                            Text(none).font(.footnote).foregroundStyle(Look.quiet)
                        }
                        Button {
                            explained = about(told)
                        } label: {
                            Label("What these are", systemImage: "questionmark.circle").foregroundStyle(Look.quiet)
                        }
                    } header: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(told.heading)
                            if let note = told.note {
                                Text(note).textCase(nil)
                            }
                        }
                        .foregroundStyle(Look.quiet)
                    }
                }
                .onAppear {
                    if let row = Self.tapped, explained == nil {
                        explained = row == 0 ? about(told) : told.rows.indices.contains(row - 1) ? about(told.rows[row - 1]) : nil
                    }
                }
            }
        }
        .containerBackground(Look.sky, for: .navigation)
        .navigationTitle(screen.title)
        .navigationDestination(item: $explained) { explained in
            ExplainedView(explained: explained)
        }
    }
}

private struct RowView: View {
    let row: Drawings.Row

    // The moment as the watch writes it in its own language and time zone.
    private var lasts: String {
        let written = DateFormatter()
        written.setLocalizedDateFormatFromTemplate(row.untilAs)
        return "until " + written.string(from: row.until)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            if let label = row.label {
                Text(label.uppercased()).font(.caption2.weight(.bold)).foregroundStyle(Look.quiet)
            }
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(row.value).font(.title3.weight(.bold)).foregroundStyle(Look.gold)
                Text(row.name).font(.headline).minimumScaleFactor(0.7).lineLimit(2)
            }
            Text(lasts).font(.caption2).foregroundStyle(Look.quiet)
        }
        .accessibilityElement(children: .combine)
    }
}

// What explains a row or a screen: each part under its title, to scroll through.
private struct ExplainedView: View {
    let explained: Explained

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(explained.parts, id: \.self) { part in
                    Text(part.title).font(.headline).foregroundStyle(Look.gold).padding(.top, 4)
                    ForEach(part.texts, id: \.self) { text in
                        Text(text).font(.body)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 4)
        }
        .containerBackground(Look.sky, for: .navigation)
        .navigationTitle(explained.title)
    }
}

// Before the iPhone sent anything, and once what it sent ran out.
private struct Waiting: View {
    var body: some View {
        VStack(spacing: 8) {
            Emblem().frame(width: 64, height: 64)
            Text("The transits show here when your iPhone is near.")
                .font(.footnote)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 8)
    }
}
